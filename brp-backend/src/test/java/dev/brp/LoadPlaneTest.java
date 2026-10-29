package dev.brp;

import dev.brp.common.event.BrpEventTypes;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.TelemetryDtos.EventEnvelope;
import dev.brp.contract.TelemetryDtos.TelemetryBatch;
import dev.brp.dto.ChunkDtos.PurchaseCreditsRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.dto.LoadDtos.AddTariffWindowRequest;
import dev.brp.dto.LoadDtos.ForecastResponse;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.LoadProfileRepository;
import dev.brp.repository.LoadSampleRepository;
import dev.brp.repository.ProfileRunRepository;
import dev.brp.repository.TariffWindowRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.ChunkService;
import dev.brp.service.LedgerService;
import dev.brp.service.LoadSampleService;
import dev.brp.service.TariffService;
import dev.brp.service.TeamService;
import dev.brp.service.TelemetryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The load plane end to end (docs/13 sections 2-4): heartbeat -> minute
 * sample -> daily profile -> forecast, and the tariff table pricing chunk
 * billing. Shared-H2 discipline: every assertion scopes itself to rows this
 * test just created, and the version-1 tariff rows are cleaned up in
 * {@link #afterEach()} — version 0 belongs to boot config, version 1+ to us.
 */
@SpringBootTest(properties = {
        "brp.chunk.billing.cents-per-minute=10",
        "brp.chunk.governor.starter-minutes=0"})
class LoadPlaneTest {

    @Autowired TelemetryService telemetry;
    @Autowired LoadSampleService loadSamples;
    @Autowired TariffService tariff;
    @Autowired LoadSampleRepository sampleRepo;
    @Autowired LoadProfileRepository profileRepo;
    @Autowired ProfileRunRepository profileRuns;
    @Autowired TariffWindowRepository tariffRows;
    @Autowired ChunkService chunks;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired org.springframework.beans.factory.ObjectProvider<java.time.Clock> clockProvider;

    private java.time.Clock clock() {
        return clockProvider.getObject();   // same clock the service reads — UTC, not the CI box's zone
    }

    private final List<Long> addedTariffRows = new java.util.ArrayList<>();

    @AfterEach
    void afterEach() {
        tariffRows.deleteAllById(addedTariffRows);
        addedTariffRows.clear();
    }

    private LocalDateTime currentMinute() {
        return LocalDateTime.now(clock()).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
    }

    private void heartbeat(double mspt, int players, int entities, int chunks, int forced) {
        telemetry.ingest(new TelemetryBatch(List.of(new EventEnvelope(
                UUID.randomUUID().toString(), BrpEventTypes.AGENT_HEARTBEAT, 1,
                "agent-load-test", null, null, "TELEMETRY",
                Map.of("mspt", mspt, "tps", Math.min(20.0, 1000.0 / Math.max(mspt, 0.001)),
                        "backlog", 0, "players", players, "entities", entities,
                        "chunks", chunks, "forced", forced)))));
    }

    @Test
    void heartbeatLandsInMinuteBucketWithCensus() {
        double mspt = 12.5;
        heartbeat(mspt, 7, 1234, 56, 9);
        var row = sampleRepo.findById(currentMinute()).orElseThrow();
        assertThat(row.getSampleCount()).isGreaterThanOrEqualTo(1);
        assertThat(row.getPlayerMax()).isGreaterThanOrEqualTo(7);
        assertThat(row.getEntityMax()).isGreaterThanOrEqualTo(1234);
        assertThat(row.getChunkMax()).isGreaterThanOrEqualTo(56);
        assertThat(row.getForcedMax()).isGreaterThanOrEqualTo(9);
        assertThat(row.getMsptMax()).isGreaterThanOrEqualTo(mspt);
    }

    @Test
    void incrementalMeanAveragesAcrossSamplesInOneMinute() {
        var entity = new dev.brp.entity.LoadSampleEntity(currentMinute(), 10.0, 1, 1, 1, 1);
        entity.addSample(30.0, 2, 2, 2, 2);
        assertThat(entity.getSampleCount()).isEqualTo(2);
        assertThat(entity.getMsptAvg()).isCloseTo(20.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(entity.getMsptMax()).isEqualTo(30.0);
        assertThat(entity.getPlayerMax()).isEqualTo(2);
    }

    @Test
    void profileRefreshIsIdempotentPerDayAndBlendsToday() {
        heartbeat(33.0, 3, 300, 30, 3);
        int minuteOfDay = currentMinute().getHour() * 60 + currentMinute().getMinute();

        profileRuns.deleteGuard(LocalDate.now(clock()));   // own the guard for today's run
        boolean first = loadSamples.refreshProfile();
        boolean second = loadSamples.refreshProfile();
        assertThat(first).isTrue();
        assertThat(second).isFalse();

        var row = profileRepo.findById(minuteOfDay).orElseThrow();
        assertThat(row.getSampleDays()).isGreaterThanOrEqualTo(1);
        // the row is global (shared minute-of-day): other heartbeats blend in,
        // so we assert our contribution landed, not the exact mixture
        assertThat(row.getEwmaMspt()).isBetween(0.01, 100.0);
    }

    @Test
    void forecastReportsShapeAndTariffClassification() {
        heartbeat(35.0, 5, 500, 50, 5);
        profileRuns.deleteGuard(LocalDate.now(clock()));
        loadSamples.refreshProfile();

        ForecastResponse fc = loadSamples.forecast(24);
        assertThat(fc.hours()).hasSize(24);
        assertThat(fc.coldStart()).isTrue();    // one contributing day, honestly labeled
        assertThat(fc.profileDays()).isGreaterThanOrEqualTo(1);
        for (var hour : fc.hours()) {
            assertThat(hour.tariffLabel()).isIn("VALLEY", "SHOULDER", "PEAK");
            assertThat(hour.multiplierPct()).isGreaterThan(0);
            assertThat(hour.utilization()).isCloseTo(hour.predictedMspt() / 50.0,
                    org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Test
    void chunkBillingFollowsTheTariffTableNotOldConfigFields() {
        String tenant = "lp-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Load Co", "LPD", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        chunks.register(tenant, new RegisterChunkRequest("minecraft:overworld", "9,9", "u"));

        // default seed (19-23h at par, surcharge 0): 100 min * 10c = 1000 cents
        chunks.purchaseCredits(tenant, "minecraft:overworld", "9,9",
                new PurchaseCreditsRequest(100L, "lp-1-" + UUID.randomUUID()));
        long afterPar = treasury(tenant);
        assertThat(1_000_000L - afterPar).isEqualTo(1_000L);

        // a version-1 window doubles the price — the table is the source of truth
        var added = tariff.addWindow(new AddTariffWindowRequest(-1, 0, 24 * 60, 200, "PEAK"));
        addedTariffRows.add(added.id());
        chunks.purchaseCredits(tenant, "minecraft:overworld", "9,9",
                new PurchaseCreditsRequest(100L, "lp-2-" + UUID.randomUUID()));
        assertThat(1_000_000L - treasury(tenant)).isEqualTo(3_000L);   // 1000 + 2000

        // window gone -> par pricing again (delete first, then assert)
        tariffRows.deleteAllById(addedTariffRows);
        addedTariffRows.clear();
        assertThat(tariff.multiplierPctAt(java.time.Instant.now())).isEqualTo(100);
    }

    private long treasury(String teamId) {
        return ledger.listAccounts(teamId).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }
}
