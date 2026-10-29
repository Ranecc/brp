package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Peg-band admission and the fee (docs/13 §8). */
class MarketMathTest {

    @Test
    void bandCheckMapsThePegIntoCents() {
        // peg quota leg 10.0 BRC/unit, band [9.5, 10.5] -> cents [950, 1050]
        assertThat(MarketMath.insidePegBand(1_000, 9.5, 10.5)).isTrue();
        assertThat(MarketMath.insidePegBand(950, 9.5, 10.5)).isTrue();
        assertThat(MarketMath.insidePegBand(1_050, 9.5, 10.5)).isTrue();
        assertThat(MarketMath.insidePegBand(949, 9.5, 10.5)).isFalse();
        assertThat(MarketMath.insidePegBand(1_051, 9.5, 10.5)).isFalse();
    }

    @Test
    void feeIsFloorRoundedBips() {
        assertThat(MarketMath.feeOn(10_000, 50)).isEqualTo(50);   // 0.5% of 100 BRC
        assertThat(MarketMath.feeOn(999, 50)).isEqualTo(4);       // floor
        assertThat(MarketMath.feeOn(10_000, 0)).isZero();
        assertThat(MarketMath.feeOn(0, 50)).isZero();
    }
}
