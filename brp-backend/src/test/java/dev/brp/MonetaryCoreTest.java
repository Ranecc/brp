package dev.brp;

import dev.brp.common.ConflictException;
import dev.brp.dto.MonetaryDtos.SnapshotResponse;
import dev.brp.dto.MonetaryDtos.WealthAssessmentRow;
import dev.brp.repository.AnomalyHitRepository;
import dev.brp.service.RegimeService;
import dev.brp.service.SnapshotService;
import dev.brp.service.WealthLevyService;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.entity.TeamMemberRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3 core (docs/12): the shared snapshot, the hysteresis band, and the
 * wealth levy that burns instead of spends.
 */
@SpringBootTest
class MonetaryCoreTest {

    @Autowired SnapshotService snapshots;
    @Autowired RegimeService regime;
    @Autowired WealthLevyService wealthLevy;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired AnomalyHitRepository hits;
    @Autowired dev.brp.repository.SnapshotTenantRepository tenantRows;

    private String fund(String prefix, long cents) {
        String tenant = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Money Co", "MON", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", cents, "seed"),
                new JournalLine(member, "CREDIT", cents, "seed"))));
        return tenant;
    }

    @Test
    void hysteresisHoldsTheBandAndFlipsOnlyAtTheEdges() {
        assertThat(regime.afterSnapshot(0.85, 0.2)).isEqualTo("ACTIVE");     // arms
        assertThat(regime.afterSnapshot(0.75, 0.2)).isEqualTo("ACTIVE");     // holds in band
        assertThat(regime.afterSnapshot(0.78, 0.2)).isEqualTo("ACTIVE");     // still holds
        assertThat(regime.afterSnapshot(0.65, 0.2)).isEqualTo("INACTIVE");   // disarms
        assertThat(regime.afterSnapshot(0.75, 0.2)).isEqualTo("INACTIVE");   // holds the other way
        assertThat(regime.regime().state()).isEqualTo("INACTIVE");
    }

    @Test
    void redlineFilesItsOwnLoudPaperwork() {
        int before = hits.findAll().size();
        regime.afterSnapshot(0.5, 0.73);
        var recent = hits.findTop50ByOrderByIdDesc();
        assertThat(recent.size()).isGreaterThan(before);
        assertThat(recent.get(0).getRuleId()).isEqualTo(950L);
    }

    @Test
    void snapshotIsOneTruthPerWeekAndSeesTheTreasures() {
        String rich = fund("rich", 50_000_000L);
        SnapshotResponse s = snapshots.capture();
        assertThat(s.tenants()).anyMatch(t -> t.tenantId().equals(rich)
                && t.wealthCents() == 50_000_000L);
        assertThat(s.gini()).isBetween(0.0, 1.0);
        // second capture the same week: the truth does not take retakes
        assertThatThrownBy(snapshots::capture).isInstanceOf(ConflictException.class);
    }

    @Test
    void wealthLevyBurnsByBracketsAndReplaysForFree() {
        String whale = fund("whale", 20_000_000L);
        // the week may already be captured (one truth per week) — the levy
        // desk under test reads the tenant rows, so hand it the whale's row
        SnapshotResponse s = snapshots.latest() != null ? snapshots.latest() : snapshots.capture();
        String week = s.weekOf();
        tenantRows.save(new dev.brp.entity.SnapshotTenantEntity(week, whale, 20_000_000L, 0.0));
        regime.afterSnapshot(0.95, 0.2);   // arm AFTER capture: capture re-reads the real Gini

        List<WealthAssessmentRow> rows = wealthLevy.assess(week);
        var whaleRow = rows.stream().filter(r -> r.tenantId().equals(whale)).findFirst().orElseThrow();
        // brackets: 9M @ 0.5% = 45,000 + 10M @ 1.5% = 150,000 -> 195,000;
        // zero volume -> velocity 0 -> hoarding surcharge +50% -> 292,500
        assertThat(whaleRow.amountCents()).isEqualTo(292_500L);
        assertThat(whaleRow.surcharge()).isTrue();

        // replay: same week, same rows, no second burn
        List<WealthAssessmentRow> replay = wealthLevy.assess(week);
        assertThat(replay.stream().filter(r -> r.tenantId().equals(whale)).findFirst().orElseThrow()
                .amountCents()).isEqualTo(292_500L);
        long treasury = ledger.listAccounts(whale).stream()
                .filter(a -> a.accountNo().equals("treasury")).findFirst().orElseThrow().balanceCents();
        assertThat(treasury).isEqualTo(20_000_000L - 292_500L);
    }
}
