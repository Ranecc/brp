package dev.brp.mod.governor;

/**
 * The chunk governor's decision table (docs/09 section 4), agent-side
 * mirror of the backend read model. Pure function, no Minecraft imports,
 * unit-tested on both sides of the wall.
 *
 * HALT governs BRP machinery only. Player movement, interaction and
 * rendering are none of the governor's business: the machines go on
 * strike, the world does not.
 */
public final class ChunkGovernor {

    public enum Verdict { RUN, THROTTLE, HALT }

    /** Governor inputs per chunk; mirrors ChunkService's read model. */
    public record ChunkState(boolean registered, boolean adminEnabled, long creditMinutes) {

        public static ChunkState unregistered() {
            return new ChunkState(false, false, 0);
        }
    }

    /** Same default as brp.chunk.governor.grace-minutes on the backend. */
    public static final long DEFAULT_GRACE_MINUTES = 60;

    private ChunkGovernor() {}

    public static Verdict rule(ChunkState s, long graceMinutes) {
        if (!s.registered() || !s.adminEnabled() || s.creditMinutes() <= 0) {
            return Verdict.HALT;
        }
        if (s.creditMinutes() <= graceMinutes) {
            return Verdict.THROTTLE;
        }
        return Verdict.RUN;
    }

    public static Verdict rule(ChunkState s) {
        return rule(s, DEFAULT_GRACE_MINUTES);
    }
}
