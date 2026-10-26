package dev.brp.service;

import dev.brp.dto.TpsReportResponse;

/**
 * TPS reports — yes, THAT kind of TPS. A Minecraft server actually has ticks
 * per second, so the classic office nightmare becomes load-bearing telemetry
 * (docs/03-economy.md section 6 sells the stuff). The cover sheet is
 * mandatory; the memo has been circulated.
 */
public interface TpsReportService {

    TpsReportResponse fileWeeklyReport(boolean withCoverSheet);
}
