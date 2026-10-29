package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A queued policy decision (docs/12 §9): re-peg votes land here; the OWNER
 * ladder decides. No third governance channel — this queue IS the existing
 * channel's monetary desk.
 */
@Entity
@Table(name = "policy_requests")
public class PolicyRequestEntity {

    public static final String REPEG_VOTE = "repeg-vote";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kind;
    private String payloadJson;
    private String status = PENDING;
    private String requestedBy;
    private String decidedBy;
    private Instant createdAt = Instant.now();
    private Instant decidedAt;

    protected PolicyRequestEntity() {}

    public PolicyRequestEntity(String kind, String payloadJson, String requestedBy) {
        this.kind = kind;
        this.payloadJson = payloadJson;
        this.requestedBy = requestedBy;
    }

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }

    public void decide(String status, String decidedBy) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = Instant.now();
    }
}
