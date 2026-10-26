package dev.brp.telemetry;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Dedup record for ingested events; full payloads land on the event bus (P1: Redis Streams). */
@Entity
@Table(name = "ingested_events")
public class IngestedEventEntity {

    @Id
    private String eventId;

    private String agentId;
    private String eventType;
    private Instant receivedAt = Instant.now();

    protected IngestedEventEntity() {}

    public IngestedEventEntity(String eventId, String agentId, String eventType) {
        this.eventId = eventId;
        this.agentId = agentId;
        this.eventType = eventType;
    }
}
