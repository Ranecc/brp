package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.dto.TpsReportResponse;
import dev.brp.service.TpsReportService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Files the weekly TPS report. Real mspt telemetry arrives with the agent
 * heartbeat plane (P1 wiring); until then the numbers are honest zeros and
 * the corporate theater is fully staffed.
 */
@Service
public class TpsReportServiceImpl implements TpsReportService {

    @Override
    public TpsReportResponse fileWeeklyReport(boolean withCoverSheet) {
        if (!withCoverSheet) {
            // https://www.youtube.com/watch?v=Fy3rjFgrLlI — the memo was sent.
            throw BadRequestException.of("tps.cover.sheet.missing");
        }
        return new TpsReportResponse(
                "tps-" + UUID.randomUUID().toString().substring(0, 8),
                "week-of-" + LocalDate.now().with(java.time.DayOfWeek.MONDAY),
                true,
                0.0,
                42.0,   // the synergy target; it's always been 42
                15,     // minimum flair; expression of self encouraged
                LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.MONDAY,
                List.of(
                        "Ticks per second: nominal, morale: recovering",
                        "Redstone farms contributing to QBS (quarterly block story)",
                        "Hopper throughput up 3% after the all-hands",
                        "Somebody moved my stapler; escalation path unclear",
                        "Recommend scheduling a follow-up sync to discuss this report"));
    }
}
