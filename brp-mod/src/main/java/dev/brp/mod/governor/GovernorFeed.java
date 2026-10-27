package dev.brp.mod.governor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The agent-side cache of the backend's governor read model (docs/09
 * section 4). Pure Java, no Minecraft imports: the fetcher (P2-12 agent
 * wiring) publishes states here, game code asks here. Unknown chunks are
 * UNREGISTERED — a chunk the backend never heard of gets the strike.
 */
public final class GovernorFeed {

    private static final Map<String, ChunkGovernor.ChunkState> STATES = new ConcurrentHashMap<>();

    private GovernorFeed() {}

    public static void publish(String dimension, String chunkKey, ChunkGovernor.ChunkState state) {
        STATES.put(key(dimension, chunkKey), state);
    }

    public static ChunkGovernor.ChunkState stateFor(String dimension, String chunkKey) {
        return STATES.getOrDefault(key(dimension, chunkKey), ChunkGovernor.ChunkState.unregistered());
    }

    /** Tests only: the cache forgets on command, unlike the backend. */
    static void clear() {
        STATES.clear();
    }

    private static String key(String dimension, String chunkKey) {
        return dimension + "|" + chunkKey;
    }
}
