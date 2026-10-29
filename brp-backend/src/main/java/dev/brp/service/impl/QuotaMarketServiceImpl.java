package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.LoadDtos.MarketOrderResponse;
import dev.brp.entity.MarketOrderEntity;
import dev.brp.entity.MarketTradeEntity;
import dev.brp.entity.PegStateEntity;
import dev.brp.entity.QuotaBucketEntity;
import dev.brp.repository.MarketOrderRepository;
import dev.brp.repository.MarketTradeRepository;
import dev.brp.repository.PegStateRepository;
import dev.brp.repository.QuotaBucketRepository;
import dev.brp.service.LedgerService;
import dev.brp.service.QuotaMarketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The exchange (docs/13 §8), deliberately small: ASKs lock quota capacity in
 * the seller's market_quota bucket; BIDs escrow BRC into market-escrow;
 * crosses settle in ONE transaction — both order rows locked, the trade
 * recorded, the fee taken. Same tenant on both sides of a cross: skipped —
 * the wash desk gets to stay bored.
 */
@Service
public class QuotaMarketServiceImpl implements QuotaMarketService {

    public static final String RESOURCE = "market_quota";
    private static final String ESCROW = "market-escrow";
    private static final String SALES = "market-sales";
    private static final String CLEARING = "market-clearing";
    private static final String FEE = "market-fee";
    private static final String TREASURY = "treasury";
    private static final int BOOK_DEPTH = 100;

    private final MarketOrderRepository orders;
    private final MarketTradeRepository trades;
    private final QuotaBucketRepository buckets;
    private final PegStateRepository pegLegs;
    private final LedgerService ledger;
    private final TransactionTemplate tx;
    private final dev.brp.repository.CreditLineRepository creditLines;
    private final Clock clock;
    private final int feeBips;
    private final int ttlDays;
    private final Map<String, Long> placedKeys = new ConcurrentHashMap<>();

    public QuotaMarketServiceImpl(MarketOrderRepository orders,
                                  MarketTradeRepository trades,
                                  QuotaBucketRepository buckets,
                                  PegStateRepository pegLegs,
                                  LedgerService ledger,
                                  TransactionTemplate tx,
                                  dev.brp.repository.CreditLineRepository creditLines,
                                  Clock clock,
                                  @Value("${brp.market.fee-bips:50}") int feeBips,
                                  @Value("${brp.market.order-ttl-days:7}") int ttlDays) {
        this.orders = orders;
        this.trades = trades;
        this.buckets = buckets;
        this.pegLegs = pegLegs;
        this.ledger = ledger;
        this.tx = tx;
        this.creditLines = creditLines;
        this.clock = clock;
        this.feeBips = feeBips;
        this.ttlDays = ttlDays;
    }

    @Override
    public MarketOrderResponse place(String tenantId, String idemKey, String side, long qty, long priceCents) {
        if (idemKey == null || idemKey.isBlank()) {
            throw new BadRequestException("market.idem.required");
        }
        if (!MarketOrderEntity.ASK.equals(side) && !MarketOrderEntity.BID.equals(side)) {
            throw new BadRequestException("market.side.invalid");
        }
        if (qty <= 0 || priceCents <= 0) {
            throw new BadRequestException("market.order.invalid");
        }
        // docs/13 §6: RESTRICTED credit refuses new commitments
        creditLines.findById(tenantId).ifPresent(line -> {
            String st = line.getStatus();
            if ("RESTRICTED".equals(st) || "DEFAULTED".equals(st)) {
                throw new BadRequestException("credit.restricted");
            }
        });
        Long replay = placedKeys.get(tenantId + ":" + idemKey);
        if (replay != null) {
            return orders.findById(replay).map(QuotaMarketServiceImpl::toDto)
                    .orElseThrow(() -> new BadRequestException("market.state.lost"));
        }
        // the peg is the anchor: the market trades compute, not peg bets
        PegStateEntity leg = pegLegs.findById(PegStateEntity.QUOTA)
                .orElseThrow(() -> new BadRequestException("market.peg.unavailable"));
        if (!MarketMath.insidePegBand(priceCents, leg.getBandLow(), leg.getBandHigh())) {
            throw new BadRequestException("market.price.outside.band");
        }
        try {
            MarketOrderResponse placed = tx.execute(status -> {
                MarketOrderEntity order = new MarketOrderEntity(tenantId, idemKey, side, qty,
                        priceCents, OffsetDateTime.now(clock).plusDays(ttlDays));
                lockCollateral(tenantId, side, qty, priceCents);
                MarketOrderEntity saved = orders.saveAndFlush(order);
                placedKeys.put(tenantId + ":" + idemKey, saved.getId());
                cross(saved);
                return toDto(orders.findById(saved.getId()).orElse(saved));
            });
            return placed;
        } catch (RuntimeException e) {
            placedKeys.remove(tenantId + ":" + idemKey);
            throw e;
        }
    }

    @Override
    public MarketOrderResponse cancel(String tenantId, long orderId) {
        MarketOrderResponse released = tx.execute(status -> {
            MarketOrderEntity order = orders.findForUpdate(orderId);
            if (order == null || !order.getTenantId().equals(tenantId) || !order.resting()) {
                throw new BadRequestException("market.cancel.invalid");
            }
            releaseCollateral(order);
            order.close(MarketOrderEntity.CANCELLED);
            return toDto(orders.save(order));
        });
        return released;
    }

    @Override
    public List<MarketOrderResponse> book() {
        return orders.findAll().stream()
                .filter(MarketOrderEntity::resting)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(QuotaMarketServiceImpl::toDto).toList();
    }

    @Override
    public List<MarketOrderResponse> orders(String tenantId) {
        return orders.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(QuotaMarketServiceImpl::toDto).toList();
    }

    @Override
    public List<dev.brp.entity.MarketTradeEntity> trades() {
        return trades.findAll().stream()
                .sorted(java.util.Comparator.comparing(dev.brp.entity.MarketTradeEntity::getId).reversed())
                .limit(20).toList();
    }

    @Override
    @Scheduled(fixedDelayString = "${brp.market.expire-ms:3600000}",
            initialDelayString = "${brp.market.expire-ms:3600000}")
    public void expireStale() {
        List<MarketOrderEntity> stale = orders.findByExpiresAtBeforeAndStatusIn(
                OffsetDateTime.now(clock), List.of(MarketOrderEntity.NEW, MarketOrderEntity.PARTIAL));
        for (MarketOrderEntity order : stale) {
            tx.executeWithoutResult(status -> {
                MarketOrderEntity locked = orders.findForUpdate(order.getId());
                if (locked == null || !locked.resting()) {
                    return;
                }
                releaseCollateral(locked);
                locked.close(MarketOrderEntity.EXPIRED);
                orders.save(locked);
            });
        }
    }

    @Override
    public int expireStaleCount() {
        return orders.findByExpiresAtBeforeAndStatusIn(
                OffsetDateTime.now(clock), List.of(MarketOrderEntity.NEW, MarketOrderEntity.PARTIAL)).size();
    }

    // --- internals ---

    /** ASKs lock quota capacity; BIDs escrow BRC. No margin, no shorting. */
    private void lockCollateral(String tenantId, String side, long qty, long priceCents) {
        if (MarketOrderEntity.ASK.equals(side)) {
            QuotaBucketEntity bucket = bucket(tenantId);
            // a zero limit is "not provisioned" here, not "unlimited": the
            // market refuses to short capacity that was never granted
            if (bucket.getLimitAmount() <= 0 || lockedQuota(tenantId) + qty > bucket.getLimitAmount()) {
                throw new BadRequestException("market.cant.cover");
            }
            bucket.setUsed(lockedQuota(tenantId) + qty);
            buckets.save(bucket);
        } else {
            long escrow = Math.multiplyExact(qty, priceCents);
            ensureSink(tenantId, ESCROW);
            ledger.post(tenantId, new PostJournalRequest(
                    "escrow:" + tenantId + ":" + UUID.randomUUID(), "BRC", List.of(
                    new JournalLine(ESCROW, "DEBIT", escrow, null),
                    new JournalLine(TREASURY, "CREDIT", escrow, null))));
        }
    }

    private void releaseCollateral(MarketOrderEntity order) {
        if (MarketOrderEntity.ASK.equals(order.getSide())) {
            QuotaBucketEntity bucket = bucket(order.getTenantId());
            bucket.setUsed(Math.max(0, lockedQuota(order.getTenantId()) - order.getQtyRemaining()));
            buckets.save(bucket);
        } else {
            long refund = Math.multiplyExact(order.getQtyRemaining(), order.getPriceCents());
            if (refund > 0) {
                ensureSink(order.getTenantId(), ESCROW);
                ledger.post(order.getTenantId(), new PostJournalRequest(
                        "escrow-release:" + order.getId() + ":" + UUID.randomUUID(), "BRC", List.of(
                        new JournalLine(TREASURY, "DEBIT", refund, null),
                        new JournalLine(ESCROW, "CREDIT", refund, null))));
            }
        }
    }

    private long lockedQuota(String tenantId) {
        return buckets.findById(new QuotaBucketEntity.Pk(tenantId, RESOURCE)).map(QuotaBucketEntity::getUsed).orElse(0L);
    }

    private QuotaBucketEntity bucket(String tenantId) {
        return buckets.findById(new QuotaBucketEntity.Pk(tenantId, RESOURCE))
                .orElseGet(() -> buckets.save(new QuotaBucketEntity(tenantId, RESOURCE, 0L)));
    }

    /** Cross the taker against the book until it is filled or the book is empty. */
    private void cross(MarketOrderEntity taker) {
        boolean takerIsBid = MarketOrderEntity.BID.equals(taker.getSide());
        List<MarketOrderEntity> book = takerIsBid
                ? orders.asksFor(taker.getPriceCents(), taker.getTenantId(), PageRequest.of(0, BOOK_DEPTH))
                : orders.bidsFor(taker.getPriceCents(), taker.getTenantId(), PageRequest.of(0, BOOK_DEPTH));
        int seq = 0;
        long spent = 0;
        for (MarketOrderEntity restingShadow : book) {
            if (!taker.resting()) {
                break;
            }
            MarketOrderEntity resting = orders.findForUpdate(restingShadow.getId());
            if (resting == null || !resting.resting()
                    || resting.getTenantId().equals(taker.getTenantId())) {
                continue;   // self-trade prevention: the candidate is skipped
            }
            long fillQty = Math.min(taker.getQtyRemaining(), resting.getQtyRemaining());
            if (fillQty <= 0) {
                continue;
            }
            long makerPrice = resting.getPriceCents();   // trades print at the resting price
            long notional = Math.multiplyExact(fillQty, makerPrice);
            long fee = MarketMath.feeOn(notional, feeBips);
            String tradeKey = "fill:" + taker.getId() + ":" + resting.getId() + ":" + (seq++);
            settle(taker, resting, fillQty, makerPrice, fee, tradeKey);
            spent += Math.multiplyExact(fillQty, makerPrice);
            trades.save(new MarketTradeEntity(tradeKey, resting.getId(), taker.getId(),
                    fillQty, makerPrice, fee));
        }
        // a fully-filled BID refunds its unused escrow before anyone notices
        if (takerIsBid && !taker.resting()) {
            long escrowIn = Math.multiplyExact(taker.getQty(), taker.getPriceCents());
            long surplus = escrowIn - spent;
            if (surplus > 0) {
                ensureSink(taker.getTenantId(), ESCROW);
                ledger.post(taker.getTenantId(), new PostJournalRequest(
                        "escrow-surplus:" + taker.getId() + ":" + UUID.randomUUID(), "BRC", List.of(
                        new JournalLine(TREASURY, "DEBIT", surplus, null),
                        new JournalLine(ESCROW, "CREDIT", surplus, null))));
            }
        }
        orders.save(taker);
    }

    /** Money and capacity move in the caller's transaction — all or nothing. */
    private void settle(MarketOrderEntity taker, MarketOrderEntity resting,
                        long qty, long price, long fee, String tradeKey) {
        MarketOrderEntity ask = MarketOrderEntity.ASK.equals(taker.getSide()) ? taker : resting;
        MarketOrderEntity bid = MarketOrderEntity.BID.equals(taker.getSide()) ? taker : resting;
        long notional = Math.multiplyExact(qty, price);

        // BRC leg (memo rail:quota — the Gresham desk watches industrial volume
        // grow). Buyer: escrow pays the clearing desk. Seller: treasury collects
        // proceeds; the fee breaks off into the market-fee sink, the treasury's
        // only market income — a central bank that trades its own book is a
        // conflict of interest with extra steps.
        ensureSink(bid.getTenantId(), ESCROW);
        ensureSink(bid.getTenantId(), CLEARING);
        ledger.post(bid.getTenantId(), new PostJournalRequest(tradeKey + ":buy", "BRC", List.of(
                new JournalLine(CLEARING, "DEBIT", notional, "rail:quota"),
                new JournalLine(ESCROW, "CREDIT", notional, "rail:quota"))));
        ensureSink(ask.getTenantId(), SALES);
        ensureSink(ask.getTenantId(), FEE);
        if (fee > 0) {
            ledger.post(ask.getTenantId(), new PostJournalRequest(tradeKey + ":sell", "BRC", List.of(
                    new JournalLine(TREASURY, "DEBIT", notional - fee, "rail:quota"),
                    new JournalLine(FEE, "DEBIT", fee, "rail:quota"),
                    new JournalLine(SALES, "CREDIT", notional, "rail:quota"))));
        } else {
            ledger.post(ask.getTenantId(), new PostJournalRequest(tradeKey + ":sell", "BRC", List.of(
                    new JournalLine(TREASURY, "DEBIT", notional, "rail:quota"),
                    new JournalLine(SALES, "CREDIT", notional, "rail:quota"))));
        }

        // quota leg: the seller's locked capacity ships — the lock clears with
        // the fill, and the buyer's provision grows by what arrived
        QuotaBucketEntity sellerBucket = bucket(ask.getTenantId());
        sellerBucket.setUsed(Math.max(0, lockedQuota(ask.getTenantId()) - qty));
        buckets.save(sellerBucket);
        QuotaBucketEntity buyerBucket = bucket(bid.getTenantId());
        buyerBucket.growLimit(qty);
        buckets.save(buyerBucket);

        long bidTaken = bid.fill(qty);
        long askTaken = ask.fill(qty);
        if (bidTaken != askTaken || bidTaken != qty) {
            throw new BadRequestException("market.fill.mismatch");
        }
    }

    private void ensureSink(String tenantId, String accountNo) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest(accountNo, "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // sinks are patient
        }
    }

    static MarketOrderResponse toDto(MarketOrderEntity o) {
        return new MarketOrderResponse(o.getId(), o.getTenantId(), o.getSide(), o.getQty(),
                o.getQtyRemaining(), o.getPriceCents(), o.getStatus());
    }
}
