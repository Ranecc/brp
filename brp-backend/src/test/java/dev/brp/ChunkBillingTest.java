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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The prepaid meter: starter credits on the first slot, money-path purchases
 * through the ledger (charged once, granted once), and the meter burning
 * credit down to the strike.
 */
@SpringBootTest(properties = "brp.chunk.billing.cents-per-minute=10")
class ChunkBillingTest {

    @Autowired ChunkService chunks;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;

    private record Guild(String teamId, String member) {}

    private Guild guild() {
        String id = "cb-" + UUID.randomUUID().toString().substring(0, 8);
        String owner = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), id, "Cubes R Us", "CUB", owner));
        memberRepo.save(new TeamMemberRow(id, member, "MEMBER"));
        ledger.createAccount(id, new CreateAccountRequest(member, "PLAYER", "BRC"));
        // fund the treasury from the member seed: DEBIT is +, CREDIT is -
        // (this ledger's convention — the code is canon, the comments flatter)
        ledger.post(id, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 10_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 10_000_000L, "seed"))));
        return new Guild(id, member);
    }

    private long treasury(String teamId) {
        return ledger.listAccounts(teamId).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }

    @Test
    void firstSlotShipsStarterCreditsAndSecondSlotsDoNot() {
        Guild g = guild();
        ChunkSlotResponse first = chunks.register(g.teamId(),
                new RegisterChunkRequest("minecraft:overworld", "0,1", "u"));
        assertThat(first.creditMinutes()).isEqualTo(1440);   // the welcome package

        ChunkSlotResponse second = chunks.register(g.teamId(),
                new RegisterChunkRequest("minecraft:overworld", "0,2", "u"));
        assertThat(second.creditMinutes()).isZero();          // welfare is a one-time offer
    }

    @Test
    void purchaseChargesOnceGrantsOnceAndReplaysForFree() {
        Guild g = guild();
        chunks.register(g.teamId(), new RegisterChunkRequest("minecraft:overworld", "1,1", "u"));
        String idem = "buy-" + UUID.randomUUID();

        ChunkSlotResponse bought = chunks.purchaseCredits(g.teamId(),
                "minecraft:overworld", "1,1", new PurchaseCreditsRequest(100L, idem));
        assertThat(bought.creditMinutes()).isEqualTo(1440 + 100);
        assertThat(treasury(g.teamId())).isEqualTo(10_000_000L - 100 * 10);

        ChunkSlotResponse replay = chunks.purchaseCredits(g.teamId(),
                "minecraft:overworld", "1,1", new PurchaseCreditsRequest(100L, idem));
        assertThat(replay.creditMinutes()).isEqualTo(1540);   // no double minutes
        assertThat(treasury(g.teamId())).isEqualTo(10_000_000L - 100 * 10);   // no double charge

        long sink = ledger.listAccounts(g.teamId()).stream()
                .filter(a -> a.accountNo().equals("chunk-sink"))
                .findFirst().orElseThrow().balanceCents();
        assertThat(sink).isEqualTo(100 * 10);   // the meter keeps what it took
    }

    @Test
    void theMeterBurnsCreditToZeroAndTheMachinesStrike() {
        Guild g = guild();
        chunks.register(g.teamId(), new RegisterChunkRequest("minecraft:overworld", "2,2", "u"));
        chunks.accrueMinutes(g.teamId(), "minecraft:overworld", "2,2", 1500);

        ChunkSlotResponse slot = chunks.slots(g.teamId()).get(0);
        assertThat(slot.creditMinutes()).isEqualTo(-60);      // debt is visible, not hidden
        var verdict = chunks.governor(g.teamId()).get(0);
        assertThat(verdict.verdict()).isEqualTo(ChunkService.VERDICT_HALT);
        assertThat(verdict.reason()).isEqualTo("NO_CREDIT");
    }

    @Test
    void theMeterIgnoresChunksItDoesNotKnow() {
        assertThatCode(() -> chunks.accrueMinutes("ghost-tenant",
                "minecraft:overworld", "9,9", 60)).doesNotThrowAnyException();
    }
}
