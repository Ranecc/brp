package dev.brp.service;

import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.GovernorStatusResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;

import java.util.List;

/**
 * Chunk governance (docs/09 section 3): registration, remote power,
 * credit metering inputs and the governor read model. Verdicts govern
 * BRP machinery only — never player movement.
 */
public interface ChunkService {

    String VERDICT_RUN = "RUN";
    String VERDICT_THROTTLE = "THROTTLE";
    String VERDICT_HALT = "HALT";

    /** Idempotent per (tenant, dimension, chunkKey): re-register returns the slot. */
    ChunkSlotResponse register(String tenantId, RegisterChunkRequest req);

    /** Grant chunk-load minutes (FAUCET wiring lands with P2-05). */
    ChunkSlotResponse grantMinutes(String tenantId, String dimension,
                                   String chunkKey, GrantMinutesRequest req);

    /** Admin remote switch (docs/09 section 3): DISABLED = governor HALTs the chunk. */
    ChunkSlotResponse setPower(String tenantId, String dimension,
                               String chunkKey, PowerRequest req);

    List<ChunkSlotResponse> slots(String tenantId);

    /** Governor read model for the agent (docs/09 section 4 decision table). */
    List<GovernorStatusResponse> governor(String tenantId);
}
