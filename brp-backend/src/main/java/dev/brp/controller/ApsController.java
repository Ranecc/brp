package dev.brp.controller;

import dev.brp.service.ApsService;
import dev.brp.service.ShopFloorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * APS console (docs/09 section 5): the fair-share haircut schedule, plus the
 * P4 shop floor desk (docs/13 section 9) — downtime, OEE, assets, costing.
 */
@RestController
@RequestMapping("/api/v1/aps")
public class ApsController {

    private final ApsService aps;
    private final ShopFloorService shopFloor;

    public ApsController(ApsService aps, ShopFloorService shopFloor) {
        this.aps = aps;
        this.shopFloor = shopFloor;
    }

    // --- P4 shop floor (docs/13 §9) ---

    @PostMapping("/shopfloor/{tenantId}/downtime")
    public dev.brp.entity.DowntimeEventEntity recordDowntime(
            @PathVariable String tenantId,
            @RequestBody dev.brp.dto.LoadDtos.DowntimeRequest req) {
        return shopFloor.recordDowntime(tenantId, req.machine(), req.reason(), req.idemKey(),
                java.time.LocalDateTime.parse(req.since()), java.time.LocalDateTime.parse(req.until()));
    }

    @GetMapping("/shopfloor/{tenantId}/downtime")
    public List<dev.brp.entity.DowntimeEventEntity> downtime(@PathVariable String tenantId) {
        return shopFloor.downtime(tenantId);
    }

    @GetMapping("/shopfloor/{tenantId}/oee")
    public ShopFloorService.OeeReport oee(@PathVariable String tenantId,
            @RequestParam(required = false) String weekOf) {
        LocalDate week = weekOf == null ? LocalDate.now() : LocalDate.parse(weekOf);
        return shopFloor.oee(tenantId, week);
    }

    @PostMapping("/shopfloor/{tenantId}/assets")
    public dev.brp.entity.AssetEntity registerAsset(
            @PathVariable String tenantId,
            @RequestBody dev.brp.dto.LoadDtos.RegisterAssetRequest req) {
        return shopFloor.registerAsset(tenantId, req.kind(), req.label(),
                req.locationChunk(), req.costBasisCents());
    }

    @GetMapping("/shopfloor/{tenantId}/assets")
    public List<dev.brp.entity.AssetEntity> assets(@PathVariable String tenantId) {
        return shopFloor.assets(tenantId);
    }

    @PostMapping("/shopfloor/{tenantId}/assets/{assetId}/retire")
    public dev.brp.entity.AssetEntity retireAsset(@PathVariable String tenantId,
            @PathVariable long assetId) {
        return shopFloor.retire(tenantId, assetId);
    }

    @PostMapping("/shopfloor/{tenantId}/workorders/{ref}/stamp")
    public dev.brp.entity.WorkorderCostEntity stampCost(@PathVariable String tenantId,
            @PathVariable String ref,
            @RequestBody dev.brp.dto.LoadDtos.StampCostRequest req) {
        return shopFloor.stampCompletionCost(tenantId, ref,
                java.time.LocalDateTime.parse(req.since()), java.time.LocalDateTime.parse(req.until()),
                req.materialCostCents());
    }

    @GetMapping("/allocations")
    public Map<String, Double> allocations() {
        return aps.allocations();
    }

    @GetMapping("/allocations/{tenantId}")
    public Map<String, Double> factor(@PathVariable String tenantId) {
        return Map.of("factor", aps.factorFor(tenantId));
    }
}
