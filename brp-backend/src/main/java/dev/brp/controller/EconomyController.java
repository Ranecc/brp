package dev.brp.controller;

import dev.brp.dto.EconomyDtos.EconomyDailyResponse;
import dev.brp.service.EconomyReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The money printer status board. */
@RestController
@RequestMapping("/api/v1/economy")
public class EconomyController {

    private final EconomyReportService economy;

    public EconomyController(EconomyReportService economy) {
        this.economy = economy;
    }

    @GetMapping("/daily")
    public EconomyDailyResponse daily(@RequestParam(defaultValue = "BRC") String currency) {
        return economy.materializeToday(currency);
    }

    @GetMapping("/history")
    public List<EconomyDailyResponse> history(@RequestParam(defaultValue = "BRC") String currency) {
        return economy.history(currency);
    }
}
