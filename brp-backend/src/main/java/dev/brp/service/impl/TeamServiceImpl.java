package dev.brp.service.impl;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.*;

import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Idempotent;
import dev.brp.contract.LedgerDtos;
import dev.brp.service.LedgerService;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.dto.TeamDtos.MemberDto;
import dev.brp.dto.TeamDtos.TeamDto;
import dev.brp.dto.TeamDtos.TeamSyncRequest;
import dev.brp.dto.TeamResponse;
import dev.brp.mapper.TeamMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Native authority + compat mirror (docs/07-tenants.md): native create is
 * atomic and provisions a treasury account; compat sync replaces rosters
 * wholesale and never touches NATIVE rows.
 */
@Service
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final LedgerService ledger;
    private final TeamMapper mapper;

    public TeamServiceImpl(TeamRepository teams, TeamMemberRepository members,
                           LedgerService ledger, TeamMapper mapper) {
        this.teams = teams;
        this.members = members;
        this.ledger = ledger;
        this.mapper = mapper;
    }

    @Override
    @Idempotent(key = "#req.idempotencyKey()")
    @Atomic
    public Map<String, Object> create(CreateTeamRequest req) {
        if (teams.existsById(req.teamId())) {
            throw new ConflictException("team already exists: " + req.teamId());
        }
        teams.save(new TeamEntity(req.teamId(), req.name(), req.tag(), "NATIVE"));
        members.save(new TeamMemberRow(req.teamId(), req.ownerUuid(), "OWNER"));
        var treasury = ledger.createAccount(req.teamId(),
                new LedgerDtos.CreateAccountRequest("treasury", "TEAM", "BRC"));
        return Map.of("teamId", req.teamId(), "treasuryAccount", treasury.accountNo());
    }

    @Override
    @Idempotent(key = "#req.batchId()")
    @Atomic
    public Map<String, Object> sync(TeamSyncRequest req) {
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

    @Override
    public List<TeamResponse> list(String source) {
        List<TeamEntity> wanted = teams.findAll().stream()
                .filter(t -> source == null || source.equals(t.getSource()))
                .toList();
        // one query for ALL rosters, grouped — no N+1
        Map<String, List<dev.brp.entity.TeamMemberRow>> rosters =
                members.findAll().stream().collect(Collectors.groupingBy(
                        dev.brp.entity.TeamMemberRow::getTeamId));
        return wanted.stream()
                .map(t -> mapper.toDto(t, rosters.getOrDefault(t.getId(), List.of())))
                .toList();
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
