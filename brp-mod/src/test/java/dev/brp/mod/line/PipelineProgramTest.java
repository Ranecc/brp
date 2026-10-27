package dev.brp.mod.line;

import dev.brp.mod.line.PipelineProgram.EnergyAvailable;
import dev.brp.mod.line.PipelineProgram.Inputs;
import dev.brp.mod.line.PipelineProgram.PriceWithin;
import dev.brp.mod.line.PipelineProgram.QuotaRemaining;
import dev.brp.mod.line.PipelineProgram.Step;
import dev.brp.mod.line.PipelineProgram.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conditional boot, by the book (docs/09 section 5): every condition is a
 * cheap local check, and blockedBy() names the shirkers so the dispatch
 * terminal can gossip accurately.
 */
class PipelineProgramTest {

    private static final Inputs HAPPY = new Inputs(
            /*energy*/ 512.0, /*quota*/ 1000L, /*spot price*/ 3.5, /*tick*/ 6000L);

    @Test
    void allConditionsHoldingAllowsBoot() {
        var program = new PipelineProgram(List.of(
                new Step("crush", new EnergyAvailable(100)),
                new Step("smelt", new QuotaRemaining(50)),
                new Step("sell", new PriceWithin(3.0, 4.0)),
                new Step("maintain", new TimeWindow(0, 12000))));
        assertTrue(program.bootAllowed(HAPPY));
        assertEquals(List.of(), program.blockedBy(HAPPY));
    }

    @Test
    void failingConditionsAreNamedNotGuessed() {
        var program = new PipelineProgram(List.of(
                new Step("crush", new EnergyAvailable(1000)),   // starving
                new Step("sell", new PriceWithin(4.0, 5.0))));  // price band left us behind
        assertEquals(List.of("crush", "sell"), program.blockedBy(HAPPY));
    }

    @Test
    void priceWindowIsInclusiveAndTimeWindowIsHalfOpen() {
        assertTrue(new PriceWithin(3.0, 4.0).satisfiedBy(new Inputs(0, 0, 3.0, 0)));
        assertTrue(new PriceWithin(3.0, 4.0).satisfiedBy(new Inputs(0, 0, 4.0, 0)));
        assertTrue(new TimeWindow(100, 200).satisfiedBy(new Inputs(0, 0, 0, 100)));
        org.junit.jupiter.api.Assertions.assertFalse(
                new TimeWindow(100, 200).satisfiedBy(new Inputs(0, 0, 0, 200)));
    }

    @Test
    void emptyProgramBootsUnconditionallyButGovernorStillRules() {
        assertTrue(new PipelineProgram(List.of()).bootAllowed(HAPPY));
        // The empty program boots on conditions alone; a governor HALT
        // overrides it upstream — the gate outranks the plan (docs/09 §5).
    }
}
