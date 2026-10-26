package dev.brp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Compute quota DTOs: machine placement is a licensed activity. */
public final class QuotaDtos {

    private QuotaDtos() {}

    public record SetQuotaRequest(@NotNull @PositiveOrZero Long limit) {}

    public record QuotaBucketResponse(
            String tenantId, String resource, long limit, long used) {}

    public record ChunkIncidentResponse(
            long id, String tenantId, String dimension, String chunk,
            double costScore, String status) {}

    public record PlacementVerdict(boolean licensed, String resource) {}
}
