package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.DeferredJobEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.DeferredJobRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.DemandResponseService;
import dev.brp.service.LedgerService;
import dev.brp.service.SignalService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demand response, shed path (docs/13 §5): when live mspt breaches the
 * budget, running windows CLOSE first (DR is the interruptible load), the
 * deadline extends, and after max-sheds the job cancels with the reservation
 * refunded to the cent. Own context because the shed budget must be LOW —
 * the award-path test needs it HIGH. Different grids, different breakers.
 */
@SpringBootTest(properties = {
        "brp.dr.mspt-budget=10",
        "brp.levy.compute.base-emeralds-per-minute=10",
        "brp.dr.max-sheds=2"})
class DemandResponseShedTest {

    @Autowired DemandResponseService dr;
    @Autowired DeferredJobRepository jobs;
    @Autowired LedgerService ledger;
    @Autowired SignalService signals;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired Clock clock;

    private record Ctx(String tenant, long jobId) {}

    /** A RUNNING job with a billed reservation, ready for the grid to shed. */
    private Ctx runningJob() {
        String tenant = "ds-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Shed Co", "SHD", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        LocalDateTime now = LocalDateTime.now(clock);
        var job = jobs.save(new DeferredJobEntity(tenant, "shed-" + UUID.randomUUID(),
                "compute", 0, 60, now.minusMinutes(1), now.plusDays(2)));
        job.award(now.truncatedTo(java.time.temporal.ChronoUnit.HOURS), 60);
        job.markRunning();
        jobs.save(job);
        // bill the reservation the scheduler would have billed (idem-compatible);
        // provision the sink first — the service does this lazily, we are earlier
        try {
            ledger.createAccount(tenant, new CreateAccountRequest("dr-sink", "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // sinks are patient
        }
        ledger.post(tenant, new PostJournalRequest(
                "dr-reserve:" + job.getId() + ":" + job.getWindowSeq(), "BRC", List.of(
                new JournalLine("dr-sink", "DEBIT", 600L, null),
                new JournalLine("treasury", "CREDIT", 600L, null))));
        return new Ctx(tenant, job.getId());
    }

    private String status(long jobId) {
        return jobs.findById(jobId).orElseThrow().getStatus();
    }

    @Test
    void belowBudgetIsNoOp() {
        Ctx ctx = runningJob();
        dr.shedIfOverloaded(5.0);
        assertThat(status(ctx.jobId())).isEqualTo("RUNNING");
    }

    @Test
    void overloadShedClosesExtendsAndRefunds() {
        Ctx ctx = runningJob();
        long before = treasuryOf(ctx.tenant());
        LocalDateTime deadlineBefore = jobs.findById(ctx.jobId()).orElseThrow().getDeadline();

        dr.shedIfOverloaded(50.0);

        var row = jobs.findById(ctx.jobId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("AWARDED");
        assertThat(row.getShedCount()).isEqualTo(1);
        assertThat(row.getDeadline()).isEqualTo(deadlineBefore.plusHours(1));
        // reservation refunded: treasury back up by the 600 billed
        assertThat(treasuryOf(ctx.tenant())).isEqualTo(before + 600L);
        var pending = signals.poll(ctx.tenant()).pending();
        assertThat(pending).anyMatch(s -> s.channel().equals("@dr/" + ctx.jobId()) && s.level() == 0);
    }

    @Test
    void thirdOverloadCancelsTheJob() {
        Ctx ctx = runningJob();
        dr.shedIfOverloaded(50.0);   // shed 1
        rerun(ctx.jobId());
        dr.shedIfOverloaded(50.0);   // shed 2
        rerun(ctx.jobId());
        dr.shedIfOverloaded(50.0);   // shed 3 -> budget exhausted

        assertThat(status(ctx.jobId())).isEqualTo("CANCELLED");
        assertThat(treasuryOf(ctx.tenant())).isEqualTo(1_000_000L);   // every cent came home
    }

    private void rerun(long jobId) {
        var row = jobs.findById(jobId).orElseThrow();
        row.award(LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.HOURS), 60);
        row.markRunning();
        jobs.save(row);
        ledger.post(row.getTenantId(), new PostJournalRequest(
                "dr-reserve:" + row.getId() + ":" + row.getWindowSeq(), "BRC", List.of(
                new JournalLine("dr-sink", "DEBIT", 600L, null),
                new JournalLine("treasury", "CREDIT", 600L, null))));
    }

    private long treasuryOf(String tenant) {
        return ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }
}
