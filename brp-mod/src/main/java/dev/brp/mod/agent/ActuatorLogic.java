package dev.brp.mod.agent;

import dev.brp.mod.governor.ChunkGovernor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Ticket reconciliation (docs/16 §3): desired = registered && verdict !=
 * HALT. THROTTLE keeps the ticket — a throttled line still runs every Nth
 * window, and a loaded chunk is the prerequisite for running at all; only
 * HALT releases. The budget caps changes per pass so a registry hiccup
 * cannot place forty tickets in one tick — tickets load chunks, chunks cost
 * mspt, and storms are how loaders get themselves banned.
 */
public final class ActuatorLogic {

    private ActuatorLogic() {}

    public record Decision(List<String> toActivate, List<String> toDeactivate) {

        public boolean isEmpty() {
            return toActivate.isEmpty() && toDeactivate.isEmpty();
        }
    }

    public static Decision reconcile(Set<String> registered,
                                     java.util.function.Function<String, ChunkGovernor.Verdict> verdicts,
                                     Predicate<String> held, int budget) {
        List<String> activate = new ArrayList<>();
        List<String> deactivate = new ArrayList<>();
        for (String key : new TreeSet<>(registered)) {
            // fail-closed: a missing verdict is a HALT, never an invitation
            ChunkGovernor.Verdict v = verdicts.apply(key);
            boolean want = v != null && v != ChunkGovernor.Verdict.HALT;
            if (want && !held.test(key) && activate.size() < budget) {
                activate.add(key);
            } else if (!want && held.test(key) && deactivate.size() < budget) {
                deactivate.add(key);
            }
        }
        return new Decision(activate, deactivate);
    }
}
