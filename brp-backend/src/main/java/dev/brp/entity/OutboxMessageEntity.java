package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Transactional outbox row: written in the work transaction, delivered later. */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;
    private String eventType;
    private String payload;
    private String status = "PENDING";
    private int attempts;
    private Instant createdAt = Instant.now();
    private Instant deliveredAt;
    private Instant updatedAt = Instant.now();

    protected OutboxMessageEntity() {}

    public OutboxMessageEntity(String eventId, String eventType, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }

    public void delivered() {
        this.status = "DELIVERED";
        this.attempts++;
        this.deliveredAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void failed() {
        this.status = "FAILED";
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() { return updatedAt; }
}
