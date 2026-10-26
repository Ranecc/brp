package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.EconomyDtos.EconomyDailyResponse;
import dev.brp.service.LedgerService;
import dev.brp.service.EconomyReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Economy observability from the ledger alone (docs/03 sections 2-3).
 * Uses a dedicated currency so the shared test context cannot skew it.
 */
@SpringBootTest
class EconomyReportTest {

    @Autowired EconomyReportService economy;
    @Autowired LedgerService ledger;

    private String account(String tenant, String no, String type, String currency) {
        ledger.createAccount(tenant, new CreateAccountRequest(no, type, currency));
        return no;
    }

    private void post(String tenant, String currency, String debitAcc, String creditAcc, long amount) {
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), currency, List.of(
                new JournalLine(debitAcc, "DEBIT", amount, null),
                new JournalLine(creditAcc, "CREDIT", amount, null))));
    }

    @Test
    void massEmissionAndVelocityComeStraightFromTheJournal() {
        String currency = "OBS" + UUID.randomUUID().toString().substring(0, 4);
        String tenant = "econ-" + UUID.randomUUID().toString().substring(0, 6);
        account(tenant, "faucet", "SYSTEM_FAUCET", currency);
        account(tenant, "sink", "SYSTEM_SINK", currency);
        account(tenant, "alice", "PLAYER", currency);
        account(tenant, "guild", "TEAM", currency);

        // faucet prints 1000 into circulation
        post(tenant, currency, "alice", "faucet", 1000);
        // alice buys from the guild for 300 (real trade, no emission)
        post(tenant, currency, "guild", "alice", 300);

        EconomyDailyResponse today = economy.materializeToday(currency);

        assertThat(today.moneyMassCents()).isEqualTo(1000);   // M: held balances
        assertThat(today.netEmissionCents()).isEqualTo(1000); // only the faucet move
        assertThat(today.tradedVolumeCents()).isEqualTo(1300); // 1000 + 300
        assertThat(today.velocity()).isCloseTo(1.3, within(1e-9));
    }

    @Test
    void sinksShrinkTheMassWithoutInflatingVolume() {
        String currency = "OBS" + UUID.randomUUID().toString().substring(0, 4);
        String tenant = "econ-" + UUID.randomUUID().toString().substring(0, 6);
        account(tenant, "faucet", "SYSTEM_FAUCET", currency);
        account(tenant, "sink", "SYSTEM_SINK", currency);
        account(tenant, "bob", "PLAYER", currency);

        post(tenant, currency, "bob", "faucet", 500);
        post(tenant, currency, "sink", "bob", 200);   // taxed into the sink

        EconomyDailyResponse today = economy.materializeToday(currency);

        assertThat(today.moneyMassCents()).isEqualTo(300);   // 500 printed - 200 burned
        assertThat(today.netEmissionCents()).isEqualTo(300);
        assertThat(today.tradedVolumeCents()).isEqualTo(500); // only held-side debits count
    }
}
