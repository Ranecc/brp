package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ApprovalDtos.Decision;
import dev.brp.dto.ApprovalDtos.SubmitWithdrawalRequest;
import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.ApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The approval labyrinth, end to end: rubber stamp for pocket change,
 * upper management for the big asks, saga execution against the real ledger,
 * and corporate cardio for invalid nudges.
 */
@SpringBootTest
class ApprovalFlowTest {

    @Autowired ApprovalService approvals;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;

    private record Guild(String teamId, String owner, String member) {}

    private Guild guild() {
        String id = "g-" + UUID.randomUUID().toString().substring(0, 8);
        String owner = UUID.randomUUID().toString();
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), id, "Initech", "INI", owner));
        memberRepo.save(new TeamMemberRow(id, member, "MEMBER"));
        ledger.createAccount(id, new CreateAccountRequest(member, "PLAYER", "BRC"));
        // fund the treasury: faucet DEBIT +50000, treasury CREDIT +50000
        ledger.post(id, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 50_000L, "seed"),
                new JournalLine(member, "CREDIT", 50_000L, "seed"))));
        return new Guild(id, owner, member);
    }

    private long balance(String teamId, String accountNo) {
        return ledger.listAccounts(teamId).stream()
                .filter(a -> a.accountNo().equals(accountNo))
                .findFirst().orElseThrow().balanceCents();
    }

    @Test
    void settledRequestsCannotBeRelitigated() {
        Guild g = guild();
        var executed = approvals.submitWithdrawalRequest(new SubmitWithdrawalRequest(
                UUID.randomUUID().toString(), g.teamId(), g.member(), 500L, "petty cash"));
        assertThat(executed.status()).isEqualTo("EXECUTED");   // rubber stamp at submit

        // EXECUTED -> REJECTED is not a verdict, it is vandalism (audit #5)
        assertThatThrownBy(() -> approvals.reject(executed.id(), new Decision(g.owner(), "undo")))
                .isInstanceOf(ConflictException.class);
        // and double-approve keeps its guard too
        assertThatThrownBy(() -> approvals.approve(executed.id(), new Decision(g.owner(), "again")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void pocketChangeGetsTheRubberStampAndExecutesThroughTheSaga() {
        Guild g = guild();
        var resp = approvals.submitWithdrawalRequest(new SubmitWithdrawalRequest(
                UUID.randomUUID().toString(), g.teamId(), g.member(), 500L, "coffee budget"));

        assertThat(resp.status()).isEqualTo("EXECUTED");
        assertThat(balance(g.teamId(), "treasury")).isEqualTo(50_000L - 500L);
        assertThat(balance(g.teamId(), "escrow")).isZero();      // froze then settled
        assertThat(balance(g.teamId(), "expense-sink")).isEqualTo(500L);
    }

    @Test
    void bigMoneyGoesToUpperManagementThenExecutes() {
        Guild g = guild();
        var resp = approvals.submitWithdrawalRequest(new SubmitWithdrawalRequest(
                UUID.randomUUID().toString(), g.teamId(), g.member(), 20_000L, "redstone fund"));
        assertThat(resp.status()).isEqualTo("AWAITING_UPPER_MANAGEMENT");

        // a plain MEMBER cannot touch upper-management money
        assertThatThrownBy(() -> approvals.approve(resp.id(),
                new Decision(g.member(), "nice try")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("approval.upper.management.required");

        // the OWNER is upper management; the buck stops here
        var approved = approvals.approve(resp.id(),
                new Decision(g.owner(), "the buck stops here"));
        assertThat(approved.status()).isEqualTo("EXECUTED");
        assertThat(balance(g.teamId(), "treasury")).isEqualTo(50_000L - 20_000L);
        assertThat(balance(g.teamId(), "expense-sink")).isEqualTo(20_000L);
    }

    @Test
    void middleMoneyNeedsOfficerApproval() {
        Guild g = guild();
        var resp = approvals.submitWithdrawalRequest(new SubmitWithdrawalRequest(
                UUID.randomUUID().toString(), g.teamId(), g.member(), 5_000L, "server rental"));
        assertThat(resp.status()).isEqualTo("PENDING"); // 1000 < 5000 < 10000

        assertThatThrownBy(() -> approvals.approve(resp.id(), new Decision(g.member(), "self-approve")))
                .isInstanceOf(Exception.class);

        var rejected = approvals.reject(resp.id(), new Decision(g.owner(), "circle back next quarter"));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(balance(g.teamId(), "treasury")).isEqualTo(50_000L); // untouched
    }

    @Test
    void duplicateSubmissionIsIdempotentAndNudgingHasLimits() {
        Guild g = guild();
        String key = UUID.randomUUID().toString();
        SubmitWithdrawalRequest req = new SubmitWithdrawalRequest(
                key, g.teamId(), g.member(), 5_000L, "stapler budget");

        WithdrawalRequestResponse first = approvals.submitWithdrawalRequest(req);
        WithdrawalRequestResponse replay = approvals.submitWithdrawalRequest(req);
        assertThat(replay.id()).isEqualTo(first.id());

        var nudge = approvals.perMyLastEmail(first.id());
        assertThat(nudge.remindersSent()).isEqualTo(1);

        // close the request, then find out nudging a settled form is cardio
        approvals.reject(first.id(), new Decision(g.owner(), "denied with love"));
        assertThatThrownBy(() -> approvals.perMyLastEmail(first.id()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void outsidersGetHrRejected() {
        Guild g = guild();
        assertThatThrownBy(() -> approvals.submitWithdrawalRequest(new SubmitWithdrawalRequest(
                UUID.randomUUID().toString(), g.teamId(),
                UUID.randomUUID().toString(), 500L, "hostile takeover")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("approval.hr.rejected");
    }
}
