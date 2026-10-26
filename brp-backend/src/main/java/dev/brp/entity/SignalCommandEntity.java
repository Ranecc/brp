package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Push plane v0 = poll + ack (SSE planned for P1).
 * State machine: PENDING -> DELIVERED (only after the agent acks).
 */
@Entity
@Table(name = "signal_commands")
public class SignalCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String channel;
    private int level;
    private String status = "PENDING";
    private Instant createdAt = Instant.now();

    protected SignalCommandEntity() {}

    public SignalCommandEntity(String tenantId, String channel, int level) {
        this.tenantId = tenantId;
        this.channel = channel;
        this.level = level;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getChannel() { return channel; }
    public int getLevel() { return level; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void markDelivered() { this.status = "DELIVERED"; }
}
