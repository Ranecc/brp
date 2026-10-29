package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** The punchline arithmetic (docs/13 §7): coupons paid in ticks actually had. */
class BondMathTest {

    @Test
    void coolServerPaysFullCoupon() {
        assertThat(BondMath.performanceFactor(30, 45, 0.5)).isCloseTo(1.0, offset(1e-9));
        assertThat(BondMath.performanceFactor(45, 45, 0.5)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void hotServerTrimsTheCoupon() {
        // 90 ms average: double the budget, half the coupon
        assertThat(BondMath.performanceFactor(90, 45, 0.5)).isCloseTo(0.5, offset(1e-9));
        // 60 ms: 45/60 = 0.75
        assertThat(BondMath.performanceFactor(60, 45, 0.5)).isCloseTo(0.75, offset(1e-9));
    }

    @Test
    void theFloorNeverZeroesTheCoupon() {
        // catastrophic 10x overload still pays the floor
        assertThat(BondMath.performanceFactor(450, 45, 0.5)).isCloseTo(0.5, offset(1e-9));
    }

    @Test
    void noTicksServedTrimsNothing() {
        assertThat(BondMath.performanceFactor(0, 45, 0.5)).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void couponPerDayIsFloored() {
        assertThat(BondMath.couponPerDay(1, 36_500, 100)).isEqualTo(100);
        assertThat(BondMath.couponPerDay(1, 36_499, 100)).isEqualTo(99);
        assertThat(BondMath.couponPerDay(2, 10_000, 12)).isEqualTo(6);
        assertThat(BondMath.couponPerDay(0, 10_000, 12)).isZero();
    }

    @Test
    void starvingBooksFailAtClose() {
        assertThat(BondMath.failedAtClose(59, 100, 0.6)).isTrue();
        assertThat(BondMath.failedAtClose(60, 100, 0.6)).isFalse();
        assertThat(BondMath.failedAtClose(0, 0, 0.6)).isTrue();
    }
}
