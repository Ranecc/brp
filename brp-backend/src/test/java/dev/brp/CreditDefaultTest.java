package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.LoadDtos.CreditStatusResponse;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.CreditLineEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.CreditService;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The end of the line (docs/13 §6): with a zero-day grace, RESTRICTED
 * defaults on the very next desk run, and only a board decision (REINSTATE)
 * walks it back — to WATCH, not to GOOD. Own context because the grace is
 * the knob; the cutoff mechanics live in {@link CreditCutoffTest}.
 */
@SpringBootTest(properties = "brp.credit.grace-days=0")
class CreditDefaultTest {

    @Autowired CreditService credit;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired dev.brp.repository.CreditAccrualRunRepository runs;

    @org.springframework.beans.factory.annotation.Autowired java.time.Clock clock;

    private boolean deskRun() {
        runs.deleteGuard(java.time.LocalDate.now(clock));
        return credit.runDailyDesk();
    }

    private String overdrawnTenant(long overdraft, long limit) {
        String tenant = "cd-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Default Co", "DFT", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        credit.setLimit(tenant, limit);
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(member, "DEBIT", 1_000_000L + overdraft, "spend"),
                new JournalLine("treasury", "CREDIT", 1_000_000L + overdraft, "spend"))));
        return tenant;
    }

    @Test
    void zeroGraceDefaultsOnTheNextDeskRun() {
        String tenant = overdrawnTenant(50_000L, 50_000L);

        deskRun();   // sweep -> RESTRICTED, grace already burned
        assertThat(credit.status(tenant).status()).isEqualTo(CreditLineEntity.DEFAULTED);

        // reinstate clears the DEFAULT; the machine then re-runs from
        // utilization — still maxed out means RESTRICTED again, honestly
        CreditStatusResponse after = credit.reinstate(tenant);
        assertThat(after.status()).isEqualTo(CreditLineEntity.RESTRICTED);
        assertThat(credit.events(tenant))
                .anyMatch(e -> e.getMemo() != null && e.getMemo().contains("REINSTATE"));
    }

    @Test
    void reinstateOnlyAnswersForTheDefaulted() {
        String tenant = overdrawnTenant(10_000L, 1_000_000L);
        deskRun();   // drawn 10k of a 1M line: GOOD
        assertThat(credit.status(tenant).status()).isEqualTo(CreditLineEntity.GOOD);
        assertThatThrownBy(() -> credit.reinstate(tenant))
                .isInstanceOf(dev.brp.common.BadRequestException.class)
                .hasMessageContaining("credit.not.defaulted");
    }
}
