package dev.brp.controller;

import dev.brp.dto.MonetaryDtos.RegimeResponse;
import dev.brp.dto.MonetaryDtos.SnapshotResponse;
import dev.brp.dto.MonetaryDtos.WealthAssessmentRow;
import dev.brp.entity.PegStateEntity;
import dev.brp.entity.PolicyRequestEntity;
import dev.brp.saga.SagaEngine;
import dev.brp.service.GreshamService;
import dev.brp.service.MintService;
import dev.brp.service.PegService;
import dev.brp.service.RegimeService;
import dev.brp.service.SnapshotService;
import dev.brp.service.WealthLevyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The monetary console (docs/12 §14): regime, snapshot, the levy desks. */
@RestController
@RequestMapping("/api/v1/monetary")
public class MonetaryController {

    private final RegimeService regime;
    private final SnapshotService snapshots;
    private final WealthLevyService wealthLevy;
    private final PegService peg;
    private final MintService mint;
    private final GreshamService gresham;
    private final dev.brp.repository.PolicyRequestRepository policyRequests;
    private final dev.brp.service.WashService wash;
    private final dev.brp.service.MarketPriceService marketPrices;
    private final dev.brp.service.impl.VaultDriftServiceImpl vaultDrift;

    public MonetaryController(RegimeService regime, SnapshotService snapshots,
                              WealthLevyService wealthLevy, PegService peg,
                              MintService mint, GreshamService gresham,
                              dev.brp.repository.PolicyRequestRepository policyRequests,
                              dev.brp.service.WashService wash,
                              dev.brp.service.MarketPriceService marketPrices,
                              dev.brp.service.impl.VaultDriftServiceImpl vaultDrift) {
        this.regime = regime;
        this.snapshots = snapshots;
        this.wealthLevy = wealthLevy;
        this.peg = peg;
        this.mint = mint;
        this.gresham = gresham;
        this.policyRequests = policyRequests;
        this.wash = wash;
        this.marketPrices = marketPrices;
        this.vaultDrift = vaultDrift;
    }

    @GetMapping("/regime")
    public RegimeResponse regime() {
        return regime.regime();
    }

    @PostMapping("/snapshot/run")
    public SnapshotResponse capture() {
        return snapshots.capture();
    }

    @GetMapping("/snapshot/latest")
    public SnapshotResponse latest() {
        return snapshots.latest();
    }

    @PostMapping("/wealth/{weekOf}/assess")
    public List<WealthAssessmentRow> assessWealth(@PathVariable String weekOf) {
        return wealthLevy.assess(weekOf);
    }

    @GetMapping("/wealth/{weekOf}")
    public List<WealthAssessmentRow> wealth(@PathVariable String weekOf) {
        return wealthLevy.assessments(weekOf);
    }

    // --- docs/12 sections 8-10: vault, peg, gresham ---

    public record RegisterVaultRequest(String tenantId, String containerRef, String itemId) {}
    public record MintRequest(String tenantId, String containerRef, String itemId,
                              long qty, long cents, String correlation) {}
    public record ObserveRequest(double observedRate) {}
    public record DecideRequest(boolean approved, String decidedBy) {}

    @PostMapping("/vault")
    public void registerVault(@org.springframework.web.bind.annotation.RequestBody RegisterVaultRequest req) {
        mint.registerVault(req.tenantId(), req.containerRef(), req.itemId());
    }

    @PostMapping("/mint")
    public SagaEngine.Outcome mint(@org.springframework.web.bind.annotation.RequestBody MintRequest req) {
        return mint.mint(req.tenantId(), req.containerRef(), req.itemId(),
                req.qty(), req.cents(), req.correlation());
    }

    @GetMapping("/peg")
    public List<PegStateEntity> peg() {
        return peg.legs();
    }

    @PostMapping("/peg/{leg}/observe")
    public void observe(@PathVariable String leg,
                        @org.springframework.web.bind.annotation.RequestBody ObserveRequest req) {
        peg.observe(leg, req.observedRate());
    }

    @GetMapping("/policy-requests")
    public List<PolicyRequestEntity> pending() {
        return policyRequests.findByStatusOrderByIdAsc(PolicyRequestEntity.PENDING);
    }

    @org.springframework.web.bind.annotation.PostMapping("/policy-requests/{id}/decide")
    public void decide(@PathVariable long id,
                       @org.springframework.web.bind.annotation.RequestBody DecideRequest req) {
        peg.decide(id, req.approved(), req.decidedBy());
    }

    @org.springframework.web.bind.annotation.PostMapping("/gresham/scan")
    public java.util.Map<String, Double> gresham() {
        return gresham.scan();
    }

    // --- docs/12 sections 11-12: wash cycles, prices, CPI, vault drift ---

    public record PriceRequest(String item, long priceCents, String currency,
                               long qty, String venue) {}

    @org.springframework.web.bind.annotation.PostMapping("/wash/scan")
    public dev.brp.service.WashService.ScanReport washScan() {
        return wash.scan();
    }

    @GetMapping("/wash/findings")
    public java.util.List<dev.brp.dto.MonetaryDtos.WashFindingResponse> washFindings() {
        return wash.findings();
    }

    @org.springframework.web.bind.annotation.PostMapping("/prices")
    public void recordPrice(@org.springframework.web.bind.annotation.RequestBody PriceRequest req) {
        marketPrices.record(req.item(), req.priceCents(), req.currency(),
                req.qty(), req.venue(), "manual");
    }

    @org.springframework.web.bind.annotation.PostMapping("/cpi/run")
    public java.util.Map<String, Double> cpiRun() {
        return java.util.Map.of("index", marketPrices.materializeToday());
    }

    @GetMapping("/cpi")
    public java.util.List<dev.brp.entity.CpiDailyEntity> cpi() {
        return marketPrices.history();
    }

    @org.springframework.web.bind.annotation.PostMapping("/vault/drift-check")
    public java.util.List<String> driftCheck() {
        return vaultDrift.driftCheck();
    }
}
