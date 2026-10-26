package dev.brp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The console renders: classic admin chrome, creeper accents, real numbers. */
@SpringBootTest
@AutoConfigureMockMvc
class PortalSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired dev.brp.service.ApprovalService portalApprovals;
    @Autowired dev.brp.service.TeamService portalTeam;
    @Autowired dev.brp.service.LedgerService portalLedger;
    @Autowired dev.brp.repository.TeamMemberRepository portalMembers;

    @Test
    void dashboardRendersWithEnterpriseChrome() throws Exception {
        mvc.perform(get("/portal"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("货币总量 M")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("TPS")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("cover sheet")));
    }

    private dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse seedPending() {
        String team = "pt-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        String owner = java.util.UUID.randomUUID().toString();
        String member = java.util.UUID.randomUUID().toString();
        portalTeam.create(new dev.brp.dto.TeamDtos.CreateTeamRequest(
                java.util.UUID.randomUUID().toString(), team, "Portal Test", "PT", owner));
        portalMembers.save(new dev.brp.entity.TeamMemberRow(team, member, "MEMBER"));
        return portalApprovals.submitWithdrawalRequest(
                new dev.brp.dto.ApprovalDtos.SubmitWithdrawalRequest(
                        java.util.UUID.randomUUID().toString(), team, member, 5_000L, "seed"));
    }

    @Test
    void approvalsInboxRenders() throws Exception {
        seedPending();   // buttons only render with paperwork on the desk
        mvc.perform(get("/portal/approvals"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("corporate labyrinth")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("批准")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(containsString("驳回")));
    }

    @org.junit.jupiter.api.Test
    void consoleVerdictFlowExecutesThroughTheService() throws Exception {
        // stage a PENDING request (middle bracket: above rubber stamp, below upper management)
        String team = "pt-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        String owner = java.util.UUID.randomUUID().toString();
        String member = java.util.UUID.randomUUID().toString();
        portalTeam.create(new dev.brp.dto.TeamDtos.CreateTeamRequest(
                java.util.UUID.randomUUID().toString(), team, "Portal Test", "PT", owner));
        portalMembers.save(new dev.brp.entity.TeamMemberRow(team, member, "MEMBER"));
        var req = portalApprovals.submitWithdrawalRequest(
                new dev.brp.dto.ApprovalDtos.SubmitWithdrawalRequest(
                        java.util.UUID.randomUUID().toString(), team, member, 5_000L, "portal test"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/portal/approvals/" + req.id() + "/approve")
                        .param("approverUuid", owner)
                        .param("note", "console stamp"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/portal/approvals"));

        // rubber meets road: the saga executed through the real ledger
        org.assertj.core.api.Assertions.assertThat(
                        portalLedger.listAccounts(team).stream()
                                .filter(a -> a.accountNo().equals("expense-sink"))
                                .findFirst().orElseThrow().balanceCents())
                .isEqualTo(5_000L);
    }
}
