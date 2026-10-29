package dev.brp.service.impl;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pure window planning (docs/13 §5): find the consecutive run of schedulable
 * hours before the deadline that costs least — tariff multiplier first,
 * predicted load second. That IS 平峰填谷, as an argmax. Static so the
 * table-driven tests can torture it without a database.
 */
final class DemandResponseMath {

    private DemandResponseMath() {}

    /** One candidate hour from the forecast. */
    record Hour(LocalDateTime start, int multiplierPct, double predictedMspt, boolean schedulable) {}

    record Plan(LocalDateTime start, int hours) {}

    /**
     * @param hours forecast, any order; must be aligned to the hour
     * @param estMinutes minutes the job needs (packed into whole hours)
     * @param earliest job may not start before this
     * @param deadline job must FINISH by this
     * @return the cheapest feasible consecutive plan, or null when the
     *         deadline cannot be met — a promise to schedule is not a bill
     */
    static Plan plan(List<Hour> hours, long estMinutes, LocalDateTime earliest, LocalDateTime deadline) {
        if (estMinutes <= 0) {
            return null;
        }
        int need = (int) Math.ceil(estMinutes / 60.0);
        List<Hour> sorted = hours.stream()
                .filter(h -> !h.start().isBefore(earliest.truncatedTo(java.time.temporal.ChronoUnit.HOURS)))
                .sorted(java.util.Comparator.comparing(Hour::start))
                .toList();
        Plan best = null;
        long bestCost = Long.MAX_VALUE;
        double bestLoad = Double.MAX_VALUE;
        for (int i = 0; i + need <= sorted.size(); i++) {
            boolean feasible = true;
            long cost = 0;
            double load = 0;
            for (int k = 0; k < need; k++) {
                Hour h = sorted.get(i + k);
                if (!h.schedulable() || !consecutive(sorted.get(i), h, k)) {
                    feasible = false;
                    break;
                }
                cost += h.multiplierPct();
                load += h.predictedMspt();
                if (h.start().plusHours(k + 1L).isAfter(deadline)) {
                    feasible = false;
                    break;
                }
            }
            if (feasible && (cost < bestCost || (cost == bestCost && load < bestLoad))) {
                best = new Plan(sorted.get(i).start(), need);
                bestCost = cost;
                bestLoad = load;
            }
        }
        return best;
    }

    /** Hours are consecutive when index k sits exactly k hours after the start. */
    private static boolean consecutive(Hour start, Hour h, int k) {
        return h.start().equals(start.start().plusHours(k));
    }
}
