package dev.brp.service.impl;

/**
 * Pure market arithmetic (docs/13 §8): peg-band admission and the fee.
 * The book ordering lives in the repository query (price-time priority).
 */
final class MarketMath {

    private MarketMath() {}

    /** The price must sit inside the peg band — the market trades compute, not peg bets. */
    static boolean insidePegBand(long priceCents, double bandLow, double bandHigh) {
        // peg rates are BRC per quota unit; the order book prices in BRC cents
        double low = bandLow * 100.0;
        double high = bandHigh * 100.0;
        return priceCents >= Math.floor(low) && priceCents <= Math.ceil(high);
    }

    /** Treasury fee on the notional, floor-rounded. */
    static long feeOn(long notionalCents, int feeBips) {
        if (notionalCents <= 0 || feeBips <= 0) {
            return 0;
        }
        return notionalCents * feeBips / 10_000L;
    }
}
