package dev.brp.controller;

import dev.brp.dto.ApprovalDtos.Decision;
import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.EconomyDtos.EconomyDailyResponse;
import dev.brp.dto.TpsReportResponse;
import dev.brp.dto.AnomalyDtos.SetRuleRequest;
import dev.brp.service.AgentMonitorService;
import dev.brp.service.ChunkService;
import dev.brp.service.HotRuleService;
import dev.brp.service.ReconciliationService;
import dev.brp.service.CurrencyService;
import dev.brp.service.QuotaService;
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
    private final CurrencyService currencies;
    private final QuotaService quotas;
    private final ChunkService chunks;
    private final ReconciliationService reconciliation;
    private final HotRuleService hotRules;

    public PortalController(EconomyReportService economy, AgentMonitorService monitor,
                            TpsReportService tpsReports, ApprovalService approvals,
                            CurrencyService currencies, QuotaService quotas,
                            ChunkService chunks, ReconciliationService reconciliation,
                            HotRuleService hotRules) {
        this.economy = economy;
        this.monitor = monitor;
        this.tpsReports = tpsReports;
        this.approvals = approvals;
        this.currencies = currencies;
        this.quotas = quotas;
        this.chunks = chunks;
        this.reconciliation = reconciliation;
        this.hotRules = hotRules;
    }

    @GetMapping("/economy")
    public String economy(Model model) {
        var registered = currencies.list();
        model.addAttribute("today", registered.stream()
                .map(c -> economy.materializeToday(c.code())).toList());
        model.addAttribute("history", economy.history("BRC"));
        model.addAttribute("currencies", registered);
        return "portal/economy";
    }

    @GetMapping("/quotas")
    public String quotasPage(Model model) {
        model.addAttribute("buckets", quotas.all());
        model.addAttribute("incidents", quotas.incidents(null));
        return "portal/quotas";
    }

    @PostMapping("/quotas/{tenantId}/{resource}")
    public String setQuota(@PathVariable String tenantId, @PathVariable String resource,
                           @RequestParam long limit) {
        quotas.setLimit(tenantId, resource, limit);
        return "redirect:/portal/quotas";
    }

    /** Chunk console (docs/09 section 3): every slot, its verdict, the switch. */
    @GetMapping("/chunks")
    public String chunksPage(Model model) {
        model.addAttribute("rows", chunks.adminRows());
        return "portal/chunks";
    }

    @PostMapping("/chunks/{tenantId}/{dimension}/{chunkKey}/power")
    public String chunkPower(@PathVariable String tenantId, @PathVariable String dimension,
                             @PathVariable String chunkKey, @RequestParam boolean enabled) {
        chunks.setPower(tenantId, dimension, chunkKey, new PowerRequest(enabled));
        return "redirect:/portal/chunks";
    }

    @PostMapping("/chunks/{tenantId}/{dimension}/{chunkKey}/grant")
    public String chunkGrant(@PathVariable String tenantId, @PathVariable String dimension,
                             @PathVariable String chunkKey, @RequestParam long minutes) {
        chunks.grantMinutes(tenantId, dimension, chunkKey, new GrantMinutesRequest(minutes));
        return "redirect:/portal/chunks";
    }

    /** The reconciliation desk (docs/10 R-1/R-2): invariants, rules, hits. */
    @GetMapping("/desk")
    public String desk(Model model) {
        model.addAttribute("report", reconciliation.reconcile());
        model.addAttribute("findings", reconciliation.findings());
        model.addAttribute("rules", hotRules.rules());
        model.addAttribute("hits", hotRules.hits());
        return "portal/desk";
    }

    @PostMapping("/desk/rules")
    public String addRule(@org.springframework.web.bind.annotation.RequestParam String name,
                          @org.springframework.web.bind.annotation.RequestParam String eventType,
                          @org.springframework.web.bind.annotation.RequestParam String field,
                          @org.springframework.web.bind.annotation.RequestParam String op,
                          @org.springframework.web.bind.annotation.RequestParam double threshold) {
        hotRules.create(new SetRuleRequest(name, eventType, field, op, threshold, "WARN"));
        return "redirect:/portal/desk";
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
