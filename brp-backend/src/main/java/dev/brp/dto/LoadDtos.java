package dev.brp.dto;

/** Load plane DTOs (docs/13 §2-4): samples, forecast, tariff. */
public final class LoadDtos {

    private LoadDtos() {}

    public record SampleResponse(String bucketMinute, int sampleCount,
                                 double msptAvg, double msptMax,
                                 int playerMax, int entityMax,
                                 int chunkMax, int forcedMax) {}

    public record ForecastHour(String hourStart, double predictedMspt,
                               double utilization, String tariffLabel,
                               int multiplierPct, boolean schedulable) {}

    public record ForecastResponse(boolean coldStart, int profileDays,
                                   double liveMspt, java.util.List<ForecastHour> hours) {}

    public record TariffWindowResponse(Long id, int version, int dayOfWeek,
                                       int startMinute, int endMinute,
                                       int multiplierPct, String label) {}

    public record AddTariffWindowRequest(int dayOfWeek, int startMinute, int endMinute,
                                         int multiplierPct, String label) {}
}
