package dev.brp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** Approval workflow DTOs — the unadulterated corporate labyrinth. */
public final class ApprovalDtos {

    private ApprovalDtos() {}

    public record SubmitWithdrawalRequest(
            @NotBlank String idempotencyKey,
            @NotBlank String teamId,
            @NotBlank String requesterUuid,
            @NotNull @Positive Long amountCents,
            String memo) {}

    public record Decision(@NotBlank String approverUuid, String note) {}

    public record WithdrawalRequestResponse(
            String id, String teamId, String requesterUuid, long amountCents,
            String memo, String status, Instant createdAt) {}

    public record NudgeAck(String requestId, int remindersSent, String corporateBuzzword) {}
}
