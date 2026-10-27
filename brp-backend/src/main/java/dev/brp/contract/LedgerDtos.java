package dev.brp.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Ledger API DTOs (contract: brp-contract/openapi.yaml). */
public final class LedgerDtos {

    private LedgerDtos() {}

    public record CreateAccountRequest(
            @NotBlank String accountNo,
            @NotBlank String accountType,   // PLAYER | TEAM | SYSTEM_SINK | SYSTEM_FAUCET | ESCROW
            @NotBlank String currency) {}

    public record AccountDto(long id, String accountNo, String accountType,
                             String currency, long balanceCents) {}

    public record JournalLine(
            @NotBlank String accountNo,
            @NotBlank String direction,     // DEBIT | CREDIT
            @NotNull @Positive Long amountCents,
            String memo) {}

    public record PostJournalRequest(
            @NotBlank @Size(max = 64) String idempotencyKey,   // V9 column width
            @NotBlank String currency,
            @NotEmpty @Valid List<JournalLine> lines) {}

    /** replayed=true means the idempotency key hit and the original response snapshot is replayed. */
    public record PostJournalResponse(String journalId, boolean replayed, List<AccountDto> affectedAccounts) {}
}
