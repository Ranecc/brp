package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.PegStateEntity;
import dev.brp.entity.PolicyRequestEntity;
import dev.brp.repository.AnomalyHitRepository;
import dev.brp.repository.PolicyRequestRepository;
import dev.brp.repository.VaultMovementRepository;
import dev.brp.saga.SagaEngine;
import dev.brp.service.GreshamService;
import dev.brp.service.LedgerService;
import dev.brp.service.MintService;
import dev.brp.service.PegService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money creation and its anchors (docs/12 sections 8-10): the saga books
 * the faucet first and compensates in reverse; the band files its own
 * votes; one rail monopolizing payments is a declared crisis.
 */
@SpringBootTest
class MintPegGreshamTest {

    @Autowired MintService mint;
    @Autowired PegService peg;
    @Autowired GreshamService gresham;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired VaultMovementRepository movements;
    @Autowired PolicyRequestRepository requests;
    @Autowired AnomalyHitRepository hits;

    private String guild(long seedCents) {
        String tenant = "mpg-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Mint Co", "MNT", UUID.randomUUID().toString()));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", seedCents, "seed"),
                new JournalLine(member, "CREDIT", seedCents, "seed"))));
        return tenant;
    }

    private long treasury(String tenantId) {
        return ledger.listAccounts(tenantId).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }

    @Test
    void mintBooksTheFaucetThenDispensesAndBothLegsAgree() {
        String tenant = guild(1);   // the ledger refuses zero-amount seeds; fine, one cent of pride
        mint.registerVault(tenant, "vault:central", "minecraft:emerald");
        String correlation = "m-" + UUID.randomUUID();

        SagaEngine.Outcome out = mint.mint(tenant, "vault:central",
                "minecraft:emerald", 640, 640_00L, correlation);

        assertThat(out.success()).isTrue();
        assertThat(treasury(tenant)).isEqualTo(640_00L + 1);   // the faucet leg landed
        assertThat(movements.findByTenantIdAndContainerRef(tenant, "vault:central"))
                .anyMatch(m -> "OUT".equals(m.getDirection())
                        && m.getCorrelation().equals(correlation));
    }

    @Test
    void failedDispenseCompensatesTheFaucetAndTheBooksComeBack() {
        String tenant = guild(1_000L);
        String correlation = "m-" + UUID.randomUUID();   // vault NOT registered: dispense will fail

        SagaEngine.Outcome out = mint.mint(tenant, "vault:nowhere",
                "minecraft:emerald", 64, 500_00L, correlation);

        assertThat(out.success()).isFalse();
        assertThat(out.failedStep()).isEqualTo("dispense");
        assertThat(out.compensated()).containsExactly("book-faucet");
        assertThat(treasury(tenant)).isEqualTo(1_000L);   // the books came back, to the cent
    }

    @Test
    void twoMissesFileOneVoteAndApprovalMovesTheAnchorAtomically() {
        peg.observe(PegStateEntity.QUOTA, 12.0);   // out of band, streak 1
        assertThat(requests.findByStatusOrderByIdAsc(PolicyRequestEntity.PENDING)).isEmpty();

        peg.observe(PegStateEntity.QUOTA, 12.0);   // streak 2: the machine files
        var pending = requests.findByStatusOrderByIdAsc(PolicyRequestEntity.PENDING);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getKind()).isEqualTo(PolicyRequestEntity.REPEG_VOTE);

        long inBandFirst = pending.get(0).getId();
        peg.decide(inBandFirst, true, "owner-1");

        var leg = peg.legs().stream().filter(l -> l.getLeg().equals(PegStateEntity.QUOTA))
                .findFirst().orElseThrow();
        assertThat(leg.getRate()).isEqualTo(12.0);
        org.assertj.core.data.Offset<Double> e = org.assertj.core.data.Offset.offset(1e-9);
        assertThat(leg.getBandLow()).isCloseTo(11.4, e);   // the band moved WITH the anchor
        peg.observe(PegStateEntity.QUOTA, 12.0);
        assertThat(leg.getOutOfBandStreak()).isZero();
    }

    @Test
    void oneLegMonopolizingPaymentsIsACrisis() {
        String tenant = guild(1_000_000L);
        String counterparty = "cp-" + UUID.randomUUID().toString().substring(0, 8);
        ledger.createAccount(tenant, new CreateAccountRequest(counterparty, "PLAYER", "BRC"));
        // rails ride the DEBIT legs (paid-in volume); every journal still balances
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 20_000L, "rail:emerald:trade"),
                new JournalLine(counterparty, "CREDIT", 20_000L, "rail:emerald:trade"))));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 100L, "rail:quota:trade"),
                new JournalLine(counterparty, "CREDIT", 100L, "rail:quota:trade"))));

        Map<String, Double> shares = gresham.scan();
        assertThat(shares.get("emerald")).isGreaterThan(0.95);
        assertThat(hits.findTop50ByOrderByIdDesc().get(0).getRuleId()).isEqualTo(903L);
    }
}
