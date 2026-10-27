package dev.brp.common.event;

/**
 * Event type registry shared by agent and backend (contract:
 * brp-contract/events.md). RELIABILITY drives the agent WAL drop policy.
 */
public final class BrpEventTypes {

    public enum Reliability { CRITICAL, TELEMETRY }

    public static final String PLAYER_JOINED = "player.joined";
    public static final String PLAYER_LEFT = "player.left";
    public static final String PLAYER_SYNC = "player.sync";
    public static final String INVENTORY_SNAPSHOT = "inventory.snapshot";
    public static final String MACHINE_STATS = "machine.stats";
    public static final String FLOW_SAMPLE = "flow.sample";
    public static final String SIGNAL_EXECUTED = "signal.executed";
    public static final String AGENT_HEARTBEAT = "agent.heartbeat";
    public static final String MACHINE_PLACED = "machine.placed";
    public static final String CHUNK_PERFORMANCE = "chunk.performance";
    public static final String QUOTA_EXCEEDED = "quota.exceeded";
    public static final String CHUNK_LOAD_MINUTES = "chunk.load.minutes";
    public static final String MARKET_PRICE_OBSERVED = "market.price.observed";   // R-6: CPI feed (docs/10)

    private BrpEventTypes() {}
}
