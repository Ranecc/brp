package dev.brp.mod.agent;

import dev.brp.mod.governor.ChunkGovernor.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ticket desk (docs/16 §3): only HALT releases, THROTTLE keeps the
 * ticket (a throttled line still runs every Nth window), and the per-pass
 * budget keeps a registry hiccup from placing forty tickets in one tick.
 */
class ActuatorLogicTest {

    private static final Set<String> REGISTERED = Set.of(
            "overworld|10,20", "overworld|11,20", "overworld|12,20", "overworld|13,20");

    @Test
    void runAndThrottleHoldTicketsHaltReleases() {
        var verdicts = Map.of(
                "overworld|10,20", Verdict.RUN,
                "overworld|11,20", Verdict.THROTTLE,
                "overworld|12,20", Verdict.HALT,
                "overworld|13,20", Verdict.HALT);
        var d = ActuatorLogic.reconcile(REGISTERED, verdicts::get,
                k -> false, 16);
        // 10,20 and 11,20 want tickets (THROTTLE included); 12,20 and 13,20
        // are unheld already, so releasing them is a no-op, not a decision
        assertEquals(2, d.toActivate().size());
        assertTrue(d.toDeactivate().isEmpty());
    }

    @Test
    void heldChunksUnderHaltAreTheOnesThatGetReleased() {
        var d = ActuatorLogic.reconcile(REGISTERED,
                k -> Verdict.HALT, k -> k.equals("overworld|10,20"), 16);
        assertTrue(d.toActivate().isEmpty());
        assertEquals(List.of("overworld|10,20"), d.toDeactivate());
    }

    @Test
    void theBudgetCapsEachDirectionPerPass() {
        var d = ActuatorLogic.reconcile(REGISTERED, k -> Verdict.RUN,
                k -> false, 1);
        assertEquals(1, d.toActivate().size());   // storm guard: the rest wait a pass
        var d2 = ActuatorLogic.reconcile(REGISTERED, k -> Verdict.HALT,
                k -> true, 1);
        assertEquals(1, d2.toDeactivate().size());
    }

    @Test
    void settledStateIsANoOp() {
        var verdicts = Map.of(
                "overworld|10,20", Verdict.RUN,
                "overworld|11,20", Verdict.HALT);
        var held = Set.of("overworld|10,20");
        var d = ActuatorLogic.reconcile(REGISTERED, verdicts::get, held::contains, 16);
        assertTrue(d.isEmpty());
    }

    @Test
    void unknownVerdictIsHaltByDefault() {
        var d = ActuatorLogic.reconcile(REGISTERED, k -> Verdict.HALT,
                k -> false, 16);
        assertTrue(d.isEmpty());   // nothing held, nothing desired: quiet desk
    }
}
