package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** OEE arithmetic (docs/13 §9): A measured, P measured, Q disclosed. */
class OeeMathTest {

    @Test
    void availabilityIsOneMinusDowntimeOverPeriod() {
        assertThat(OeeMath.availability(0, 10_080)).isCloseTo(1.0, offset(1e-9));
        assertThat(OeeMath.availability(5_040, 10_080)).isCloseTo(0.5, offset(1e-9));
        assertThat(OeeMath.availability(10_080, 10_080)).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void overbookingTheDowntimeFloorsAtZeroNotNegativity() {
        assertThat(OeeMath.availability(20_000, 10_080)).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void performanceAgainstBaseline() {
        assertThat(OeeMath.performance(500, 1_000)).isCloseTo(0.5, offset(1e-9));
        assertThat(OeeMath.performance(2_000, 1_000)).isCloseTo(1.0, offset(1e-9));   // capped, no bragging
    }

    @Test
    void coldStartPerformanceIsOneAndDisclosed() {
        assertThat(OeeMath.performance(0, 0)).isCloseTo(1.0, offset(1e-9));
    }
}
