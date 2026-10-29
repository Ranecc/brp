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
import dev.brp.service.GreshamService;
import dev.brp.service.HotRuleService;
import dev.brp.service.MarketPriceService;
import dev.brp.service.PegService;
import dev.brp.service.ReconciliationService;
import dev.brp.service.RegimeService;
import dev.brp.service.SnapshotService;
import dev.brp.service.WashService;
import dev.brp.service.WealthLevyService;
import dev.brp.repository.PolicyRequestRepository;
import dev.brp.entity.PolicyRequestEntity;
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
    private final RegimeService regime;
    private final SnapshotService snapshots;
    private final WealthLevyService wealthLevy;
    private final PegService peg;
    private final GreshamService gresham;
    private final MarketPriceService marketPrices;
    private final WashService wash;
    private final PolicyRequestRepository policyRequests;
    private final dev.brp.service.LoadSampleService loadSamples;
    private final dev.brp.service.TariffService tariff;
    private final dev.brp.service.DemandResponseService demandResponse;
    private final dev.brp.service.CreditService credit;
    private final dev.brp.service.BondService bonds;
    private final dev.brp.service.QuotaMarketService market;
    private final dev.brp.repository.DeferredJobRepository deferredJobs;


    public PortalController(EconomyReportService economy, AgentMonitorService monitor,
                            TpsReportService tpsReports, ApprovalService approvals,
                            CurrencyService currencies, QuotaService quotas,
                            ChunkService chunks, ReconciliationService reconciliation,
                            HotRuleService hotRules, RegimeService regime,
                            SnapshotService snapshots, WealthLevyService wealthLevy,
                            PegService peg, GreshamService gresham,
                            MarketPriceService marketPrices, WashService wash,
                            PolicyRequestRepository policyRequests,
                            dev.brp.service.LoadSampleService loadSamples,
                            dev.brp.service.TariffService tariff,
                            dev.brp.service.DemandResponseService demandResponse,
                            dev.brp.service.CreditService credit,
                            dev.brp.service.BondService bonds,
                            dev.brp.service.QuotaMarketService market,
                            dev.brp.repository.DeferredJobRepository deferredJobs) {
        this.economy = economy;
        this.monitor = monitor;
        this.tpsReports = tpsReports;
        this.approvals = approvals;
        this.currencies = currencies;
        this.quotas = quotas;
        this.chunks = chunks;
        this.reconciliation = reconciliation;
        this.hotRules = hotRules;
        this.regime = regime;
        this.snapshots = snapshots;
        this.wealthLevy = wealthLevy;
        this.peg = peg;
        this.gresham = gresham;
        this.marketPrices = marketPrices;
        this.wash = wash;
        this.policyRequests = policyRequests;
        this.loadSamples = loadSamples;
        this.tariff = tariff;
        this.demandResponse = demandResponse;
        this.credit = credit;
        this.bonds = bonds;
        this.market = market;
        this.deferredJobs = deferredJobs;
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

    /** The load desk (docs/13 §13): forecast curve, tariff, DR, credit, bonds, the book. */
    @GetMapping("/load")
    public String load(Model model) {
        var forecast = loadSamples.forecast(24);
        model.addAttribute("forecast", forecast);
        model.addAttribute("curve", forecastPoints(forecast));
        model.addAttribute("tariff", tariff.windows());
        model.addAttribute("jobs", deferredJobs.findAll());
        model.addAttribute("bonds", bonds.series());
        model.addAttribute("book", market.book());
        model.addAttribute("recentTrades", market.trades());
        return "portal/load";
    }

    /** Inline-SVG polyline points for the forecast curve; same chrome as CPI. */
    static String forecastPoints(dev.brp.dto.LoadDtos.ForecastResponse forecast) {
        if (forecast.hours().isEmpty()) {
            return "";
        }
        double maxMspt = forecast.hours().stream()
                .mapToDouble(dev.brp.dto.LoadDtos.ForecastHour::predictedMspt)
                .max().orElse(1.0);
        double scale = 100.0 / Math.max(maxMspt, 1.0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < forecast.hours().size(); i++) {
            double y = 100.0 - forecast.hours().get(i).predictedMspt() * scale;
            sb.append(i * 10).append(',').append(String.format("%.1f", y));
            if (i < forecast.hours().size() - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /** The monetary console (docs/12 §14): regime, snapshot, rails, CPI, peg, votes. */
    @GetMapping("/monetary")
    public String monetary(Model model) {
        model.addAttribute("regime", regime.regime());
        model.addAttribute("snapshot", snapshots.latest());
        model.addAttribute("giniOn", 0.8);
        model.addAttribute("giniOff", 0.7);
        model.addAttribute("rails", gresham.scan());
        model.addAttribute("cpiHistory", marketPrices.history());
        model.addAttribute("cpiPoints", cpiPoints(marketPrices.history()));
        model.addAttribute("cpiToday", tryMaterializeCpi());
        model.addAttribute("legs", peg.legs());
        model.addAttribute("requests", policyRequests.findByStatusOrderByIdAsc(PolicyRequestEntity.PENDING));
        model.addAttribute("washFindings", wash.findings());
        return "portal/monetary";
    }

    private String tryMaterializeCpi() {
        try {
            return String.valueOf(marketPrices.materializeToday());
        } catch (RuntimeException notEnoughPrices) {
            return "—";
        }
    }

    /** Inline-SVG polyline points for the CPI line; classic chrome, no JS. */
    static String cpiPoints(List<dev.brp.entity.CpiDailyEntity> history) {
        List<dev.brp.entity.CpiDailyEntity> chrono = new java.util.ArrayList<>(history);
        chrono.sort(java.util.Comparator.comparing(dev.brp.entity.CpiDailyEntity::getDay));
        if (chrono.size() < 2) {
            return "";
        }
        double min = chrono.stream().mapToDouble(dev.brp.entity.CpiDailyEntity::getIndexValue).min().orElse(0);
        double max = chrono.stream().mapToDouble(dev.brp.entity.CpiDailyEntity::getIndexValue).max().orElse(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chrono.size(); i++) {
            double v = chrono.get(i).getIndexValue();
            double y = max == min ? 40 : 80 - ((v - min) / (max - min)) * 70;
            double x = i * 200.0 / (chrono.size() - 1);
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%.1f,%.1f", x, y));
        }
        return sb.toString();
    }

    @PostMapping("/monetary/policy/{id}/decide")
    public String decidePolicy(@PathVariable long id, @RequestParam boolean approved) {
        peg.decide(id, approved, "portal");
        return "redirect:/portal/monetary";
    }

    @PostMapping("/monetary/cpi/run")
    public String runCpi() {
        try {
            marketPrices.materializeToday();
        } catch (RuntimeException notEnoughDataYet) {
            // the desk opens when the data shows up
        }
        return "redirect:/portal/monetary";
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
