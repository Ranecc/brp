package dev.brp.controller;

import dev.brp.dto.TpsReportResponse;
import dev.brp.service.TpsReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Where middle management gets its ticks. */
@RestController
@RequestMapping("/api/v1/tps-reports")
public class TpsReportController {

    private final TpsReportService tpsReportService;

    public TpsReportController(TpsReportService tpsReportService) {
        this.tpsReportService = tpsReportService;
    }

    @GetMapping("/weekly")
    public TpsReportResponse weekly(@RequestParam(defaultValue = "true") boolean withCoverSheet) {
        return tpsReportService.fileWeeklyReport(withCoverSheet);
    }
}
