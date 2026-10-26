package dev.brp.service;

import dev.brp.dto.QuotaDtos.ChunkIncidentResponse;
import dev.brp.dto.QuotaDtos.PlacementVerdict;
import dev.brp.dto.QuotaDtos.QuotaBucketResponse;

import java.util.List;

/**
 * Compute-quota contract (docs/03 section 6): machine placement is licensed,
 * chunk gluttony pages management. BRP detects and notifies; consequences are
 * an administrative lifestyle choice.
 */
public interface QuotaService {

    String RESOURCE_MACHINE_SLOT = "machine_slot";

    QuotaBucketResponse setLimit(String tenantId, String resource, long limit);

    List<QuotaBucketResponse> usage(String tenantId);

    /** Fleet-wide buckets for the console. */
    List<QuotaBucketResponse> all();

    PlacementVerdict recordMachinePlaced(String tenantId, String machineId);

    void recordChunkPerformance(String tenantId, String dimension, String chunk, double costScore);

    List<ChunkIncidentResponse> incidents(String tenantId);
}
