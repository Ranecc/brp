package dev.brp;

import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.dto.TeamDtos.MemberDto;
import dev.brp.dto.TeamDtos.TeamDto;
import dev.brp.dto.TeamDtos.TeamSyncRequest;
import dev.brp.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Native teams (ledger-backed treasury) + compat mirror sync (FTB-style). */
@SpringBootTest
class TeamTest {

    @Autowired TeamService teams;
    @Autowired TeamMemberRepository members;
    @Autowired LedgerService ledger;

    @Test
    void nativeCreateProvisionsTeamAndTreasuryAccount() {
        String teamId = "guild-" + UUID.randomUUID().toString().substring(0, 8);
        String owner = UUID.randomUUID().toString();

        Map<String, Object> created = teams.create(new CreateTeamRequest(
                UUID.randomUUID().toString(), teamId, "Dawn Brigade", "DAWN", owner));

        assertThat(created).containsEntry("teamId", teamId);
        assertThat(ledger.listAccounts(teamId))
                .anyMatch(a -> a.accountNo().equals("treasury"));

        // native team refuses compat overwrite
        assertThatThrownBy(() -> teams.sync(new TeamSyncRequest(
                UUID.randomUUID().toString(), "FTBTEAMS",
                List.of(new TeamDto(teamId, "hijack", null,
                        List.of(new MemberDto(owner, "OWNER")))))))
                .hasMessageContaining("NATIVE");
    }

    @Test
    void compatSyncMirrorsRostersAndAppliesLeaves() {
        String teamId = "ftb-" + UUID.randomUUID().toString().substring(0, 8);
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();

        teams.sync(new TeamSyncRequest(UUID.randomUUID().toString(), "FTBTEAMS",
                List.of(new TeamDto(teamId, "Rust Engineers", "RUST",
                        List.of(new MemberDto(a, "OWNER"), new MemberDto(b, "MEMBER"))))));
        assertThat(members.findByTeamId(teamId)).hasSize(2);

        // b leaves game-side: next sync removes the row
        teams.sync(new TeamSyncRequest(UUID.randomUUID().toString(), "FTBTEAMS",
                List.of(new TeamDto(teamId, "Rust Engineers", "RUST",
                        List.of(new MemberDto(a, "OWNER"))))));
        assertThat(members.findByTeamId(teamId)).hasSize(1);

        // duplicate batch replays, never re-applies
        var first = teams.sync(new TeamSyncRequest("batch-" + teamId, "FTBTEAMS",
                List.of(new TeamDto(teamId, "Rust Engineers", "RUST",
                        List.of(new MemberDto(a, "OWNER"))))));
        var replay = teams.sync(new TeamSyncRequest("batch-" + teamId, "FTBTEAMS",
                List.of(new TeamDto(teamId, "Rust Engineers", "RUST",
                        List.of(new MemberDto(a, "OWNER"))))));
        assertThat(replay).isEqualTo(first);
        assertThat(members.findByTeamId(teamId)).hasSize(1);
    }
}
