package dev.brp.mod.governor;

import dev.brp.mod.governor.ChunkGovernor.ChunkState;
import dev.brp.mod.governor.ChunkGovernor.Verdict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The feed defaults to the strike: any chunk the backend never vouched for
 * is unregistered, and unregistered means HALT. Optimism is not a policy.
 */
class GovernorFeedTest {

    @Test
    void unknownChunksAreUnregisteredAndHalt() {
        assertEquals(ChunkState.unregistered(), GovernorFeed.stateFor("minecraft:overworld", "0,0"));
        assertEquals(Verdict.HALT, ChunkGovernor.rule(GovernorFeed.stateFor("minecraft:overworld", "0,0")));
    }

    @Test
    void publishedStatesAreServedAndScopedByDimension() {
        GovernorFeed.publish("minecraft:overworld", "5,5", new ChunkState(true, true, 500));
        assertEquals(Verdict.RUN, ChunkGovernor.rule(GovernorFeed.stateFor("minecraft:overworld", "5,5")));
        assertNotEquals(Verdict.RUN,
                ChunkGovernor.rule(GovernorFeed.stateFor("minecraft:the_nether", "5,5")));
    }
}
