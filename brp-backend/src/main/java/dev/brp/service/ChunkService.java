package dev.brp.service;

import dev.brp.dto.ChunkDtos.ChunkAdminRow;
import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.GovernorStatusResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.ChunkDtos.PurchaseCreditsRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;

import java.util.List;

/**
 * Chunk governance (docs/09 section 3): registration, remote power,
 * prepaid credits, and the governor read model. Verdicts govern BRP
 * machinery only — never player movement.
 */
public interface ChunkService {

    String VERDICT_RUN = "RUN";
    String VERDICT_THROTTLE = "THROTTLE";
    String VERDICT_HALT = "HALT";

    /** Idempotent per (tenant, dimension, chunkKey): re-register returns the slot. */
    ChunkSlotResponse register(String tenantId, RegisterChunkRequest req);

    /** Grant chunk-load minutes (starter bundle + admin top-ups). */
    ChunkSlotResponse grantMinutes(String tenantId, String dimension,
                                   String chunkKey, GrantMinutesRequest req);

    /**
     * Money path: charge treasury -> chunk-sink via the ledger (idempotency
     * key carried by the request), then credit the minutes. Prepaid model:
     * credits burn as the agent reports chunk.load.minutes.
     */
    ChunkSlotResponse purchaseCredits(String tenantId, String dimension,
                                      String chunkKey, PurchaseCreditsRequest req);

    /**
     * Agent metering (chunk.load.minutes events): burn credit. Crossing to
     * <= 0 notifies once. Unknown chunks are a no-op — rogue-loader
     * detection is P2-12's detective work, not the meter's.
     */
    void accrueMinutes(String tenantId, String dimension, String chunkKey, long minutes);

    /** Admin remote switch (docs/09 section 3): DISABLED = governor HALTs the chunk. */
    ChunkSlotResponse setPower(String tenantId, String dimension,
                               String chunkKey, PowerRequest req);

    List<ChunkSlotResponse> slots(String tenantId);

    /** Portal admin view across all tenants: slot + verdict, one row. */
    List<ChunkAdminRow> adminRows();

    /** Governor read model for the agent (docs/09 section 4 decision table). */
    List<GovernorStatusResponse> governor(String tenantId);
}
