package dev.brp.mod.line;

/**
 * P2-15: the agent-side cache of this tenant's APS fair-share factor.
 * Default 1.0 — before the first successful poll, the line assumes the
 * server has not decided to be scarce yet (and the governor can still
 * strike it independently).
 */
public final class ApsFeed {

    private static volatile double factor = 1.0;

    private ApsFeed() {}

    public static void publish(double f) {
        factor = Math.max(0.0, Math.min(1.0, f));
    }

    public static double factor() {
        return factor;
    }

    /** Tests only. */
    static void reset() {
        factor = 1.0;
    }
}
