package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The rebaselined Gresham (docs/12 §10): behavioral, per-rail vs its own
 * moving baseline. Consumer dominance is Tuesday; industrial collapse
 * while consumer holds is the crime.
 */
class GreshamEvaluateTest {

    private static final LocalDate W1 = LocalDate.of(2026, 9, 7);
    private static final LocalDate W2 = W1.plusWeeks(1);
    private static final LocalDate W3 = W1.plusWeeks(2);
    private static final LocalDate W4 = W1.plusWeeks(3);
    private static final LocalDate W5 = W1.plusWeeks(4);   // current

    private Map<LocalDate, Map<String, Long>> weeks(long[] emerald, long[] quota) {
        LocalDate[] keys = {W1, W2, W3, W4, W5};
        Map<LocalDate, Map<String, Long>> out = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Long> legs = new HashMap<>();
            if (emerald[i] > 0) legs.put("emerald", emerald[i]);
            if (quota[i] > 0) legs.put("quota", quota[i]);
            out.put(keys[i], legs);
        }
        return out;
    }

    @Test
    void industrialCollapseWhileConsumerHoldsAlarms() {
        // healthy baselines: 1000 consumer / 200 industrial per week;
        // current week: consumer holds (1100), industrial collapses to 60 (<50% of 200)
        var signal = GreshamServiceImpl.evaluate(
                weeks(new long[]{1000, 1000, 1000, 1000, 1100},
                      new long[]{200, 200, 200, 200, 60}),
                4, 0.5, 1.0);
        assertThat(signal.alarming()).isTrue();
        assertThat(signal.industrialRatio()).isCloseTo(0.3, within(1e-9));
    }

    @Test
    void consumerDominanceIsJustTuesday() {
        // the owner's scenario: consumer rail at 99.5% of volume, industrial
        // rail at its NORMAL level — no collapse, no alarm, ever
        var signal = GreshamServiceImpl.evaluate(
                weeks(new long[]{1000, 1000, 1000, 1000, 5000},
                      new long[]{200, 200, 200, 200, 200}),
                4, 0.5, 1.0);
        assertThat(signal.alarming()).isFalse();
    }

    @Test
    void bothLegsCollapsingIsARecessionNotGresham() {
        var signal = GreshamServiceImpl.evaluate(
                weeks(new long[]{1000, 1000, 1000, 1000, 300},
                      new long[]{200, 200, 200, 200, 60}),
                4, 0.5, 1.0);
        assertThat(signal.alarming()).isFalse();   // consumer did NOT hold
    }

    @Test
    void coldStartKeepsSilent() {
        // only the current week exists: no baseline, no accusation
        var signal = GreshamServiceImpl.evaluate(
                weeks(new long[]{0, 0, 0, 0, 5000}, new long[]{0, 0, 0, 0, 200}),
                4, 0.5, 1.0);
        assertThat(signal.alarming()).isFalse();
    }

    @Test
    void zeroIndustrialHistoryNeverAccuses() {
        // industrial rail never flowed: a "collapse" from nothing means nothing
        var signal = GreshamServiceImpl.evaluate(
                weeks(new long[]{1000, 1000, 1000, 1000, 1100},
                      new long[]{0, 0, 0, 0, 10}),
                4, 0.5, 1.0);
        assertThat(signal.alarming()).isFalse();
    }
}
