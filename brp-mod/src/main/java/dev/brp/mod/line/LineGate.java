package dev.brp.mod.line;

import dev.brp.mod.governor.ChunkGovernor.Verdict;

/**
 * P2-15 rate cap (pure): the verdict times the fair-share factor decides
 * each window. HALT strikes; a factor of 1.0 runs every window; 0.5 runs
 * every second window — the haircut, expressed in skipped windows.
 */
public final class LineGate {

    public enum Decision { RUNNING, THROTTLED, ON_STRIKE }

    private LineGate() {}

    public static Decision decide(Verdict verdict, double factor, long windowIndex) {
        return decide(verdict, factor, windowIndex, true);
    }

    /**
     * P4 demand response (docs/13 §5): a closed DR window THROTTLES the line
     * (shed, not strike — the grid paused it, the meter still gets paid).
     */
    public static Decision decide(Verdict verdict, double factor, long windowIndex, boolean drOpen) {
        if (verdict == Verdict.HALT || factor <= 0) {
            return Decision.ON_STRIKE;
        }
        if (!drOpen) {
            return Decision.THROTTLED;
        }
        long every = Math.max(1, (long) Math.ceil(1.0 / Math.min(1.0, factor)));
        return windowIndex % every == 0 ? Decision.RUNNING : Decision.THROTTLED;
    }

    /** @return the effective run rate in (0,1] for display and gossip. */
    public static double effectiveRate(Verdict verdict, double factor) {
        if (verdict == Verdict.HALT || factor <= 0) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(factor, 0.0));
    }
}
