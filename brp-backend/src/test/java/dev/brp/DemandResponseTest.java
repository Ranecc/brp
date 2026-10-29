package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.SignalDtos.SignalSetCommand;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.dto.LoadDtos.DeferredJobResponse;
import dev.brp.dto.LoadDtos.FileDeferredJobRequest;
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
 * Demand response, award path (docs/13 §5): file -> award (cheapest feasible
 * hours, reservation billed) -> dispatch (OPEN signal, RUNNING) -> window
 * elapses (CLOSE, DONE). The forecast is cold-start flat, so schedulability
 * rides the budget property; the tariff's always-peak window makes the
 * multiplier observable. Shedding lives in {@link DemandResponseShedTest}
 * — a grid that sheds at 9999 mspt is not a grid.
 */
@SpringBootTest(properties = {
        "brp.dr.mspt-budget=9999",
        "brp.levy.compute.base-emeralds-per-minute=10"})
class DemandResponseTest {

    @Autowired DemandResponseService dr;
    @Autowired DeferredJobRepository jobs;
    @Autowired LedgerService ledger;
    @Autowired SignalService signals;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired Clock clock;

    private String fund() {
        String tenant = "dr-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Deferred Co", "DFC", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        return tenant;
    }

    private long treasury(String tenant) {
        return ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }

    private DeferredJobResponse file(String tenant, long estMinutes) {
        LocalDateTime now = LocalDateTime.now(clock);
        return dr.file(tenant, new FileDeferredJobRequest(
                "job-" + UUID.randomUUID(), "compute", 0, estMinutes,
                now.minusMinutes(1).toString(), now.plusDays(2).toString()));
    }

    @Test
    void filingIsIdempotentByKey() {
        String tenant = fund();
        String key = "idem-" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now(clock);
        var first = dr.file(tenant, new FileDeferredJobRequest(key, "compute", 0, 60,
                now.minusMinutes(1).toString(), now.plusDays(2).toString()));
        var replay = dr.file(tenant, new FileDeferredJobRequest(key, "compute", 0, 60,
                now.minusMinutes(1).toString(), now.plusDays(2).toString()));
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(dr.list(tenant)).hasSize(1);
    }

    @Test
    void awardPicksAWindowBillsReservationAndDispatchesOpen() {
        String tenant = fund();
        var job = file(tenant, 60);
        long before = treasury(tenant);

        dr.awardDueJobs();

        var awarded = dr.list(tenant).get(0);
        assertThat(awarded.status()).isEqualTo("AWARDED");
        assertThat(awarded.awardedStart()).isNotNull();
        assertThat(awarded.windowSeq()).isEqualTo(1);
        // reservation: 60 min * 10c * multiplier(at window). The cold-start
        // forecast prices all hours flat, so any feasible window is fine —
        // the fee must simply have LEFT, at whatever the table said.
        assertThat(treasury(tenant)).isLessThan(before);

        dr.dispatchDueSignals();
        var running = dr.list(tenant).get(0);
        assertThat(running.status()).isEqualTo("RUNNING");
        var pending = signals.poll(tenant).pending();
        assertThat(pending).anyMatch(s -> s.channel().equals("@dr/" + job.id()) && s.level() == 15);
    }

    @Test
    void windowElapsingClosesTheLineAndDone() {
        String tenant = fund();
        var job = file(tenant, 60);
        dr.awardDueJobs();
        // force the window into the past: award, then re-stamp start an hour back
        var row = jobs.findById(job.id()).orElseThrow();
        row.award(row.getAwardedStart().minusHours(2), 60);
        row.markRunning();
        jobs.save(row);

        dr.dispatchDueSignals();

        var done = dr.list(tenant).get(0);
        assertThat(done.status()).isEqualTo("DONE");
        var pending = signals.poll(tenant).pending();
        assertThat(pending).anyMatch(s -> s.channel().equals("@dr/" + job.id()) && s.level() == 0);
    }

    @Test
    void unawardedJobPastItsDeadlineExpiresWithoutCharge() {
        String tenant = fund();
        LocalDateTime now = LocalDateTime.now(clock);
        // deadline already in the past: award finds nothing, expiry fires
        var job = dr.file(tenant, new FileDeferredJobRequest(
                "late-" + UUID.randomUUID(), "compute", 0, 60,
                now.minusHours(2).toString(), now.minusHours(1).toString()));
        long before = treasury(tenant);

        dr.awardDueJobs();

        var expired = dr.list(tenant).get(0);
        assertThat(expired.status()).isEqualTo("EXPIRED");
        assertThat(treasury(tenant)).isEqualTo(before);   // a promise to schedule is not a bill
    }

    @Test
    void cancelRefundsTheReservation() {
        String tenant = fund();
        var job = file(tenant, 60);
        dr.awardDueJobs();
        long reserved = 1_000_000L - treasury(tenant);
        assertThat(reserved).isGreaterThan(0);

        dr.cancel(tenant, job.id());

        var cancelled = dr.list(tenant).get(0);
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(treasury(tenant)).isEqualTo(1_000_000L);   // reservation came back
        var pending = signals.poll(tenant).pending();
        assertThat(pending).anyMatch(s -> s.channel().equals("@dr/" + job.id()) && s.level() == 0);
    }

    @Test
    void replayedAwardTickDoesNotDoubleBill() {
        String tenant = fund();
        file(tenant, 60);
        dr.awardDueJobs();
        long afterFirst = treasury(tenant);
        dr.awardDueJobs();   // job is AWARDED now, not FILED — the second tick is a no-op
        assertThat(treasury(tenant)).isEqualTo(afterFirst);
    }
}
