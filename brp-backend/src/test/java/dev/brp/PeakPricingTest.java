package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.PurchaseCreditsRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.service.ChunkService;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.entity.TeamMemberRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Time-of-use pricing (docs/10 R-3b): the always-peak window makes the
 * surcharge observable without mocking a clock — integral cents only.
 */
@SpringBootTest(properties = {
        "brp.chunk.billing.cents-per-minute=10",
        "brp.chunk.billing.peak-start-hour=0",
        "brp.chunk.billing.peak-end-hour=24",      // wrap window: always peak
        "brp.chunk.billing.peak-surcharge-percent=100",
        "brp.chunk.governor.starter-minutes=0"})
class PeakPricingTest {

    @Autowired ChunkService chunks;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;

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

        ChunkSlotResponse bought = chunks.purchaseCredits(tenant, "minecraft:overworld", "1,2",
                new PurchaseCreditsRequest(100L, "peak-" + UUID.randomUUID()));
        assertThat(bought.creditMinutes()).isEqualTo(100);
        // base 100*10 = 1000 cents, surcharge 100% = +1000 -> 2000 total
        assertThat(treasury(tenant)).isEqualTo(1_000_000L - 2_000L);
    }
}
