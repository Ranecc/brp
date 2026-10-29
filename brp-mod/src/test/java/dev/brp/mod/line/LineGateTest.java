package dev.brp.mod.line;

import dev.brp.mod.governor.ChunkGovernor.Verdict;
import dev.brp.mod.line.LineGate.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The haircut in windows (docs/09 section 5): HALT strikes, 1.0 runs every
 * window, 0.5 runs every second one — APS arithmetic you can watch.
 */
class LineGateTest {

    @Test
    void fullFactorRunsEveryWindow() {
        for (long w = 0; w < 5; w++) {
            assertEquals(Decision.RUNNING, LineGate.decide(Verdict.RUN, 1.0, w));
        }
    }

    @Test
    void halfFactorRunsEverySecondWindow() {
        assertEquals(Decision.RUNNING, LineGate.decide(Verdict.RUN, 0.5, 0));
        assertEquals(Decision.THROTTLED, LineGate.decide(Verdict.RUN, 0.5, 1));
        assertEquals(Decision.RUNNING, LineGate.decide(Verdict.RUN, 0.5, 2));
        assertEquals(Decision.RUNNING, LineGate.decide(Verdict.RUN, 0.25, 0));
        assertEquals(Decision.THROTTLED, LineGate.decide(Verdict.RUN, 0.25, 1));
        assertEquals(Decision.RUNNING, LineGate.decide(Verdict.RUN, 0.25, 4));
    }

    @Test
    void strikesOverrideEverything() {
        assertEquals(Decision.ON_STRIKE, LineGate.decide(Verdict.HALT, 1.0, 0));
        assertEquals(Decision.ON_STRIKE, LineGate.decide(Verdict.RUN, 0.0, 0));
        assertEquals(0.0, LineGate.effectiveRate(Verdict.HALT, 1.0));
    }

    @Test
    void apsFeedClampsAndDefaults() {
        ApsFeed.reset();
        assertEquals(1.0, ApsFeed.factor());   // the server has not decided to be scarce yet
        ApsFeed.publish(1.7);
        assertEquals(1.0, ApsFeed.factor());
        ApsFeed.publish(-0.3);
        assertEquals(0.0, ApsFeed.factor());
        ApsFeed.reset();
    }

    @Test
    void closedDrWindowThrottlesButNeverStrikes() {
        // P4 (docs/13 section 5): the grid shed the line, the meter still gets paid
        assertEquals(Decision.THROTTLED, LineGate.decide(Verdict.RUN, 1.0, 0, false));
        assertEquals(Decision.THROTTLED, LineGate.decide(Verdict.RUN, 1.0, 3, false));
        assertEquals(Decision.RUNNING, LineGate.decide(Verdict.RUN, 1.0, 0, true));
        // a strike stays a strike even with the window open
        assertEquals(Decision.ON_STRIKE, LineGate.decide(Verdict.HALT, 1.0, 0, true));
        assertEquals(Decision.ON_STRIKE, LineGate.decide(Verdict.RUN, 0.0, 0, false));
    }
}
