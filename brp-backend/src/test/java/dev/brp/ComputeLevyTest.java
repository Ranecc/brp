package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.repository.LevyMinuteRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.entity.TeamMemberRow;
import dev.brp.service.ComputeLevyService;
import dev.brp.service.LedgerService;
import dev.brp.service.RegimeService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The marginal compute levy (docs/12 §5): income-tax band semantics — no
 * cliff at any bracket edge, idempotent per tenant-minute, inert until the
 * owner prices compute.
 */
@SpringBootTest(properties = {
        "brp.levy.compute.base-emeralds-per-minute=10",
        "brp.peg.emerald.brc-per-emerald=1.0"})
class ComputeLevyTest {

    @Autowired ComputeLevyService levy;
    @Autowired RegimeService regime;
    @Autowired LevyMinuteRepository minutes;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;

    private String fund(String prefix, long cents) {
        String tenant = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Levy Co", "LVY", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", cents, "seed"),
                new JournalLine(member, "CREDIT", cents, "seed"))));
        return tenant;
    }

    @Test
    void loneWhalePaysTheFullCurveAtNoCliff() {
        regime.afterSnapshot(0.95, 0.2);   // armed
        String whale = fund("whale", 100_000_000L);
        LocalDateTime minute = LocalDateTime.now().minusMinutes(2).withSecond(0).withNano(0);
        levy.recordUsage(whale, 30, minute);
        levy.recordUsage(whale, 30, minute);   // same minute accumulates

        assertThat(levy.materialize(minute)).isEqualTo(1);
        var row = minutes.findById(new dev.brp.entity.LevyMinuteEntity.Pk(minute, whale)).orElseThrow();
        assertThat(row.getUnits()).isEqualTo(60);
        assertThat(row.getSharePct()).isEqualTo(1.0);
        // share 100%: (0.2*1.0 + 0.15*1.2 + 0.15*1.6 + 0.5*2.5) = 1.87x
        assertThat(row.getAmountCents()).isEqualTo((long) (60 * 10 * 1.87 * 100));
        // replay: the minute is priced once, the ledger is not asked twice
        assertThat(levy.materialize(minute)).isEqualTo(0);
    }

    @Test
    void twoHalvesSplitTheBillMarginalStyle() {
        regime.afterSnapshot(0.95, 0.2);
        String a = fund("half-a", 100_000_000L);
        String b = fund("half-b", 100_000_000L);
        LocalDateTime minute = LocalDateTime.now().minusMinutes(3).withSecond(0).withNano(0);
        levy.recordUsage(a, 30, minute);
        levy.recordUsage(b, 30, minute);

        levy.materialize(minute);
        double mult = dev.brp.service.impl.ComputeLevyServiceImpl.effectiveMultiplier(0.5);
        // (0.2*1.0 + 0.15*1.2 + 0.15*1.6) / 0.5 = 1.24x — the 2.5x band untouched
        assertThat(mult).isCloseTo(1.24, org.assertj.core.data.Offset.offset(1e-9));
        long expected = Math.round(30 * 10 * 1.24 * 100);
        assertThat(minutes.findById(new dev.brp.entity.LevyMinuteEntity.Pk(minute, a)).orElseThrow()
                .getAmountCents()).isEqualTo(expected);
        assertThat(minutes.findById(new dev.brp.entity.LevyMinuteEntity.Pk(minute, b)).orElseThrow()
                .getAmountCents()).isEqualTo(expected);
    }

    @Test
    void disarmedWeeksStillRecordAtZero() {
        regime.afterSnapshot(0.10, 0.1);   // INACTIVE: clemency is also a record
        String quiet = fund("quiet", 100_000_000L);
        LocalDateTime minute = LocalDateTime.now().minusMinutes(4).withSecond(0).withNano(0);
        levy.recordUsage(quiet, 10, minute);
        levy.materialize(minute);
        var row = minutes.findById(new dev.brp.entity.LevyMinuteEntity.Pk(minute, quiet)).orElseThrow();
        assertThat(row.getAmountCents()).isZero();
        assertThat(row.isPosted()).isTrue();
    }
}
