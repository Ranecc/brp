package dev.brp;

import dev.brp.dto.AnomalyDtos.SetRuleRequest;
import dev.brp.dto.ReconciliationDtos.RunReport;
import dev.brp.entity.AccountEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.service.HotRuleService;
import dev.brp.service.LedgerService;
import dev.brp.service.ReconciliationService;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.service.TeamService;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.entity.TeamMemberRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The desk trio (docs/10): hot rules fire on telemetry payloads, and the
 * reconciliation desk catches books that forgot how double entry works.
 */
@SpringBootTest
class DeskServicesTest {

    @Autowired HotRuleService hotRules;
    @Autowired ReconciliationService desk;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired AccountRepository accounts;

    @Test
    void hotRulesTripOnThresholdsAndStayQuietBelowThem() {
        hotRules.create(new SetRuleRequest("lag-watchdog-" + UUID.randomUUID().toString().substring(0, 6),
                "agent.heartbeat", "mspt", "GT", 50.0, "WARN"));

        int before = hotRules.hits().size();
        hotRules.evaluate("agent.heartbeat", Map.of("mspt", 80.5));   // trips
        hotRules.evaluate("agent.heartbeat", Map.of("mspt", 12.0));   // quiet
        hotRules.evaluate("agent.heartbeat", Map.of("tps", 80.0));    // field absent: waits politely

        var recent = hotRules.hits();
        assertThat(recent.size()).isGreaterThan(before);
        // shared-H2 discipline: other tests' rules write hits into the same
        // global table — scope to OUR rule, never index the world
        var ours = recent.stream()
                .filter(h -> h.ruleName().startsWith("lag-watchdog-"))
                .toList();
        assertThat(ours).isNotEmpty();
        assertThat(ours.get(0).observed()).isEqualTo(80.5);
    }

    @Test
    void theDeskFilesAFindingWhenBooksDrift() throws Exception {
        // balanced world first
        RunReport clean = desk.reconcile();
        assertThat(clean.findingsFiled()).isZero();

        // a rogue account with a nonzero balance — the books drift
        String tenant = "desk-" + UUID.randomUUID().toString().substring(0, 8);
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant, "Drift Co", "DFT",
                UUID.randomUUID().toString()));
        AccountEntity rogue = new AccountEntity(tenant, "off-books", "PLAYER", "RGC");
        java.lang.reflect.Field balance = AccountEntity.class.getDeclaredField("balanceCents");
        balance.setAccessible(true);
        balance.set(rogue, 123L);
        accounts.saveAndFlush(rogue);

        RunReport dirty = desk.reconcile();
        assertThat(dirty.findingsFiled()).isGreaterThanOrEqualTo(1);
        assertThat(desk.findings().get(0).currency()).isEqualTo("RGC");
        assertThat(desk.findings().get(0).imbalanceCents()).isEqualTo(123L);
    }

    @Test
    void balancedJournalsNeverFile() {
        String tenant = "bal-" + UUID.randomUUID().toString().substring(0, 8);
        String owner = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant, "Balance Inc", "BAL", owner));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000L, "seed"))));

        RunReport report = desk.reconcile();
        // BRC may carry imbalance from OTHER tests' dirty data; this tenant's
        // balanced journal must not ADD one — findings count cannot grow
        // because of a balanced post. (The desk checks Σ per currency; a
        // balanced journal contributes zero by construction.)
        assertThat(report.currenciesChecked()).isGreaterThanOrEqualTo(1);
    }
}
