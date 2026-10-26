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

    public void transitionTo(String status) {
        this.status = status;
    }
}
