package dev.brp.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Telemetry plane DTOs: at-least-once delivery with eventId-based dedup
 * (docs/06-transactions.md sections 2-3).
 */
public final class TelemetryDtos {

    private TelemetryDtos() {}

    /**
     * reliability: CRITICAL (monetary/command events, never dropped) |
     * TELEMETRY (sampled/droppable under disk pressure, see agent WAL watermark).
     */
    public record EventEnvelope(
            @NotBlank String eventId,
            @NotBlank String type,
            int schemaVersion,
            @NotBlank String agentId,
            String tenantId,
            String occurredAt,
            String reliability,
            Object payload) {}

    public record TelemetryBatch(@NotEmpty List<EventEnvelope> events) {}

    public record TelemetryAck(int accepted, int duplicates) {}
}
