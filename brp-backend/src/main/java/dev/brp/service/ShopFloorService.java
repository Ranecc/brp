package dev.brp.service;

import dev.brp.entity.AssetEntity;
import dev.brp.entity.DowntimeEventEntity;
import dev.brp.entity.WorkorderCostEntity;

import java.time.LocalDate;
import java.util.List;

/**
 * The shop floor desk (docs/13 §9): the downtime ledger, weekly OEE, the
 * asset registry, and work-order completion costing. Data-honest by charter:
 * A and P are measured, Q is disclosed as 1.0.
 */
public interface ShopFloorService {

    /** Record a stoppage (idempotent by key — telemetry replays happen). */
    DowntimeEventEntity recordDowntime(String tenantId, String machine, String reason,
                                       String idemKey, java.time.LocalDateTime since,
                                       java.time.LocalDateTime until);

    List<DowntimeEventEntity> downtime(String tenantId);

    /** Weekly OEE for the week containing {@param weekOf} (Monday-aligned). */
    OeeReport oee(String tenantId, LocalDate weekOf);

    record OeeReport(String tenantId, LocalDate weekOf, long downtimeMinutes,
                     double availability, double performance, double quality,
                     double oee, boolean performanceColdStart) {}

    AssetEntity registerAsset(String tenantId, String kind, String label,
                              String locationChunk, long costBasisCents);

    List<AssetEntity> assets(String tenantId);

    AssetEntity retire(String tenantId, long assetId);

    /** Completion cost: materials as reported + levy-priced machine minutes in the window. */
    WorkorderCostEntity stampCompletionCost(String tenantId, String workorderRef,
                                            java.time.LocalDateTime since,
                                            java.time.LocalDateTime until,
                                            long materialCostCents);
}
