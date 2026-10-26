package dev.brp.controller;

import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;
import dev.brp.dto.EconomyDtos.EconomyDailyResponse;
import dev.brp.dto.TpsReportResponse;
import dev.brp.service.AgentMonitorService;
import dev.brp.service.ApprovalService;
import dev.brp.service.EconomyReportService;
import dev.brp.service.TpsReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * The BRP web console: server-rendered MVC in the classic admin chrome,
 * Minecraft accents where they count (docs/01 section 2, portal P1).
 * v0 assumes the trusted internal network — auth lands with RBAC.
 */
@Controller
@RequestMapping("/portal")
public class PortalController {

    private final EconomyReportService economy;
    private final AgentMonitorService monitor;
    private final TpsReportService tpsReports;
    private final ApprovalService approvals;

    public PortalController(EconomyReportService economy, AgentMonitorService monitor,
                            TpsReportService tpsReports, ApprovalService approvals) {
        this.economy = economy;
        this.monitor = monitor;
        this.tpsReports = tpsReports;
        this.approvals = approvals;
    }

    @GetMapping
    public String dashboard(Model model) {
        EconomyDailyResponse daily = economy.materializeToday("BRC");
        TpsReportResponse tps = tpsReports.fileWeeklyReport(true);
        List<WithdrawalRequestResponse> pending = approvals.latestPending();

        model.addAttribute("daily", daily);
        model.addAttribute("tps", tps);
        model.addAttribute("mspt", monitor.averageMspt());
        model.addAttribute("pending", pending);
        model.addAttribute("pendingCount", pending.size());
        return "portal/dashboard";
    }

    @GetMapping("/approvals")
    public String approvals(Model model) {
        model.addAttribute("pending", approvals.latestPending());
        return "portal/approvals";
    }
}
