package dev.brp.mod.spi;

/**
 * Signal execution port (docs/02 section 5, docs/04 section 3).
 *
 * Channels are namespaced strings ("@team/farm_north"); levels are 0..15 to
 * match comparator analog semantics. Execution is last-writer-wins idempotent:
 * the backend push plane is at-least-once, so replays must converge to the
 * same observable redstone state.
 */
public interface SignalPort extends BrpAdapter {

    void set(String channel, int level, Cause cause);

    int level(String channel);

    record Cause(String policyId, String journalId) {
        public static Cause manual() {
            return new Cause("manual", null);
        }
    }
}
