package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Window planning (docs/13 §5): cheapest feasible consecutive hours win. */
class DemandResponseMathTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 30, 0, 0);

    private static DemandResponseMath.Hour hour(int h, int pct, double mspt, boolean schedulable) {
        return new DemandResponseMath.Hour(BASE.plusHours(h), pct, mspt, schedulable);
    }

    @Test
    void zeroMinutesIsNotAJob() {
        assertThat(DemandResponseMath.plan(List.of(hour(0, 100, 10, true)), 0,
                BASE, BASE.plusDays(1))).isNull();
    }

    @Test
    void cheapestFeasibleRunWinsEvenWhenLater() {
        var hours = List.of(
                hour(0, 200, 10, true),    // peak, expensive
                hour(1, 200, 10, true),
                hour(2, 60, 10, true),     // valley, cheap
                hour(3, 60, 10, true));
        var plan = DemandResponseMath.plan(hours, 120, BASE.minusHours(1), BASE.plusDays(1));
        assertThat(plan).isNotNull();
        assertThat(plan.start()).isEqualTo(BASE.plusHours(2));
        assertThat(plan.hours()).isEqualTo(2);
    }

    @Test
    void tieOnPriceBreaksByPredictedLoad() {
        var hours = List.of(
                hour(0, 100, 45, true),
                hour(1, 100, 45, true),
                hour(2, 100, 20, true),
                hour(3, 100, 20, true));
        var plan = DemandResponseMath.plan(hours, 120, BASE.minusHours(1), BASE.plusDays(1));
        assertThat(plan.start()).isEqualTo(BASE.plusHours(2));
    }

    @Test
    void deadlineMissMeansNoPlan() {
        var hours = List.of(hour(5, 60, 10, true), hour(6, 60, 10, true));
        assertThat(DemandResponseMath.plan(hours, 120, BASE, BASE.plusHours(5))).isNull();
    }

    @Test
    void unschedulableHoursBreakTheRun() {
        var hours = List.of(
                hour(0, 60, 10, true),
                hour(1, 60, 99, false),    // overloaded hour splits candidates
                hour(2, 60, 10, true),
                hour(3, 60, 10, true));
        var plan = DemandResponseMath.plan(hours, 120, BASE, BASE.plusDays(1));
        assertThat(plan).isNotNull();
        assertThat(plan.start()).isEqualTo(BASE.plusHours(2));
    }

    @Test
    void earliestIsRespected() {
        var hours = List.of(
                hour(0, 60, 10, true),
                hour(1, 60, 10, true),
                hour(2, 60, 10, true));
        var plan = DemandResponseMath.plan(hours, 120, BASE.plusHours(1), BASE.plusDays(1));
        assertThat(plan.start()).isEqualTo(BASE.plusHours(1));
    }

    @Test
    void gappedHoursAreNotConsecutive() {
        // hours at 0 and 2 (1 missing): a 2-hour run must not bridge the gap
        var hours = List.of(
                hour(0, 60, 10, true),
                hour(2, 60, 10, true));
        assertThat(DemandResponseMath.plan(hours, 120, BASE, BASE.plusDays(1))).isNull();
    }
}
