package dev.brp;

import dev.brp.entity.AccountEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit #29 (high): the ledger's long arithmetic was bare — one oversized
 * line could wrap a balance silently. The DTO now caps a line at 1e12 cents
 * (bean validation, 400 before the service), and both summation and the
 * balance apply run on exact arithmetic, so overflow is an exception on the
 * money path, never a wrap.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LedgerAmountBoundTest {

    @Autowired MockMvc mvc;

    @Test
    void oversizedLineIsRefusedAtValidation() throws Exception {
        mvc.perform(post("/api/v1/tenants/overflow-t/journal")
                        .contentType(APPLICATION_JSON).content("""
                                {"idempotencyKey":"ovf-1","currency":"BRC","lines":[
                                  {"accountNo":"a","direction":"DEBIT","amountCents":1000000000001},
                                  {"accountNo":"b","direction":"CREDIT","amountCents":1000000000001}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void balanceApplyNeverWrapsSilently() {
        AccountEntity account = new AccountEntity("t", "vault", "TEAM", "BRC");
        account.apply(Long.MAX_VALUE);
        assertThatThrownBy(() -> account.apply(1L))
                .isInstanceOf(ArithmeticException.class);
        // the balance is intact at its ceiling — not wrapped negative
        assertThat(account.getBalanceCents()).isEqualTo(Long.MAX_VALUE);
    }
}
