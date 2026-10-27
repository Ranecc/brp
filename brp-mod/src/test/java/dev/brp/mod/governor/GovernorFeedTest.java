package dev.brp.mod.governor;

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
    void unknownChunksHaltByDefault() {
        assertEquals(Verdict.HALT, GovernorFeed.verdictFor("minecraft:overworld", "0,0"));
    }

    @Test
    void publishedVerdictsAreServedAndScopedByDimension() {
        GovernorFeed.publish("minecraft:overworld", "5,5", Verdict.RUN);
        assertEquals(Verdict.RUN, GovernorFeed.verdictFor("minecraft:overworld", "5,5"));
        assertNotEquals(Verdict.RUN, GovernorFeed.verdictFor("minecraft:the_nether", "5,5"));
        assertNotEquals(Verdict.RUN, GovernorFeed.verdictFor("minecraft:overworld", "5,6"));
    }
}
