package dev.brp.player.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Player sync request DTOs. */
public final class PlayerDtos {

    private PlayerDtos() {}

    public record PlayerDto(@NotBlank String uuid, @NotBlank String name) {}

    public record PlayerSyncRequest(
            @NotBlank String batchId,
            @NotEmpty @Valid List<PlayerDto> players) {}

    public record SyncAck(int inserted, int renamed, int refreshed) {}
}
