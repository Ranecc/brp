package dev.brp.service;

import dev.brp.dto.EconomyDtos.EconomyDailyResponse;

import java.util.List;

/** Economy observability contract (impl: {@link dev.brp.service.impl.EconomyReportServiceImpl}). */
public interface EconomyReportService {

    /** Recompute today's metrics for a currency and return them (idempotent upsert). */
    EconomyDailyResponse materializeToday(String currency);

    List<EconomyDailyResponse> history(String currency);
}
