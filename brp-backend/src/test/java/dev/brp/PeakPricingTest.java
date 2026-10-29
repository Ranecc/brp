package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.PurchaseCreditsRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.dto.LoadDtos.AddTariffWindowRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.TariffWindowRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.ChunkService;
import dev.brp.service.LedgerService;
import dev.brp.service.TariffService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Time-of-use pricing (docs/10 R-3b, docs/13 section 4): the always-peak
 * window makes the surcharge observable without mocking a clock. Since the
 * refactor onto the tariff TABLE, the window is published through the API
 * (version 1, cleaned up below) — the legacy config keys only seed version 0,
 * and a config-seeded always-peak would leak into every other test context
 * sharing the H2. Shared-state discipline wins again.
 */
@SpringBootTest(properties = {
        "brp.chunk.billing.cents-per-minute=10",
        "brp.chunk.governor.starter-minutes=0"})
class PeakPricingTest {

    @Autowired ChunkService chunks;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired TariffService tariff;
    @Autowired TariffWindowRepository tariffRows;

    private final List<Long> addedWindows = new ArrayList<>();

    @AfterEach
    void cleanup() {
        tariffRows.deleteAllById(addedWindows);
        addedWindows.clear();
    }

    private void fund(String tenant) {
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Peak Co", "PEK", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
    }

    private long treasury(String teamId) {
        return ledger.listAccounts(teamId).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }

    @Test
    void peakHoursCostDouble() {
        String tenant = "pk-" + UUID.randomUUID().toString().substring(0, 8);
        fund(tenant);
        chunks.register(tenant, new RegisterChunkRequest("minecraft:overworld", "1,2", "u"));

        var window = tariff.addWindow(new AddTariffWindowRequest(
                -1, 0, 24 * 60, 200, "PEAK"));   // wrap window: always peak
        addedWindows.add(window.id());
        assertThat(tariff.multiplierPctAt(java.time.Instant.now())).isEqualTo(200);

        ChunkSlotResponse bought = chunks.purchaseCredits(tenant, "minecraft:overworld", "1,2",
                new PurchaseCreditsRequest(100L, "peak-" + UUID.randomUUID()));
        assertThat(bought.creditMinutes()).isEqualTo(100);
        // base 100*10 = 1000 cents, tariff 200% -> 2000 total
        assertThat(treasury(tenant)).isEqualTo(1_000_000L - 2_000L);

        // window gone -> par pricing again, before @AfterEach even runs
        tariffRows.deleteAllById(addedWindows);
        addedWindows.clear();
        assertThat(tariff.multiplierPctAt(java.time.Instant.now())).isEqualTo(100);
    }
}
