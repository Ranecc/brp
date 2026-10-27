package dev.brp.mod.governor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The agent-side cache of the backend's governor read model (docs/09
 * section 4), P2-12 wiring: the runtime polls getGovernor() and publishes
 * verdicts here; game code asks here. Unknown chunks get HALT — a chunk
 * the backend never vouched for is on strike, optimism is not a policy.
 */
public final class GovernorFeed {

    private static final Map<String, ChunkGovernor.Verdict> VERDICTS = new ConcurrentHashMap<>();

    private GovernorFeed() {}

    public static void publish(String dimension, String chunkKey, ChunkGovernor.Verdict verdict) {
        VERDICTS.put(key(dimension, chunkKey), verdict);
    }

    public static ChunkGovernor.Verdict verdictFor(String dimension, String chunkKey) {
        return VERDICTS.getOrDefault(key(dimension, chunkKey), ChunkGovernor.Verdict.HALT);
    }

    /** Tests only: the cache forgets on command, unlike the backend. */
    static void clear() {
        VERDICTS.clear();
    }

    static String key(String dimension, String chunkKey) {
        return dimension + "|" + chunkKey;
    }
}
