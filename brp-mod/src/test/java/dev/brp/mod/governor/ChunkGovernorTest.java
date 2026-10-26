package dev.brp.mod.governor;

import dev.brp.mod.governor.ChunkGovernor.ChunkState;
import dev.brp.mod.governor.ChunkGovernor.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The decision table, mirrored from backend ChunkServiceImpl (docs/09
 * section 4). If these two ever disagree, the machines strike in one
 * world and party in the other — this file exists to prevent that.
 */
class ChunkGovernorTest {

    @Test
    void unregisteredOrDisabledOrBrokeChunksHalt() {
        assertEquals(Verdict.HALT, ChunkGovernor.rule(ChunkState.unregistered()));
        assertEquals(Verdict.HALT, ChunkGovernor.rule(new ChunkState(true, false, 9999)));
        assertEquals(Verdict.HALT, ChunkGovernor.rule(new ChunkState(true, true, 0)));
    }

    @Test
    void lowCreditThrottles() {
        assertEquals(Verdict.THROTTLE, ChunkGovernor.rule(new ChunkState(true, true, 1)));
        assertEquals(Verdict.THROTTLE, ChunkGovernor.rule(new ChunkState(true, true, 60)));
    }

    @Test
    void healthyCreditRuns() {
        assertEquals(Verdict.RUN, ChunkGovernor.rule(new ChunkState(true, true, 61)));
        assertEquals(Verdict.RUN, ChunkGovernor.rule(new ChunkState(true, true, 10_000)));
    }

    @Test
    void graceLineIsConfigurableButBoundarySticks() {
        assertEquals(Verdict.THROTTLE, ChunkGovernor.rule(new ChunkState(true, true, 120), 120));
        assertEquals(Verdict.RUN, ChunkGovernor.rule(new ChunkState(true, true, 121), 120));
    }
}
