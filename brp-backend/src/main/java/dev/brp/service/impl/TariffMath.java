package dev.brp.service.impl;

import dev.brp.entity.TariffWindowEntity;
import dev.brp.service.TariffService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pure tariff resolution (docs/13 §4): highest version wins, first matching
 * row inside it wins, default is par. Static so the table-driven tests can
 * hammer window boundaries without a database.
 */
final class TariffMath {

    private TariffMath() {}

    static final TariffService.Resolution DEFAULT =
            new TariffService.Resolution(100, TariffServiceImpl.DEFAULT_LABEL, -1);

    /** Highest-version row matching the datetime; DEFAULT when nothing does. */
    static TariffService.Resolution resolve(List<TariffWindowEntity> rows, LocalDateTime when) {
        int latest = rows.stream().mapToInt(TariffWindowEntity::getVersion).max().orElse(-1);
        if (latest < 0) {
            return DEFAULT;
        }
        int isoDay = when.getDayOfWeek().getValue();
        int minuteOfDay = when.getHour() * 60 + when.getMinute();
        return rows.stream()
                .filter(w -> w.getVersion() == latest)
                .filter(w -> w.getDayOfWeek() == TariffWindowEntity.ALL_DAYS
                        || w.getDayOfWeek() == isoDay)
                .filter(w -> inWindow(w.getStartMinute(), w.getEndMinute(), minuteOfDay))
                .findFirst()
                .map(w -> new TariffService.Resolution(
                        w.getMultiplierPct(), w.getLabel(), w.getVersion()))
                .orElse(DEFAULT);
    }

    /** [start, end); start > end means an overnight window wrapping midnight. */
    static boolean inWindow(int startMinute, int endMinute, int minuteOfDay) {
        if (startMinute <= endMinute) {
            return minuteOfDay >= startMinute && minuteOfDay < endMinute;
        }
        return minuteOfDay >= startMinute || minuteOfDay < endMinute;
    }
}
