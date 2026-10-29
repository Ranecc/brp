package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CC:T desk (docs/13 §10): a scoped token, one read-only poll, and the
 * desk shut entirely when no token is configured. Computers get the state;
 * the state never gets a computer's writes.
 */
@SpringBootTest(properties = {
        "brp.cc.token=cc-test-secret",
        "brp.dr.mspt-budget=9999"}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CcStateTest {

    @Autowired TestRestTemplate rest;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired LedgerService ledger;

    private String fundedTenant() {
        String tenant = "cc-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Turtle Co", "TRT", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        return tenant;
    }

    private ResponseEntity<Map> get(String tenant, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return rest.exchange("/api/v1/cc/" + tenant + "/state", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void statePollReportsGridCreditAndWindows() {
        String tenant = fundedTenant();
        ResponseEntity<Map> response = get(tenant, "cc-test-secret");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body.get("tenant")).isEqualTo(tenant);
        assertThat(body.get("creditStatus")).isEqualTo("GOOD");
        assertThat(body.get("quotaLimit")).isEqualTo(0);
        assertThat((List<?>) body.get("windows")).isEmpty();
        assertThat(body.get("schedulable")).isEqualTo(true);   // budget 9999: the grid always has room
        assertThat((Integer) body.get("multiplierPct")).isGreaterThan(0);
    }

    @Test
    void wrongTokenIsRefused() {
        String tenant = fundedTenant();
        assertThat(get(tenant, "wrong-token").getStatusCode().value()).isEqualTo(400);
        assertThat(get(tenant, null).getStatusCode().value()).isEqualTo(400);
    }
}
