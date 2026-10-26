package dev.brp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Team request DTOs. */
public final class TeamDtos {

    private TeamDtos() {}

    public record CreateTeamRequest(
            @NotBlank String idempotencyKey,
            @NotBlank String teamId,
            @NotBlank String name,
            String tag,
            @NotBlank String ownerUuid) {}

    public record MemberDto(@NotBlank String uuid, @NotBlank String role) {}

    public record TeamDto(
            @NotBlank String id,
            @NotBlank String name,
            String tag,
            @NotEmpty List<MemberDto> members) {}

    public record TeamSyncRequest(
            @NotBlank String batchId,
            @NotBlank String source,          // FTBTEAMS | ...
            @NotEmpty @Valid List<TeamDto> teams) {}
}
