package dev.brp.dto;

import jakarta.validation.constraints.NotBlank;

/** Chunk governance DTOs (docs/09 section 3): slots, power, verdicts. */
public final class ChunkDtos {

    private ChunkDtos() {}

    public record RegisterChunkRequest(
            @NotBlank String dimension,
            @NotBlank String chunkKey,
            String registeredBy) {}

    public record GrantMinutesRequest(long minutes) {}

    public record PowerRequest(boolean enabled) {}

    public record ChunkSlotResponse(
            String tenantId, String dimension, String chunkKey,
            String status, long creditMinutes, String registeredBy) {}

    /** Governor read model for the agent (docs/09 section 4). */
    public record GovernorStatusResponse(
            String dimension, String chunkKey, String verdict, String reason) {}
}
