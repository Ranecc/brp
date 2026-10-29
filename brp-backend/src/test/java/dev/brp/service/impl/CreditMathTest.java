package dev.brp.service.impl;

import dev.brp.entity.CreditLineEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The status machine and the interest math (docs/13 §6), no database: the
 * entity is a plain object until JPA gets hold of it, so the desk arithmetic
 * is table-driven here.
 */
class CreditMathTest {

    private static CreditLineEntity line(long limit) {
        return new CreditLineEntity("t-1", limit, 12);
    }

    @Test
    void utilizationIsDrawnOverLimit() {
        var l = line(1_000);
        l.draw(250);
        assertThat(l.utilization()).isCloseTo(0.25, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void zeroLimitMeansMaximumCautionNotDivisionByZero() {
        assertThat(line(0).utilization()).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void statusWalksGoodWatchRestricted() {
        var l = line(1_000);
        assertThat(l.transitionTo(0.8, 1.0)).isNull();          // 0/1000: stays GOOD

        l.draw(799);
        assertThat(l.transitionTo(0.8, 1.0)).isNull();          // 79.9%: still GOOD
        l.draw(1);
        assertThat(l.transitionTo(0.8, 1.0)).isEqualTo("WATCH"); // 80%: watched

        l.draw(200);
        assertThat(l.transitionTo(0.8, 1.0)).isEqualTo("RESTRICTED"); // 100%: teeth
    }

    @Test
    void restrictedHoldsUntilBelowTheRestrictLine() {
        var l = line(1_000);
        l.draw(1_000);
        l.transitionTo(0.8, 1.0);
        assertThat(l.getStatus()).isEqualTo("RESTRICTED");
        l.repay(1);                                             // 99.9%: below restrict...
        assertThat(l.transitionTo(0.8, 1.0)).isEqualTo("WATCH"); // ...but only one step down
        l.repay(200);                                           // 79.9%: below watch...
        assertThat(l.transitionTo(0.8, 1.0)).isEqualTo("GOOD");  // ...one more step
    }

    @Test
    void defaultedIsTerminalUntilReinstated() {
        var l = line(1_000);
        l.draw(1_000);
        l.transitionTo(0.8, 1.0);
        l.noteDefaulted();
        l.repay(1_000);
        assertThat(l.transitionTo(0.8, 1.0)).isNull();          // no silent resurrection
        l.reinstate();
        assertThat(l.getStatus()).isEqualTo("WATCH");
    }

    @Test
    void repaymentNeverExceedsTheDrawn() {
        var l = line(1_000);
        l.draw(300);
        assertThat(l.repay(5_000)).isEqualTo(300);
        assertThat(l.getDrawnCents()).isZero();
    }

    @Test
    void interestIsDailySimpleFloored() {
        // 365-day count, floor rounding favors the debtor
        assertThat(CreditServiceImpl.interestFor(36_500, 100)).isEqualTo(100);
        assertThat(CreditServiceImpl.interestFor(36_499, 100)).isEqualTo(99);
        assertThat(CreditServiceImpl.interestFor(50_000, 12)).isEqualTo(16);
        assertThat(CreditServiceImpl.interestFor(0, 12)).isZero();
        assertThat(CreditServiceImpl.interestFor(50_000, 0)).isZero();
    }

    @Test
    void absurdDrawnOverflowsIntoAnErrorNotANegative() {
        assertThatThrownBy(() -> CreditServiceImpl.interestFor(Long.MAX_VALUE, 12))
                .isInstanceOf(dev.brp.common.BadRequestException.class);
    }
}
