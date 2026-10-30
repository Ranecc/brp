package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ExchangeDtos.BookEntry;
import dev.brp.dto.ExchangeDtos.BookResponse;
import dev.brp.dto.ExchangeDtos.OrderRequest;
import dev.brp.dto.ExchangeDtos.OrderResponse;
import dev.brp.entity.ExchangeItemEntity;
import dev.brp.entity.ExchangeOrderEntity;
import dev.brp.repository.CreditLineRepository;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.repository.ExchangeOrderRepository;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.ExchangeOrderService;
import dev.brp.service.ExchangeWarehouseService;
import dev.brp.service.LedgerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Order filing (docs/14 §6). Collateral moves at filing time — escrow for
 * BIDs, a warehouse hold for ASKs — and the batch auction settles it later
 * at one uniform price. Filing is admission, not execution.
 */
@Service
public class ExchangeOrderServiceImpl implements ExchangeOrderService {

    private static final String ESCROW = "market-escrow";
    private static final String TREASURY = "treasury";

    private final ExchangeOrderRepository orders;
    private final ExchangeItemRepository items;
    private final ExchangeCatalogService catalog;
    private final ExchangeWarehouseService warehouse;
    private final LedgerService ledger;
    private final CreditLineRepository creditLines;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int ttlDays;

    public ExchangeOrderServiceImpl(ExchangeOrderRepository orders,
                                    ExchangeItemRepository items,
                                    ExchangeCatalogService catalog,
                                    ExchangeWarehouseService warehouse,
                                    LedgerService ledger,
                                    CreditLineRepository creditLines,
                                    TransactionTemplate tx,
                                    Clock clock,
                                    @Value("${brp.exchange.order-ttl-days:7}") int ttlDays) {
        this.orders = orders;
        this.items = items;
        this.catalog = catalog;
        this.warehouse = warehouse;
        this.ledger = ledger;
        this.creditLines = creditLines;
        this.tx = tx;
        this.clock = clock;
        this.ttlDays = ttlDays;
    }

    @Override
    public OrderResponse place(OrderRequest request) {
        String tenantId = request.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BadRequestException("exchange.order.tenant.required");
        }
        if (request.idemKey() == null || request.idemKey().isBlank()) {
            throw new BadRequestException("exchange.order.idem.required");
        }
        if (!ExchangeOrderEntity.ASK.equals(request.side())
                && !ExchangeOrderEntity.BID.equals(request.side())) {
            throw new BadRequestException("exchange.order.side.invalid");
        }
        if (request.qty() <= 0 || request.priceCents() <= 0) {
            throw new BadRequestException("exchange.order.invalid");
        }
        // docs/13 §6: RESTRICTED credit refuses new commitments
        creditLines.findById(tenantId).ifPresent(line -> {
            String st = line.getStatus();
            if ("RESTRICTED".equals(st) || "DEFAULTED".equals(st)) {
                throw new BadRequestException("credit.restricted");
            }
        });
        ExchangeItemEntity item = items.findById(request.itemId())
                .orElseThrow(() -> new BadRequestException("exchange.item.unknown"));
        if (!catalog.tradable(item.getItemId())) {
            throw new BadRequestException("exchange.item.not.listed");
        }
        Optional<ExchangeOrderEntity> replay = orders.findByTenantIdAndIdemKey(tenantId, request.idemKey());
        if (replay.isPresent()) {
            return toDto(replay.get());
        }
        try {
            return tx.execute(status -> {
                ExchangeOrderEntity order = new ExchangeOrderEntity(tenantId, item.getItemId(),
                        request.idemKey(), request.side(), request.qty(), request.priceCents(),
                        OffsetDateTime.now(clock).plusDays(ttlDays));
                ExchangeOrderEntity saved = orders.saveAndFlush(order);
                lockCollateral(saved);
                return toDto(saved);
            });
        } catch (DataIntegrityViolationException lostTheRace) {
            return orders.findByTenantIdAndIdemKey(tenantId, request.idemKey())
                    .map(ExchangeOrderServiceImpl::toDto)
                    .orElseThrow(() -> new BadRequestException("exchange.order.state.lost"));
        }
    }

    private void lockCollateral(ExchangeOrderEntity order) {
        if (ExchangeOrderEntity.BID.equals(order.getSide())) {
            long escrow = Math.multiplyExact(order.getQty(), order.getPriceCents());
            ensureAccount(order.getTenantId(), ESCROW);
            ledger.post(order.getTenantId(), new PostJournalRequest(
                    "bourse-escrow:" + order.getId() + ":" + UUID.randomUUID(), "BRC", List.of(
                    new JournalLine(ESCROW, "DEBIT", escrow, null),
                    new JournalLine(TREASURY, "CREDIT", escrow, null))));
        } else {
            warehouse.hold(order.getTenantId(), order.getItemId(), order.getQty(),
                    "order:" + order.getId(), "wh-hold:order:" + order.getId());
        }
    }

    @Override
    public OrderResponse cancel(String tenantId, long orderId) {
        return tx.execute(status -> {
            ExchangeOrderEntity order = orders.findForUpdate(orderId);
            if (order == null || !order.getTenantId().equals(tenantId) || !order.resting()) {
                throw new BadRequestException("exchange.cancel.invalid");
            }
            releaseCollateral(order);
            order.close(ExchangeOrderEntity.CANCELLED);
            return toDto(orders.save(order));
        });
    }

    private void releaseCollateral(ExchangeOrderEntity order) {
        if (ExchangeOrderEntity.BID.equals(order.getSide())) {
            long refund = Math.multiplyExact(order.getQtyRemaining(), order.getPriceCents());
            if (refund > 0) {
                ensureAccount(order.getTenantId(), ESCROW);
                ledger.post(order.getTenantId(), new PostJournalRequest(
                        "bourse-escrow-release:" + order.getId() + ":" + UUID.randomUUID(),
                        "BRC", List.of(
                        new JournalLine(TREASURY, "DEBIT", refund, null),
                        new JournalLine(ESCROW, "CREDIT", refund, null))));
            }
        } else {
            warehouse.release(order.getTenantId(), order.getItemId(), order.getQtyRemaining(),
                    "order:" + order.getId(), "wh-release:order:" + order.getId());
        }
    }

    @Override
    public List<OrderResponse> orders(String tenantId) {
        return orders.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(ExchangeOrderServiceImpl::toDto).toList();
    }

    @Override
    public List<OrderResponse> ordersFor(String tenantId, String itemId) {
        return orders.findByTenantIdAndItemIdOrderByCreatedAtDesc(tenantId, itemId).stream()
                .map(ExchangeOrderServiceImpl::toDto).toList();
    }

    @Override
    public BookResponse book(String itemId) {
        ExchangeItemEntity item = items.findById(itemId)
                .orElseThrow(() -> new BadRequestException("exchange.item.unknown"));
        List<ExchangeOrderEntity> resting = orders.restingForItem(itemId);
        List<BookEntry> bids = resting.stream()
                .filter(o -> ExchangeOrderEntity.BID.equals(o.getSide()))
                .sorted(Comparator.comparingLong(ExchangeOrderEntity::getPriceCents).reversed()
                        .thenComparing(ExchangeOrderEntity::getId))
                .map(ExchangeOrderServiceImpl::toEntry).toList();
        List<BookEntry> asks = resting.stream()
                .filter(o -> ExchangeOrderEntity.ASK.equals(o.getSide()))
                .sorted(Comparator.comparingLong(ExchangeOrderEntity::getPriceCents)
                        .thenComparing(ExchangeOrderEntity::getId))
                .map(ExchangeOrderServiceImpl::toEntry).toList();
        return new BookResponse(itemId, item.getRefCents(), item.isPinned(),
                item.getListedState(), bids, asks);
    }

    @Override
    @Scheduled(fixedDelayString = "${brp.exchange.order-expire-ms:3600000}",
            initialDelayString = "${brp.exchange.order-expire-ms:3600000}")
    public void expireStale() {
        List<ExchangeOrderEntity> stale = orders.findByExpiresAtBeforeAndStatusIn(
                OffsetDateTime.now(clock), List.of(ExchangeOrderEntity.NEW, ExchangeOrderEntity.PARTIAL));
        for (ExchangeOrderEntity order : stale) {
            tx.executeWithoutResult(status -> {
                ExchangeOrderEntity locked = orders.findForUpdate(order.getId());
                if (locked == null || !locked.resting()) {
                    return;
                }
                releaseCollateral(locked);
                locked.close(ExchangeOrderEntity.EXPIRED);
                orders.save(locked);
            });
        }
    }

    @Override
    public int expireStaleCount() {
        return orders.findByExpiresAtBeforeAndStatusIn(
                OffsetDateTime.now(clock), List.of(ExchangeOrderEntity.NEW, ExchangeOrderEntity.PARTIAL)).size();
    }

    private void ensureAccount(String tenantId, String accountNo) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest(accountNo, "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // sinks are patient
        }
    }

    static OrderResponse toDto(ExchangeOrderEntity o) {
        return new OrderResponse(o.getId(), o.getTenantId(), o.getItemId(), o.getSide(),
                o.getQty(), o.getQtyRemaining(), o.getPriceCents(), o.getStatus());
    }

    private static BookEntry toEntry(ExchangeOrderEntity o) {
        return new BookEntry(o.getId(), o.getSide(), o.getQtyRemaining(),
                o.getPriceCents(), o.getTenantId());
    }
}
