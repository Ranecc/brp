package dev.brp.team;

import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Idempotent;
import dev.brp.contract.LedgerDtos;
import dev.brp.ledger.LedgerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tenant teams: native authority + compat mirror (docs/07-tenants.md).
 *
 * NATIVE teams: created here, treasury account provisioned in the ledger.
 * Compat teams (FTBTEAMS, ...): the agent pulls game-side rosters and syncs
 * them wholesale; native write endpoints refuse compat-sourced rows.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final LedgerService ledger;

    public TeamController(TeamRepository teams, TeamMemberRepository members,
                          LedgerService ledger) {
        this.teams = teams;
        this.members = members;
        this.ledger = ledger;
    }

    // --- native authority ---

    public record CreateTeamRequest(
            @NotBlank String idempotencyKey,
            @NotBlank String teamId,
            @NotBlank String name,
            String tag,
            @NotBlank String ownerUuid) {}

    @PostMapping
    @Idempotent(key = "#req.idempotencyKey()")
    @Atomic
    public Map<String, Object> create(@Valid @RequestBody CreateTeamRequest req) {
        if (teams.existsById(req.teamId())) {
            throw new ConflictException("team already exists: " + req.teamId());
        }
        teams.save(new TeamEntity(req.teamId(), req.name(), req.tag(), "NATIVE"));
        members.save(new TeamMemberRow(req.teamId(), req.ownerUuid(), "OWNER"));
        var treasury = ledger.createAccount(req.teamId(),
                new LedgerDtos.CreateAccountRequest("treasury", "TEAM", "BRC"));
        return Map.of("teamId", req.teamId(), "treasuryAccount", treasury.accountNo());
    }

    // --- compat mirror sync (agent-pulled) ---

    public record MemberDto(@NotBlank String uuid, @NotBlank String role) {}

    public record TeamDto(
            @NotBlank String id,
            @NotBlank String name,
            String tag,
            @NotEmpty List<MemberDto> members) {}

    public record TeamSyncRequest(
            @NotBlank String batchId,
            @NotBlank String source,          // FTBTEAMS | ...
            @NotEmpty List<TeamDto> teams) {}

    @PostMapping("/sync")
    @Idempotent(key = "#req.batchId()")
    @Atomic
    public Map<String, Object> sync(@Valid @RequestBody TeamSyncRequest req) {
        int upserted = 0, roster = 0;
        for (TeamDto dto : req.teams()) {
            TeamEntity team = teams.findById(dto.id()).orElse(null);
            if (team == null) {
                teams.save(new TeamEntity(dto.id(), dto.name(), dto.tag(), req.source()));
            } else {
                if ("NATIVE".equals(team.getSource())) {
                    throw new BadRequestException(
                            "team " + dto.id() + " is NATIVE; refusing compat overwrite");
                }
                team.updateFrom(dto.name(), dto.tag());
                teams.save(team);
            }
            upserted++;
            roster += replaceRoster(dto.id(), dto.members());
        }
        return Map.of("source", req.source(), "teams", upserted, "members", roster);
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String source) {
        List<TeamEntity> all = teams.findAll().stream()
                .filter(t -> source == null || source.equals(t.getSource()))
                .toList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (TeamEntity t : all) {
            out.add(Map.of(
                    "id", t.getId(),
                    "name", t.getName(),
                    "tag", t.getTag() == null ? "" : t.getTag(),
                    "source", t.getSource(),
                    "members", members.findByTeamId(t.getId()).stream()
                            .map(m -> Map.of("uuid", m.getPlayerUuid(), "role", m.getRole()))
                            .toList()));
        }
        return out;
    }

    /** Replace roster wholesale but keep joined_at for continuing members. */
    private int replaceRoster(String teamId, List<MemberDto> incoming) {
        List<TeamMemberRow> current = members.findByTeamId(teamId);
        Set<String> incomingUuids = incoming.stream()
                .map(MemberDto::uuid).collect(Collectors.toSet());
        int changed = 0;
        for (TeamMemberRow row : current) {
            if (!incomingUuids.contains(row.getPlayerUuid())) {
                members.delete(row);   // left the team game-side
                changed++;
            }
        }
        Map<String, TeamMemberRow> byUuid = current.stream()
                .filter(r -> incomingUuids.contains(r.getPlayerUuid()))
                .collect(Collectors.toMap(TeamMemberRow::getPlayerUuid, Function.identity()));
        for (MemberDto dto : incoming) {
            TeamMemberRow existing = byUuid.get(dto.uuid());
            if (existing == null) {
                members.save(new TeamMemberRow(teamId, dto.uuid(), dto.role()));
                changed++;
            } else if (!existing.getRole().equals(dto.role())) {
                existing.setRole(dto.role());
                members.save(existing);
                changed++;
            }
        }
        return changed;
    }
}
