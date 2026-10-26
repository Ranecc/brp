package dev.brp.dto;

import java.time.LocalDate;

/** Economy observability DTOs — M, V, emission; the money printer status. */
public final class EconomyDtos {

    private EconomyDtos() {}

    public record EconomyDailyResponse(
            LocalDate date, String currency,
            long moneyMassCents, long netEmissionCents,
            long tradedVolumeCents, double velocity) {}
}
