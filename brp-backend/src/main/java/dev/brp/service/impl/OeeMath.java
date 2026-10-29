package dev.brp.service.impl;

/**
 * OEE arithmetic (docs/13 §9): A is measured from the downtime ledger, P is
 * actual production against the trailing baseline, Q stays 1.0 and is
 * DISCLOSED as such until rework data exists. Static and table-driven.
 */
final class OeeMath {

    private OeeMath() {}

    /** Availability = 1 - downtime/period, floored at 0. */
    static double availability(long downtimeMinutes, long periodMinutes) {
        if (periodMinutes <= 0) {
            return 1.0;
        }
        double a = 1.0 - downtimeMinutes / (double) periodMinutes;
        return Math.max(0.0, Math.min(1.0, a));
    }

    /**
     * Performance = actual / baseline. Cold start (no baseline) is honestly
     * 1.0 — the number says "nothing to compare", not "perfect".
     */
    static double performance(long actual, long baseline) {
        if (baseline <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, actual / (double) baseline));
    }
}
