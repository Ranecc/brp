package dev.brp.dto;

import dev.brp.entity.AnomalyRuleEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** L1 hot-rule DTOs (docs/10 R-2). */
public final class AnomalyDtos {

    private AnomalyDtos() {}

    public record SetRuleRequest(
            @NotBlank String name,
            @NotBlank String eventType,
            @NotBlank String field,
            @NotBlank String op,          // GT | GTE | LT | LTE | EQ
            @NotNull Double threshold,
            String severity) {}

    public record AnomalyRuleResponse(
            long id, String name, String eventType, String field,
            String op, double threshold, String severity, String status) {}

    public record AnomalyHitResponse(
            long id, String ruleName, String eventType, double observed,
            String severity, String createdAt) {}

    public static AnomalyRuleResponse toRuleDto(AnomalyRuleEntity e) {
        return new AnomalyRuleResponse(e.getId() == null ? 0 : e.getId(), e.getName(),
                e.getEventType(), e.getField(), e.getOp(), e.getThreshold(),
                e.getSeverity(), e.getStatus());
    }
}
