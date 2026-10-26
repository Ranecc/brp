package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Append-only audit of who did what to a withdrawal request. */
@Entity
@Table(name = "approval_actions")
public class ApprovalActionRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String requestId;
    private String actorUuid;
    private String action;
    private String note;
    private Instant createdAt = Instant.now();

    protected ApprovalActionRow() {}

    public ApprovalActionRow(String requestId, String actorUuid, String action, String note) {
        this.requestId = requestId;
        this.actorUuid = actorUuid;
        this.action = action;
        this.note = note;
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public String getActorUuid() { return actorUuid; }
    public String getAction() { return action; }
    public String getNote() { return note; }
}
