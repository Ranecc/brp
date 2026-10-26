package dev.brp.contract;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Push plane DTOs: v0 uses poll + ack (SSE planned for P1). At-least-once;
 * agent-side execution is idempotent (channel is last-writer-wins).
 */
public final class SignalDtos {

    private SignalDtos() {}

    public record SignalSetCommand(
            @NotBlank String channel,       // namespaced channels like "@team/farm_north" (docs/02 §5)
            @Min(0) @Max(15) int level) {}

    public record SignalCommandDto(long id, String channel, int level, String status, String createdAt) {}

    public record SignalAck(@NotEmpty List<Long> ids) {}

    public record SignalPollResult(List<SignalCommandDto> pending, long cursor) {}
}
