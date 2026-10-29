package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.dto.LoadDtos.CreditStatusResponse;
import dev.brp.dto.LoadDtos.FileDeferredJobRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.CreditLineEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.DeferredJobRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.ChunkService;
import dev.brp.service.CreditService;
import dev.brp.service.DemandResponseService;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Credit cutoff (docs/13 §6): RESTRICTED closes the collateral (the chunks),
 * suspends flexible work, and refuses new commitments at the purchase desk —
 * while movement and play stay free (that half lives on the mod side, where
 * the governor never touches walk paths). grace-days stays at the default 7
 * here: RESTRICTED must HOLD; defaulting gets its own zero-grace context.
 */
@SpringBootTest(properties = "brp.chunk.governor.starter-minutes=0")
class CreditCutoffTest {

    @Autowired CreditService credit;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired ChunkService chunks;
    @Autowired DemandResponseService dr;
    @Autowired DeferredJobRepository jobs;
    @Autowired Clock clock;
    @Autowired dev.brp.repository.CreditAccrualRunRepository runs;

    /** Own the day's guard, then run the desk — same discipline as the profile refresh. */
    private boolean deskRun() {
        runs.deleteGuard(java.time.LocalDate.now(clock));
        return credit.runDailyDesk();
    }

    /** Seed 2M, overdraw the treasury by `overdraft`, set a tight limit. */
    private String overdrawnTenant(long overdraft, long limit) {
        String tenant = "cc-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Cut Co", "CUT", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 2_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 2_000_000L, "seed"))));
        credit.setLimit(tenant, limit);
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(member, "DEBIT", 2_000_000L + overdraft, "spend"),
                new JournalLine("treasury", "CREDIT", 2_000_000L + overdraft, "spend"))));
        return tenant;
    }

    @Test
    void firstLookCreatesAnAutoLine() {
        String tenant = "ca-" + UUID.randomUUID().toString().substring(0, 8);
        CreditStatusResponse s = credit.status(tenant);
        assertThat(s.limitCents()).isGreaterThanOrEqualTo(50_000L);   // the floor
        assertThat(s.status()).isEqualTo("GOOD");
        assertThat(s.drawnCents()).isZero();
    }

    @Test
    void sweepDrawsTheOverdraftAndRestricts() {
        String tenant = overdrawnTenant(300_000L, 300_000L);

        deskRun();

        CreditStatusResponse s = credit.status(tenant);
        // drawn = sweep + day-one interest
        assertThat(s.drawnCents()).isBetween(300_000L, 301_000L);
        assertThat(s.status()).isIn(CreditLineEntity.RESTRICTED, CreditLineEntity.DEFAULTED);
        // the sweep moved real BRC: treasury is back to exactly zero
        long treasury = ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
        assertThat(treasury).isZero();
        // interest landed on day one
        assertThat(credit.events(tenant)).anyMatch(e -> dev.brp.entity.CreditEventEntity.INTEREST.equals(e.getKind()));
    }

    @Test
    void restrictedClosesChunksAndBlocksPurchasesAndFiling() {
        String tenant = overdrawnTenant(100_000L, 100_000L);
        chunks.register(tenant, new RegisterChunkRequest("minecraft:overworld", "3,4", "u"));
        chunks.setPower(tenant, "minecraft:overworld", "3,4",
                new dev.brp.dto.ChunkDtos.PowerRequest(true));

        deskRun();

        // the collateral closed
        var slot = chunks.slots(tenant).get(0);
        assertThat(slot.status()).isEqualTo("DISABLED");
        // new commitments refused at the desks
        assertThat(credit.blocked(tenant)).isTrue();
        assertThatThrownBy(() -> chunks.purchaseCredits(tenant, "minecraft:overworld", "3,4",
                new dev.brp.dto.ChunkDtos.PurchaseCreditsRequest(10L, "x-" + UUID.randomUUID())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("credit.restricted");
        LocalDateTime now = LocalDateTime.now(clock);
        assertThatThrownBy(() -> dr.file(tenant, new FileDeferredJobRequest(
                "blocked-" + UUID.randomUUID(), "compute", 0, 60,
                now.minusMinutes(1).toString(), now.plusDays(1).toString())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("credit.restricted");
        // while movement and play stay free: slots() still readable, no player path touched
        assertThat(chunks.slots(tenant)).hasSize(1);
    }

    @Test
    void repayBringsTheStatusBackDownOneStep() {
        String tenant = overdrawnTenant(100_000L, 100_000L);
        deskRun();
        assertThat(credit.blocked(tenant)).isTrue();

        // pay cash back INTO the treasury, then repay
        String member = memberOf(tenant);
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(member, "CREDIT", 150_000L, "windfall"),
                new JournalLine("treasury", "DEBIT", 150_000L, "windfall"))));
        CreditStatusResponse after = credit.repay(tenant, 150_000L);

        assertThat(after.drawnCents()).isLessThan(100_000L);
        assertThat(after.status()).isEqualTo("WATCH");   // one step, not a leap
        assertThat(credit.events(tenant)).anyMatch(e -> dev.brp.entity.CreditEventEntity.REPAY.equals(e.getKind()));
    }

    @Test
    void repayWithoutCashIsRefused() {
        String tenant = overdrawnTenant(50_000L, 50_000L);
        deskRun();   // treasury swept to exactly zero
        assertThatThrownBy(() -> credit.repay(tenant, 10_000L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("credit.no.cash");
    }

    @Test
    void deskRunsOncePerDay() {
        String tenant = overdrawnTenant(10_000L, 10_000L);
        assertThat(deskRun()).isTrue();
        assertThat(credit.runDailyDesk()).isFalse();
    }

    private String memberOf(String tenant) {
        // teams.create also files the OWNER under the tenant id — take the MEMBER
        return memberRepo.findAll().stream()
                .filter(m -> m.getTeamId().equals(tenant) && "MEMBER".equals(m.getRole()))
                .findFirst().orElseThrow().getPlayerUuid();
    }
}
