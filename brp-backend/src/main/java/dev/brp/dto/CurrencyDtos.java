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
            @Min(0) @Max(8) int decimals) {}

    public record CurrencyResponse(
            String code, String name, String symbol, int decimals, String status) {}
}
