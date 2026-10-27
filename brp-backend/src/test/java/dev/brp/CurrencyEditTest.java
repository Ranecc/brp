package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.dto.CurrencyDtos.CurrencyResponse;
import dev.brp.dto.CurrencyDtos.RegisterCurrencyRequest;
import dev.brp.dto.CurrencyDtos.UpdateCurrencyRequest;
import dev.brp.service.CurrencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The registry's front door: mint with a bearer item, rebrand without a
 * new identity. Renaming a currency is marketing; re-identifying one is
 * fraud, and there is no endpoint for fraud.
 */
@SpringBootTest
class CurrencyEditTest {

    @Autowired CurrencyService currencies;

    @Test
    void registersWithBearerItemAndPatchesDisplayFields() {
        String code = "PXT" + System.nanoTime() % 100;
        CurrencyResponse minted = currencies.register(new RegisterCurrencyRequest(
                code, "Pixel Token", "px", 2, "minecraft:emerald"));
        assertThat(minted.bearerItemId()).isEqualTo("minecraft:emerald");

        CurrencyResponse rebranded = currencies.update(code,
                new UpdateCurrencyRequest("Pixel Token v2", "p2", null));
        assertThat(rebranded.name()).isEqualTo("Pixel Token v2");
        assertThat(rebranded.symbol()).isEqualTo("p2");
        assertThat(rebranded.bearerItemId()).isNull();   // null = pure ledger currency again
        assertThat(rebranded.code()).isEqualTo(code);   // the code remains the code
    }

    @Test
    void unknownCodesAreRejectedForEditingToo() {
        assertThatThrownBy(() -> currencies.update("NOPE",
                new UpdateCurrencyRequest("Ghost Buck", "g", null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void duplicateRegistrationIsAReplayNotAMintAndTradabilitySurvivesRebrand() {
        String code = "DUP" + System.nanoTime() % 100;
        CurrencyResponse first = currencies.register(new RegisterCurrencyRequest(code, "One", "1", 0, null));
        // @Idempotent(key = code): a re-mint with the same code replays the
        // stored answer — the first answer is the only answer.
        CurrencyResponse again = currencies.register(new RegisterCurrencyRequest(code, "Two", "2", 0, null));
        assertThat(again.name()).isEqualTo(first.name()).isEqualTo("One");

        currencies.update(code, new UpdateCurrencyRequest("One Prime", "1p", "minecraft:diamond"));
        assertThat(currencies.isTradable(code)).isTrue();
    }
}
