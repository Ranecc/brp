package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.service.LedgerService;
import dev.brp.service.MarketPriceService;
import dev.brp.service.MintService;
import dev.brp.service.TeamService;
import dev.brp.service.WashService;
import dev.brp.service.ChunkService;
import dev.brp.repository.AnomalyHitRepository;
import dev.brp.repository.InventoryRepository;
import dev.brp.service.impl.VaultDriftServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The detection surfaces (docs/12 §11-12 + §8 drift): wash findings carry
 * scores, not sentences; the CPI prints one geometric truth per day; the
 * vault's opinion loses to the books.
 */
@SpringBootTest(properties = "brp.chunk.governor.starter-minutes=0")
class MonetaryDetectionTest {

    @Autowired WashService wash;
    @Autowired MarketPriceService prices;
    @Autowired VaultDriftServiceImpl drift;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired MintService mint;
    @Autowired ChunkService chunks;
    @Autowired InventoryRepository inventory;
    @Autowired AnomalyHitRepository hits;

    private String guild(long seedCents) {
        String tenant = "det-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Detect Co", "DET", UUID.randomUUID().toString()));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", seedCents, "seed"),
                new JournalLine(member, "CREDIT", seedCents, "seed"))));
        return tenant;
    }

    @Test
    void roundTripRoundAmountsLookExactlyLikeWhatTheyAre() {
        String tenant = guild(1_000_000L);
        String a = "laundry-in";
        String b = "laundry-out";
        ledger.createAccount(tenant, new CreateAccountRequest(a, "PLAYER", "BRC"));
        ledger.createAccount(tenant, new CreateAccountRequest(b, "PLAYER", "BRC"));
        // A -> B -> A, round amounts, same hour: the textbook shape
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 90_000L, "sale"),
                new JournalLine(a, "CREDIT", 90_000L, "sale"))));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(a, "DEBIT", 90_000L, "resale"),
                new JournalLine(b, "CREDIT", 90_000L, "resale"))));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(b, "DEBIT", 90_000L, "resale2"),
                new JournalLine("treasury", "CREDIT", 90_000L, "resale2"))));

        wash.scan();
        var finding = wash.findings().stream()
                .filter(f -> f.tenantId().equals(tenant)).findFirst();
        assertThat(finding).isPresent();
        assertThat(finding.get().score()).isBetween(0.01, 1.0);   // the score carries the accusation

        // second scan the same week: suspicion is not repeatable business
        int before = wash.findings().size();
        wash.scan();
        assertThat(wash.findings().size()).isEqualTo(before);
    }

    @Test
    void cpiPrintsOneGeometricTruthPerDay() {
        prices.record("minecraft:emerald", 64_00L, "BRC", 1, "test-venue", "manual");
        prices.record("minecraft:diamond", 100_00L, "BRC", 1, "test-venue", "manual");
        prices.record("minecraft:emerald", 128_00L, "BRC", 1, "test-venue", "manual");   // doubled
        prices.record("minecraft:diamond", 100_00L, "BRC", 1, "test-venue", "manual");
        // single-observation items have no price MOVEMENT yet and sit out the index

        double index = prices.materializeToday();
        // equal weights: sqrt(2.0 * 1.0) * 100 = 141.42
        assertThat(index).isCloseTo(141.42, within(0.01));

        double again = prices.materializeToday();
        assertThat(again).isEqualTo(index);   // one truth per day
    }

    @Test
    void theVaultsOpinionLosesToTheBooks() {
        String tenant = guild(1_000_000L);
        mint.registerVault(tenant, "vault:drift", "minecraft:emerald");
        chunks.register(tenant, new RegisterChunkRequest("minecraft:overworld", "8,8", "u"));

        // honest mint: 640 emeralds OUT, books say 640
        mint.mint(tenant, "vault:drift", "minecraft:emerald", 640, 640_00L, "d-" + UUID.randomUUID());
        // the agent's report disagrees: physical count 600
        var snap = new dev.brp.entity.InventorySnapshotEntity(
                tenant, "vault:drift", "minecraft:emerald", 600);
        inventory.save(snap);

        List<String> drifted = drift.driftCheck();
        assertThat(drifted).anyMatch(line -> line.contains("vault:drift") && line.contains("off by -40"));
        assertThat(hits.findTop50ByOrderByIdDesc().get(0).getRuleId()).isEqualTo(904L);
    }
}
