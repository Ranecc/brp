package dev.brp.service.impl;

import dev.brp.entity.TariffWindowEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Table-driven resolution checks (docs/13 section 4): versions, wrap, days. */
class TariffMathTest {

    private static final TariffWindowEntity PEAK_V0 =
            new TariffWindowEntity(0, -1, 19 * 60, 23 * 60, 150, "PEAK");

    @Test
    void defaultIsParWhenNothingMatches() {
        var tuesday = LocalDateTime.of(2026, 10, 27, 12, 0);   // noon, outside peak
        var res = TariffMath.resolve(List.of(PEAK_V0), tuesday);
        assertThat(res.multiplierPct()).isEqualTo(100);
        assertThat(res.label()).isEqualTo("SHOULDER");
    }

    @Test
    void emptyTableIsPar() {
        var res = TariffMath.resolve(List.of(), LocalDateTime.of(2026, 10, 27, 20, 0));
        assertThat(res.multiplierPct()).isEqualTo(100);
    }

    @Test
    void peakWindowMatchesInclusiveStartExclusiveEnd() {
        var start = LocalDateTime.of(2026, 10, 27, 19, 0);
        var before = LocalDateTime.of(2026, 10, 27, 18, 59);
        var lastMinute = LocalDateTime.of(2026, 10, 27, 22, 59);
        var end = LocalDateTime.of(2026, 10, 27, 23, 0);
        assertThat(TariffMath.resolve(List.of(PEAK_V0), start).multiplierPct()).isEqualTo(150);
        assertThat(TariffMath.resolve(List.of(PEAK_V0), lastMinute).multiplierPct()).isEqualTo(150);
        assertThat(TariffMath.resolve(List.of(PEAK_V0), before).multiplierPct()).isEqualTo(100);
        assertThat(TariffMath.resolve(List.of(PEAK_V0), end).multiplierPct()).isEqualTo(100);
    }

    @Test
    void overnightWindowsWrap() {
        var wrap = new TariffWindowEntity(0, -1, 22 * 60, 6 * 60, 60, "VALLEY");
        assertThat(TariffMath.resolve(List.of(wrap), LocalDateTime.of(2026, 10, 27, 23, 0)).multiplierPct())
                .isEqualTo(60);
        assertThat(TariffMath.resolve(List.of(wrap), LocalDateTime.of(2026, 10, 27, 3, 0)).multiplierPct())
                .isEqualTo(60);
        assertThat(TariffMath.resolve(List.of(wrap), LocalDateTime.of(2026, 10, 27, 12, 0)).multiplierPct())
                .isEqualTo(100);
    }

    @Test
    void alwaysPeakCoversTheWholeDay() {
        // the PeakPricingTest seed: 0:00-24:00
        var always = new TariffWindowEntity(0, -1, 0, 24 * 60, 200, "PEAK");
        for (int h = 0; h < 24; h++) {
            assertThat(TariffMath.resolve(List.of(always), LocalDateTime.of(2026, 10, 27, h, 0)).multiplierPct())
                    .as("hour %d", h).isEqualTo(200);
        }
    }

    @Test
    void highestVersionWinsEvenWhenItExcludes() {
        var v1Valley = new TariffWindowEntity(1, -1, 0, 24 * 60, 60, "VALLEY");
        var res = TariffMath.resolve(List.of(PEAK_V0, v1Valley),
                LocalDateTime.of(2026, 10, 27, 20, 0));
        assertThat(res.multiplierPct()).isEqualTo(60);
        assertThat(res.version()).isEqualTo(1);
    }

    @Test
    void dayOfWeekFilterApplies() {
        var weekendsOnly = new TariffWindowEntity(0, 6, 0, 24 * 60, 80, "VALLEY");  // Saturday
        var saturday = LocalDateTime.of(2026, 10, 31, 12, 0);
        var tuesday = LocalDateTime.of(2026, 10, 27, 12, 0);
        assertThat(TariffMath.resolve(List.of(weekendsOnly), saturday).multiplierPct()).isEqualTo(80);
        assertThat(TariffMath.resolve(List.of(weekendsOnly), tuesday).multiplierPct()).isEqualTo(100);
    }
}
