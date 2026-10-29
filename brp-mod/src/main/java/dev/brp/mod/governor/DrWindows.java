package dev.brp.mod.governor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The agent-side registry of open demand-response windows (P4, docs/13 §5):
 * the backend dispatches {@code @dr/<jobId>} signals (15 = OPEN, 0 = CLOSE)
 * through the signal plane; game code asks here whether a DR-tagged line may
 * run. Unknown jobs are CLOSED — optimism is not a scheduling policy.
 */
public final class DrWindows {

    private static final Set<String> OPEN = ConcurrentHashMap.newKeySet();

    private DrWindows() {}

    public static void open(String jobId) {
        OPEN.add(jobId);
    }

    public static void close(String jobId) {
        OPEN.remove(jobId);
    }

    public static boolean isOpen(String jobId) {
        return OPEN.contains(jobId);
    }

    /** @return the channel name the backend dispatches this job on. */
    public static String channel(String jobId) {
        return "@dr/" + jobId;
    }

    /** Tests only. */
    static void clear() {
        OPEN.clear();
    }
}
