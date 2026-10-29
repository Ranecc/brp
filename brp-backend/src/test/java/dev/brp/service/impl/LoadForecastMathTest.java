package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/** EWMA and the damped predictor (docs/13 section 2), no database involved. */
class LoadForecastMathTest {

    @Test
    void ewmaOfEmptyHistoryIsZeroNotNaN() {
        assertThat(LoadForecastMath.ewma(List.of(), 0.3)).isEqualTo(0.0);
    }

    @Test
    void singleSampleIsPassedThrough() {
        assertThat(LoadForecastMath.ewma(List.of(42.0), 0.3)).isCloseTo(42.0, offset(1e-9));
    }

    @Test
    void recentSamplesDominate() {
        // alpha 0.3: the newest value should pull the estimate most of the way
        double live = LoadForecastMath.ewma(List.of(10.0, 10.0, 10.0, 10.0, 40.0), 0.3);
        assertThat(live).isGreaterThan(20.0);
        assertThat(live).isLessThan(40.0);
    }

    @Test
    void ewmaIsNormalizedSoShortWindowsStayHonest() {
        // a single spike must not be diluted by "invisible history"
        double live = LoadForecastMath.ewma(List.of(80.0), 0.3);
        assertThat(live).isCloseTo(80.0, offset(1e-9));
    }

    @Test
    void predictorDampsTheDeviationByHalf() {
        // profile says 20, live says 40 -> predict 30 (damp 0.5)
        assertThat(LoadForecastMath.predict(20.0, 40.0, 20.0, 0.5)).isCloseTo(30.0, offset(1e-9));
    }

    @Test
    void predictorUsesTheTargetMinutesOwnProfile() {
        // profile(m)=35, profile(now)=20, live=40 -> 35 + 0.5*(40-20) = 45
        assertThat(LoadForecastMath.predict(35.0, 40.0, 20.0, 0.5)).isCloseTo(45.0, offset(1e-9));
    }

    @Test
    void utilizationIsAgainstTheFiftyMillisecondBudget() {
        assertThat(LoadForecastMath.utilization(50.0)).isCloseTo(1.0, offset(1e-9));
        assertThat(LoadForecastMath.utilization(25.0)).isCloseTo(0.5, offset(1e-9));
        assertThat(LoadForecastMath.utilization(100.0)).isCloseTo(2.0, offset(1e-9));  // overload is visible
    }
}
