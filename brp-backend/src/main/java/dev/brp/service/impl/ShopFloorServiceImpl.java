package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.entity.AssetEntity;
import dev.brp.entity.DowntimeEventEntity;
import dev.brp.entity.LevyMinuteEntity;
import dev.brp.entity.WorkorderCostEntity;
import dev.brp.repository.AssetRepository;
import dev.brp.repository.DowntimeEventRepository;
import dev.brp.repository.LevyMinuteRepository;
import dev.brp.repository.WorkorderCostRepository;
import dev.brp.service.ShopFloorService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShopFloorServiceImpl implements ShopFloorService {

    private static final long WEEK_MINUTES = 10_080L;
    private static final int BASELINE_WEEKS = 4;

    private final DowntimeEventRepository downtime;
    private final AssetRepository assets;
    private final WorkorderCostRepository costs;
    private final LevyMinuteRepository levyMinutes;

    public ShopFloorServiceImpl(DowntimeEventRepository downtime,
                                AssetRepository assets,
                                WorkorderCostRepository costs,
                                LevyMinuteRepository levyMinutes) {
        this.downtime = downtime;
        this.assets = assets;
        this.costs = costs;
        this.levyMinutes = levyMinutes;
    }

    @Override
    public DowntimeEventEntity recordDowntime(String tenantId, String machine, String reason,
                                              String idemKey, LocalDateTime since, LocalDateTime until) {
        if (!until.isAfter(since)) {
            throw new BadRequestException("downtime.window.invalid");
        }
        try {
            return downtime.saveAndFlush(new DowntimeEventEntity(
                    tenantId, machine, reason, idemKey, since, until));
        } catch (DataIntegrityViolationException replay) {
            // at-least-once telemetry: the stoppage already filed itself
            return downtime.findByTenantIdOrderBySinceDesc(tenantId).stream()
                    .filter(e -> e.getIdemKey().equals(idemKey))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("downtime.state.lost"));
        }
    }

    @Override
    public List<DowntimeEventEntity> downtime(String tenantId) {
        return downtime.findByTenantIdOrderBySinceDesc(tenantId);
    }

    @Override
    public OeeReport oee(String tenantId, LocalDate weekOf) {
        LocalDate week = weekOf.with(DayOfWeek.MONDAY);
        LocalDateTime from = week.atStartOfDay();
        LocalDateTime to = week.plusWeeks(1).atStartOfDay();
        long down = downtime.findByTenantIdAndSinceGreaterThanEqualAndSinceLessThan(tenantId, from, to)
                .stream().mapToLong(DowntimeEventEntity::getMinutes).sum();
        double availability = OeeMath.availability(down, WEEK_MINUTES);

        long actual = levyMinutes.findByMinuteBucketBetween(from, to).stream()
                .filter(m -> m.getTenantId().equals(tenantId))
                .mapToLong(m -> (long) m.getUnits()).sum();
        long baseline = 0;
        for (int i = 1; i <= BASELINE_WEEKS; i++) {
            LocalDateTime bFrom = from.minusWeeks(i);
            LocalDateTime bTo = to.minusWeeks(i);
            baseline += levyMinutes.findByMinuteBucketBetween(bFrom, bTo).stream()
                    .filter(m -> m.getTenantId().equals(tenantId))
                    .mapToLong(m -> (long) m.getUnits()).sum();
        }
        boolean cold = baseline <= 0;
        double performance = OeeMath.performance(actual, baseline / BASELINE_WEEKS);
        double quality = 1.0;   // DISCLOSED: no rework data exists yet (docs/13 §9)
        double oee = availability * performance * quality;
        return new OeeReport(tenantId, week, down,
                round(availability), round(performance), quality, round(oee), cold);
    }

    @Override
    public AssetEntity registerAsset(String tenantId, String kind, String label,
                                     String locationChunk, long costBasisCents) {
        if (kind == null || kind.isBlank() || label == null || label.isBlank() || costBasisCents < 0) {
            throw new BadRequestException("asset.invalid");
        }
        return assets.save(new AssetEntity(tenantId, kind, label, locationChunk, costBasisCents));
    }

    @Override
    public List<AssetEntity> assets(String tenantId) {
        return assets.findByTenantIdOrderByIdAsc(tenantId);
    }

    @Override
    public AssetEntity retire(String tenantId, long assetId) {
        AssetEntity asset = assets.findById(assetId).orElseThrow(() -> new BadRequestException("asset.unknown"));
        if (!asset.getTenantId().equals(tenantId)) {
            throw new BadRequestException("asset.unknown");
        }
        asset.retire();
        return assets.save(asset);
    }

    @Override
    public WorkorderCostEntity stampCompletionCost(String tenantId, String workorderRef,
                                                   LocalDateTime since, LocalDateTime until,
                                                   long materialCostCents) {
        if (materialCostCents < 0) {
            throw new BadRequestException("workorder.cost.invalid");
        }
        long machineCost = levyMinutes.findByMinuteBucketBetween(since, until).stream()
                .filter(m -> m.getTenantId().equals(tenantId))
                .mapToLong(LevyMinuteEntity::getAmountCents).sum();
        try {
            return costs.saveAndFlush(new WorkorderCostEntity(
                    tenantId, workorderRef, materialCostCents, machineCost));
        } catch (DataIntegrityViolationException restamp) {
            throw new BadRequestException("workorder.already.stamped");
        }
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
