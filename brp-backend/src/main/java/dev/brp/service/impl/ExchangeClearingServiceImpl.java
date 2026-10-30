package dev.brp.service.impl;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ExchangeDtos.ClearResponse;
import dev.brp.dto.ExchangeDtos.ClearRunResponse;
import dev.brp.dto.ExchangeDtos.TradeResponse;
import dev.brp.entity.ExchangeBatchEntity;
import dev.brp.entity.ExchangeClearEntity;
import dev.brp.entity.ExchangeItemEntity;
import dev.brp.entity.ExchangeOrderEntity;
import dev.brp.entity.ExchangeTradeEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.ExchangeBatchRepository;
import dev.brp.repository.ExchangeClearRepository;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.repository.ExchangeOrderRepository;
import dev.brp.repository.ExchangeTradeRepository;
import dev.brp.service.ExchangeClearingService;
import dev.brp.service.ExchangeMintService;
import dev.brp.service.ExchangeWarehouseService;
import dev.brp.service.LedgerService;
import dev.brp.service.MarketPriceService;
import dev.brp.service.PegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The batch auction (docs/14 §6). One insert-only guard per batch_seq, then
 * a single transaction per batch: for each item with resting orders — mint
 * quotes derived from the reserve, the kink algorithm, uniform-price
 * settlement on the same ledger accounts as the quota market, the ref crawl,
 * the CPI feed, and the emerald leg phoning the peg band. All or nothing;
 * unfilled remainders wait for the next bell.
 */
@Service
public class ExchangeClearingServiceImpl implements ExchangeClearingService {

    private static final String ESCROW = "market-escrow";
    private static final String SALES = "market-sales";
    private static final String CLEARING = "market-clearing";
    private static final String FEE = "market-fee";
    private static final String TREASURY = "treasury";

    private final ExchangeOrderRepository orders;
    private final ExchangeItemRepository items;
    private final ExchangeTradeRepository trades;
    private final ExchangeClearRepository clears;
    private final ExchangeBatchRepository batches;
    private final ExchangeMintService mint;
    private final ExchangeWarehouseService warehouse;
    private final LedgerService ledger;
    private final MarketPriceService marketPrice;
    private final PegService peg;
    private final TransactionTemplate tx;
    private final int feeBips;
    private final double refCrawl;
    private final long refMinQty;
    private final boolean autoObserve;

    public ExchangeClearingServiceImpl(ExchangeOrderRepository orders,
                                       ExchangeItemRepository items,
                                       ExchangeTradeRepository trades,
                                       ExchangeClearRepository clears,
                                       ExchangeBatchRepository batches,
                                       ExchangeMintService mint,
                                       ExchangeWarehouseService warehouse,
                                       LedgerService ledger,
                                       MarketPriceService marketPrice,
                                       PegService peg,
                                       TransactionTemplate tx,
                                       @Value("${brp.exchange.fee-bips:50}") int feeBips,
                                       @Value("${brp.exchange.ref-crawl:0.25}") double refCrawl,
                                       @Value("${brp.exchange.ref-min-qty:8}") long refMinQty,
                                       @Value("${brp.peg.auto-observe:true}") boolean autoObserve) {
        this.orders = orders;
        this.items = items;
        this.trades = trades;
        this.clears = clears;
        this.batches = batches;
        this.mint = mint;
        this.warehouse = warehouse;
        this.ledger = ledger;
        this.marketPrice = marketPrice;
        this.peg = peg;
        this.tx = tx;
        this.feeBips = feeBips;
        this.refCrawl = refCrawl;
        this.refMinQty = refMinQty;
        this.autoObserve = autoObserve;
    }

    @Scheduled(fixedDelayString = "${brp.exchange.clear-ms:300000}",
            initialDelayString = "${brp.exchange.clear-ms:300000}")
    public void scheduledBatch() {
        runBatch();
    }

    @Override
    public ClearRunResponse runBatch() {
        long seq = batches.findTopByOrderByBatchSeqDesc().map(b -> b.getBatchSeq() + 1).orElse(1L);
        try {
            return tx.execute(status -> {
                batches.saveAndFlush(new ExchangeBatchEntity(seq));   // the guard
                return clearAll(seq);
            });
        } catch (DataIntegrityViolationException someoneRangTheBellFirst) {
            ExchangeBatchEntity current = batches.findById(seq).orElse(null);
            return current != null
                    ? new ClearRunResponse(seq, current.getItemsCleared(), current.getTradesCleared())
                    : new ClearRunResponse(seq, 0, 0);
        }
    }

    private ClearRunResponse clearAll(long seq) {
        mint.ensureAccounts();
        Set<String> itemIds = new LinkedHashSet<>(orders.itemIdsWithResting());
        int tradeCount = 0;
        for (String itemId : itemIds) {
            tradeCount += clearItem(seq, itemId);
        }
        ExchangeBatchEntity batch = batches.findById(seq).orElseThrow();
        batch.tally(itemIds.size(), tradeCount);
        batches.save(batch);
        return new ClearRunResponse(seq, itemIds.size(), tradeCount);
    }

    /** @return trades booked for this item in this batch. */
    private int clearItem(long seq, String itemId) {
        ExchangeItemEntity item = items.findById(itemId).orElseThrow();
        List<AuctionMath.Quote> bids = new ArrayList<>();
        List<AuctionMath.Quote> asks = new ArrayList<>();
        for (ExchangeOrderEntity order : orders.restingForItem(itemId)) {
            AuctionMath.Quote quote = new AuctionMath.Quote(order.getId(), order.getTenantId(),
                    order.getId(), order.getQtyRemaining(), order.getPriceCents());
            (ExchangeOrderEntity.BID.equals(order.getSide()) ? bids : asks).add(quote);
        }
        AuctionMath.Quote mintBid = mint.bidQuote(item);
        AuctionMath.Quote mintAsk = mint.askQuote(item);
        if (mintBid != null) {
            bids.add(mintBid);
        }
        if (mintAsk != null) {
            asks.add(mintAsk);
        }
        AuctionMath.Result result = AuctionMath.clear(bids, asks, item.getRefCents());
        long refBefore = item.getRefCents();
        long refAfter = refBefore;
        long mintBidFilled = 0;
        long mintAskFilled = 0;
        int booked = 0;

        if (result.clearPrice() != null) {
            int fillIdx = 0;
            for (AuctionMath.Fill fill : result.fills()) {
                String tradeKey = "bourse-fill:" + seq + ":" + itemId + ":" + (fillIdx++);
                settle(fill, itemId, tradeKey, seq);
                if (ExchangeMintServiceImpl.TENANT.equals(fill.buyer())) {
                    mintBidFilled += fill.qty();
                }
                if (ExchangeMintServiceImpl.TENANT.equals(fill.seller())) {
                    mintAskFilled += fill.qty();
                }
                booked++;
            }
            refundFilledBidSurpluses(result);
            if (result.clearedQty() >= refMinQty && !item.isPinned()) {
                item.crawl(result.clearPrice(), refCrawl);
                refAfter = item.getRefCents();
                items.save(item);
            }
            marketPrice.record(itemId, result.clearPrice(), "BRC", result.clearedQty(),
                    "bourse", "clear");
            if (autoObserve && "minecraft:emerald".equals(itemId)) {
                peg.observe("emerald", result.clearPrice() / 100.0);
            }
        }
        clears.save(new ExchangeClearEntity(seq, itemId, result.clearPrice(),
                result.clearedQty(), result.bidVol(), result.askVol(), result.surplus(),
                mintBidFilled, mintAskFilled, refBefore, refAfter));
        return booked;
    }

    /** Money, items, and order rows move in the caller's single transaction. */
    private void settle(AuctionMath.Fill fill, String itemId, String tradeKey, long seq) {
        long notional = Math.multiplyExact(fill.qty(), fill.priceCents());
        long fee = MarketMath.feeOn(notional, feeBips);
        String memo = "bourse:" + itemId;

        if (ExchangeMintServiceImpl.TENANT.equals(fill.buyer())) {
            ensureSink(ExchangeMintServiceImpl.TENANT, CLEARING);
            ledger.post(ExchangeMintServiceImpl.TENANT, new PostJournalRequest(
                    tradeKey + ":buy", "BRC", List.of(
                    new JournalLine(CLEARING, "DEBIT", notional, memo),
                    new JournalLine(TREASURY, "CREDIT", notional, memo))));
        } else {
            ensureSink(fill.buyer(), ESCROW);
            ensureSink(fill.buyer(), CLEARING);
            ledger.post(fill.buyer(), new PostJournalRequest(
                    tradeKey + ":buy", "BRC", List.of(
                    new JournalLine(CLEARING, "DEBIT", notional, memo),
                    new JournalLine(ESCROW, "CREDIT", notional, memo))));
        }
        ensureSink(fill.seller(), SALES);
        ensureSink(fill.seller(), FEE);
        if (fee > 0) {
            ledger.post(fill.seller(), new PostJournalRequest(
                    tradeKey + ":sell", "BRC", List.of(
                    new JournalLine(TREASURY, "DEBIT", notional - fee, memo),
                    new JournalLine(FEE, "DEBIT", fee, memo),
                    new JournalLine(SALES, "CREDIT", notional, memo))));
        } else {
            ledger.post(fill.seller(), new PostJournalRequest(
                    tradeKey + ":sell", "BRC", List.of(
                    new JournalLine(TREASURY, "DEBIT", notional, memo),
                    new JournalLine(SALES, "CREDIT", notional, memo))));
        }

        if (ExchangeMintServiceImpl.TENANT.equals(fill.seller())) {
            warehouse.ship(ExchangeMintServiceImpl.TENANT, fill.buyer(), itemId, fill.qty(),
                    tradeKey, "wh-fill:" + tradeKey);
        } else {
            warehouse.deliver(fill.seller(), fill.buyer(), itemId, fill.qty(),
                    tradeKey, "wh-fill:" + tradeKey);
        }

        if (fill.bidOrderId() != null) {
            ExchangeOrderEntity bid = orders.findForUpdate(fill.bidOrderId());
            bid.fill(fill.qty());
            orders.save(bid);
        }
        if (fill.askOrderId() != null) {
            ExchangeOrderEntity ask = orders.findForUpdate(fill.askOrderId());
            ask.fill(fill.qty());
            orders.save(ask);
        }
        trades.save(new ExchangeTradeEntity(seq, itemId,
                fill.buyer(), fill.seller(), fill.qty(), fill.priceCents(), fee,
                fill.bidOrderId(), fill.askOrderId()));
    }

    /**
     * A bid that just filled releases its unused escrow — order price minus
     * what the uniform clears actually charged it, summed from the trade
     * ledger so no counters live on the entity.
     */
    private void refundFilledBidSurpluses(AuctionMath.Result result) {
        Set<Long> filledBids = new LinkedHashSet<>();
        for (AuctionMath.Fill fill : result.fills()) {
            if (fill.bidOrderId() != null) {
                filledBids.add(fill.bidOrderId());
            }
        }
        for (Long orderId : filledBids) {
            ExchangeOrderEntity bid = orders.findById(orderId).orElse(null);
            if (bid == null || !ExchangeOrderEntity.FILLED.equals(bid.getStatus())) {
                continue;
            }
            long settled = trades.findByBidOrderId(orderId).stream()
                    .mapToLong(t -> t.getQty() * t.getPriceCents()).sum();
            long surplus = Math.multiplyExact(bid.getQty(), bid.getPriceCents()) - settled;
            if (surplus > 0) {
                ensureSink(bid.getTenantId(), ESCROW);
                ledger.post(bid.getTenantId(), new PostJournalRequest(
                        "bourse-escrow-surplus:" + orderId, "BRC", List.of(
                        new JournalLine(TREASURY, "DEBIT", surplus, null),
                        new JournalLine(ESCROW, "CREDIT", surplus, null))));
            }
        }
    }

    @Override
    public List<ClearResponse> clears(String itemId, int limit) {
        return clears.findByItemIdOrderByBatchSeqDesc(itemId, PageRequest.of(0, Math.min(limit, 200)))
                .stream().map(ExchangeClearingServiceImpl::toDto).toList();
    }

    @Override
    public List<TradeResponse> trades(String itemId, int limit) {
        return trades.findByItemIdOrderByIdDesc(itemId, PageRequest.of(0, Math.min(limit, 200)))
                .stream().map(ExchangeClearingServiceImpl::toDto).toList();
    }

    private void ensureSink(String tenantId, String accountNo) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest(accountNo, "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // sinks are patient
        }
    }

    static ClearResponse toDto(ExchangeClearEntity c) {
        return new ClearResponse(c.getBatchSeq(), c.getItemId(), c.getPriceCents(),
                c.getClearedQty(), c.getBidVol(), c.getAskVol(),
                c.getMintBidQty(), c.getMintAskQty(), c.getRefBefore(), c.getRefAfter());
    }

    static TradeResponse toDto(ExchangeTradeEntity t) {
        return new TradeResponse(t.getId(), t.getBatchSeq(), t.getItemId(), t.getBuyer(),
                t.getSeller(), t.getQty(), t.getPriceCents(), t.getFeeCents());
    }
}
