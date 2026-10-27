package dev.brp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Owner-configured currency registry DTOs. */
public final class CurrencyDtos {

    private CurrencyDtos() {}

    public record RegisterCurrencyRequest(
            @NotBlank @Size(max = 8) String code,
            @NotBlank String name,
            @NotBlank String symbol,
            @Min(0) @Max(8) int decimals,
            String bearerItemId) {

        /** Pure ledger currency shorthand: no bearer item, just a promise. */
        public RegisterCurrencyRequest(String code, String name, String symbol, int decimals) {
            this(code, name, symbol, decimals, null);
        }
    }

    /**
     * Display-level edits only. No code field by design: renaming a currency
     * is marketing, re-identifying one is fraud. Null bearerItemId = back to
     * pure ledger currency.
     */
    public record UpdateCurrencyRequest(
            @NotBlank String name,
            @NotBlank String symbol,
            String bearerItemId) {}

    public record CurrencyResponse(
            String code, String name, String symbol, int decimals,
            String status, String bearerItemId) {}
}
