package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Treasury withdrawal request; lifecycle driven by ApprovalServiceImpl. */
@Entity
@Table(name = "withdrawal_requests")
public class WithdrawalRequestEntity {

    @Id
    private String id;

    private String teamId;
    private String requesterUuid;
    private long amountCents;
    private String memo;
    private String status = "PENDING";
    private Instant createdAt = Instant.now();

    protected WithdrawalRequestEntity() {}

    public WithdrawalRequestEntity(String id, String teamId, String requesterUuid,
                                   long amountCents, String memo) {
        this.id = id;
        this.teamId = teamId;
        this.requesterUuid = requesterUuid;
        this.amountCents = amountCents;
        this.memo = memo;
    }

    public String getId() { return id; }
    public String getTeamId() { return teamId; }
    public String getRequesterUuid() { return requesterUuid; }
    public long getAmountCents() { return amountCents; }
    public String getMemo() { return memo; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    private static final java.util.Map<String, java.util.Set<String>> ALLOWED =
            java.util.Map.of(
                    "PENDING", java.util.Set.of("AWAITING_UPPER_MANAGEMENT", "REJECTED",
                            "EXECUTED", "FAILED"),
                    "AWAITING_UPPER_MANAGEMENT", java.util.Set.of("EXECUTED", "FAILED", "REJECTED"));

    /** State machine, not a setter (audit D-2): terminal states are terminal. */
    public void transitionTo(String next) {
        if (!ALLOWED.getOrDefault(status, java.util.Set.of()).contains(next)) {
            throw new IllegalStateException(
                    "illegal approval transition: " + status + " -> " + next);
        }
        this.status = next;
    }
}
