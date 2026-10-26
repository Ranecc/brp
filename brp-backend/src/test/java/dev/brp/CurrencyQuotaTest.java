package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.dto.CurrencyDtos.RegisterCurrencyRequest;
import dev.brp.dto.QuotaDtos.PlacementVerdict;
import dev.brp.repository.OutboxRepository;
import dev.brp.service.CurrencyService;
import dev.brp.service.LedgerService;
import dev.brp.service.QuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Owner-configured legal tender + compute quota as the economy lever:
 * the ledger refuses unregistered currencies; machine placement is
 * licensed; chunk gluttony pages management (handling stays human).
 */
@SpringBootTest
class CurrencyQuotaTest {

    @Autowired CurrencyService currencies;
    @Autowired LedgerService ledger;
    @Autowired QuotaService quotas;
    @Autowired OutboxRepository outbox;

    @Test
    void theLedgerOnlyAcceptsRegisteredTender() {
        String code = "GL" + UUID.randomUUID().toString().substring(0, 3);
        currencies.register(new RegisterCurrencyRequest(code, "Guild Ledger", "G", 2));

        String tenant = "cur-" + UUID.randomUUID().toString().substring(0, 6);
        ledger.createAccount(tenant, new CreateAccountRequest("vault", "TEAM", code));

        // SCAMcoin was never on the registry; finance declines
        assertThatThrownBy(() -> ledger.createAccount(tenant,
                new CreateAccountRequest("scam-wallet", "PLAYER", "SCAM")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("economy.currency.unknown");
    }

    @Test
    void machinePlacementIsLicensedAndOverrunsPageManagement() {
        String tenant = "q-" + UUID.randomUUID().toString().substring(0, 6);
        quotas.setLimit(tenant, QuotaService.RESOURCE_MACHINE_SLOT, 1);

        assertThat(quotas.recordMachinePlaced(tenant, "create:crusher-1").licensed()).isTrue();
        PlacementVerdict overrun = quotas.recordMachinePlaced(tenant, "create:crusher-2");
        assertThat(overrun.licensed()).isFalse();

        // management has been notified; consequences remain a human choice
        assertThat(outbox.findAll().stream()
                .anyMatch(m -> "quota.machine.exceeded".equals(m.getEventType()))).isTrue();
        assertThat(quotas.usage(tenant).stream()
                .filter(b -> QuotaService.RESOURCE_MACHINE_SLOT.equals(b.resource()))
                .findFirst().orElseThrow().used()).isEqualTo(1);
    }

    @Test
    void chunkGluttonyOpensAnIncidentAndCCsManagement() {
        String tenant = "q-" + UUID.randomUUID().toString().substring(0, 6);
        quotas.recordChunkPerformance(tenant, "minecraft:overworld", "128,64", 999.0);
        quotas.recordChunkPerformance(tenant, "minecraft:overworld", "0,0", 0.5);

        var open = quotas.incidents(tenant);
        assertThat(open).hasSize(1);              // only the glutton, not the coast
        assertThat(open.get(0).costScore()).isEqualTo(999.0);
        assertThat(outbox.findAll().stream()
                .anyMatch(m -> "quota.chunk.incident".equals(m.getEventType()))).isTrue();
    }
}
