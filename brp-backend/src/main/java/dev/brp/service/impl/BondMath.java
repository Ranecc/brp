package dev.brp.service.impl;

/**
 * Pure bond arithmetic (docs/13 §7). The performance factor is the joke's
 * punchline: the treasury pays coupons in ticks it actually had — ran cool
 * and you get the full coupon, ran double over the budget and the coupon
 * halves. Principal is never touched; default is P5's problem.
 */
final class BondMath {

    private BondMath() {}

    /** clamp(threshold / avgMspt, floor, 1.0); a cold server is the cap, not a bonus. */
    static double performanceFactor(double avgMspt, double thresholdMspt, double floor) {
        if (avgMspt <= 0) {
            return 1.0;   // no ticks served at all: nothing was degraded, nothing is trimmed
        }
        double factor = thresholdMspt / avgMspt;
        return Math.max(floor, Math.min(1.0, factor));
    }

    /** Daily coupon per subscription: units x face x rate / 365, floor-rounded. */
    static long couponPerDay(long units, long faceCents, int couponPctAnnual) {
        if (units <= 0 || faceCents <= 0 || couponPctAnnual <= 0) {
            return 0;
        }
        return units * faceCents * couponPctAnnual / 36_500L;
    }

    /** Under-subscribed issues die honestly: below this ratio, everything refunds. */
    static boolean failedAtClose(long soldUnits, long totalUnits, double minFillRatio) {
        return totalUnits <= 0 || soldUnits < Math.ceil(totalUnits * minFillRatio);
    }
}
