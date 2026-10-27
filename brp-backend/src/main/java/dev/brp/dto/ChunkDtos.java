package dev.brp.dto;

import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Chunk governance DTOs (docs/09 section 3): slots, power, credits, verdicts. */
public final class ChunkDtos {

    private ChunkDtos() {}

    public record RegisterChunkRequest(
            @NotBlank String dimension,
            @NotBlank String chunkKey,
            String registeredBy) {}

    public record GrantMinutesRequest(long minutes) {}

    /**
     * Money path (docs/09 section 3, prepaid model): treasury DEBIT ->
     * chunk-sink CREDIT through the ledger's same-transaction idempotency,
     * then the minutes land as credit. Same key, same minutes, one charge.
     */
    public record PurchaseCreditsRequest(
            @NotNull @Positive Long minutes,
            @NotBlank String idemKey) {}

    public record PowerRequest(boolean enabled) {}

    public record ChunkSlotResponse(
            String tenantId, String dimension, String chunkKey,
            String status, long creditMinutes, String registeredBy) {}

    /** Governor read model for the agent (docs/09 section 4). */
    public record GovernorStatusResponse(
            String dimension, String chunkKey, String verdict, String reason) {}

    /** Portal admin view: one slot next to its verdict. */
    public record ChunkAdminRow(ChunkSlotResponse slot, String verdict, String reason) {}
}
