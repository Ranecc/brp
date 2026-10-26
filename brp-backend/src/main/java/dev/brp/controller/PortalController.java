package dev.brp.controller;

import dev.brp.dto.ApprovalDtos.Decision;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    // v0 console actions: form posts, PRG; RBAC wires real identity later
    @PostMapping("/approvals/{id}/approve")
    public String approve(@PathVariable String id,
                          @RequestParam String approverUuid,
                          @RequestParam(required = false) String note) {
        approvals.approve(id, new Decision(approverUuid, note));
        return "redirect:/portal/approvals";
    }

    @PostMapping("/approvals/{id}/reject")
    public String reject(@PathVariable String id,
                         @RequestParam String approverUuid,
                         @RequestParam(required = false) String note) {
        approvals.reject(id, new Decision(approverUuid, note));
        return "redirect:/portal/approvals";
    }
}
