package dev.brp.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * The batch clearing algorithm (docs/14 §6), pure: maximize executable
 * volume, minimize imbalance, anchor to the mint reference — then one
 * uniform clearing price for every fill, mint legs included. Call auctions
 * anchor to the reference precisely so a thin book never prints a midpoint
 * between a 101 ask and a 1000 bid.
 *
 * Bids/asks arrive pre-sorted in time priority (caller's job); the walk
 * preserves it. Same-tenant pairs are skipped during allocation, so realized
 * volume may fall below vol(P*) — money moves only on realized fills, and
 * conservation is the invariant that may not bend.
 */
public final class AuctionMath {

    private AuctionMath() {}

    /** One side's resting interest. orderId null marks a mint quote. */
    public record Quote(long seq, String tenantId, Long orderId,
                        long qtyRemaining, long priceCents) {}

    public record Fill(String buyer, String seller, long qty, long priceCents,
                       Long bidOrderId, Long askOrderId) {}

    /**
     * @param clearPrice null when the book did not cross — no price is the
     *                   honest print for an empty batch
     */
    public record Result(Long clearPrice, long clearedQty, long bidVol,
                         long askVol, long surplus, List<Fill> fills) {}

    public static Result clear(List<Quote> bids, List<Quote> asks, long refCents) {
        TreeSet<Long> candidates = new TreeSet<>();
        bids.forEach(q -> candidates.add(q.priceCents()));
        asks.forEach(q -> candidates.add(q.priceCents()));

        long bestVol = 0;
        Long bestPrice = null;
        long bestSurplus = 0;
        for (long p : candidates) {
            long vol = Math.min(bidVol(bids, p), askVol(asks, p));
            if (vol == 0) {
                continue;
            }
            long surplus = bidVol(bids, p) - askVol(asks, p);
            if (bestPrice == null || better(vol, surplus, p, bestVol, bestSurplus, bestPrice, refCents)) {
                bestVol = vol;
                bestSurplus = surplus;
                bestPrice = p;
            }
        }
        if (bestPrice == null) {
            long bidTotal = bids.stream().mapToLong(Quote::qtyRemaining).sum();
            long askTotal = asks.stream().mapToLong(Quote::qtyRemaining).sum();
            return new Result(null, 0, bidTotal, askTotal, bidTotal - askTotal, List.of());
        }
        List<Fill> fills = allocate(bids, asks, bestPrice);
        long cleared = fills.stream().mapToLong(Fill::qty).sum();
        long bidVol = bidVol(bids, bestPrice);
        long askVol = askVol(asks, bestPrice);
        return new Result(bestPrice, cleared, bidVol, askVol, bidVol - askVol, fills);
    }

    private static long bidVol(List<Quote> bids, long price) {
        return bids.stream().filter(q -> q.priceCents() >= price)
                .mapToLong(Quote::qtyRemaining).sum();
    }

    private static long askVol(List<Quote> asks, long price) {
        return asks.stream().filter(q -> q.priceCents() <= price)
                .mapToLong(Quote::qtyRemaining).sum();
    }

    /** The kink rule: more volume, then flatter book, then nearest the anchor. */
    private static boolean better(long vol, long surplus, long price,
                                  long bestVol, long bestSurplus, long bestPrice,
                                  long refCents) {
        if (vol != bestVol) {
            return vol > bestVol;
        }
        long slack = Math.abs(surplus);
        long bestSlack = Math.abs(bestSurplus);
        if (slack != bestSlack) {
            return slack < bestSlack;
        }
        long distance = Math.abs(price - refCents);
        long bestDistance = Math.abs(bestPrice - refCents);
        if (distance != bestDistance) {
            return distance < bestDistance;
        }
        return price < bestPrice;   // deterministic last resort: the lower print
    }

    /**
     * Uniform price, time priority, same-tenant pairs skipped. A skipped ask
     * stays available for later bids — the skip belongs to the pair, not to
     * the book.
     */
    private static List<Fill> allocate(List<Quote> bids, List<Quote> asks, long price) {
        List<Quote> liveBids = new ArrayList<>(bids.stream()
                .filter(q -> q.priceCents() >= price)
                .sorted(Comparator.comparingLong(Quote::seq)).toList());
        List<Quote> liveAsks = new ArrayList<>(asks.stream()
                .filter(q -> q.priceCents() <= price)
                .sorted(Comparator.comparingLong(Quote::seq)).toList());

        List<Fill> fills = new ArrayList<>();
        int head = 0;   // first ask that still has quantity
        for (Quote bid : liveBids) {
            long want = bid.qtyRemaining();
            int probe = head;
            while (want > 0 && probe < liveAsks.size()) {
                Quote ask = liveAsks.get(probe);
                if (ask.qtyRemaining() <= 0 || ask.tenantId().equals(bid.tenantId())) {
                    probe++;
                    continue;
                }
                long taken = Math.min(want, ask.qtyRemaining());
                want -= taken;
                liveAsks.set(probe, new Quote(ask.seq(), ask.tenantId(), ask.orderId(),
                        ask.qtyRemaining() - taken, ask.priceCents()));
                fills.add(new Fill(bid.tenantId(), ask.tenantId(), taken, price,
                        bid.orderId(), ask.orderId()));
                if (liveAsks.get(probe).qtyRemaining() == 0) {
                    if (probe == head) {
                        head++;
                    }
                    probe++;
                }
            }
        }
        return fills;
    }
}
