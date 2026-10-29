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

    public record FileDeferredJobRequest(String idemKey, String resource, long quantity,
                                         long estMinutes, String earliest, String deadline) {}

    public record DeferredJobResponse(Long id, String tenantId, String resource, long quantity,
                                      long estMinutes, String earliest, String deadline,
                                      String status, String awardedStart, long awardedMinutes,
                                      int windowSeq, int shedCount) {}

    public record CreditStatusResponse(String tenantId, long limitCents, long drawnCents,
                                       long availableCents, double utilizationPct,
                                       String status, String statusSince,
                                       int annualRatePct, boolean limitPinned) {}

    public record RepayRequest(long amountCents) {}

    public record BondIssueRequest(String name, long faceCents, int couponPctAnnual,
                                   int termDays, long totalUnits, double thresholdMspt) {}

    public record SubscribeRequest(long units) {}

    public record MarketOrderResponse(Long id, String tenantId, String side, long qty,
                                      long qtyRemaining, long priceCents, String status) {}

    public record PlaceOrderRequest(String idemKey, String side, long qty, long priceCents) {}

    public record DowntimeRequest(String machine, String reason, String idemKey,
                                  String since, String until) {}

    public record RegisterAssetRequest(String kind, String label, String locationChunk,
                                       long costBasisCents) {}

    public record StampCostRequest(String since, String until, long materialCostCents) {}

    public record SetLimitRequest(long limitCents) {}
}
