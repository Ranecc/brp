package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Re-peg audit trail: who moved the anchor, on whose vote. */
@Entity
@Table(name = "peg_history")
public class PegHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String leg;
    private double oldRate;
    private double newRate;
    private Long requestId;
    private Instant changedAt = Instant.now();

    protected PegHistoryEntity() {}

    public PegHistoryEntity(String leg, double oldRate, double newRate, Long requestId) {
        this.leg = leg;
        this.oldRate = oldRate;
        this.newRate = newRate;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public String getLeg() { return leg; }
    public double getOldRate() { return oldRate; }
    public double getNewRate() { return newRate; }
    public Long getRequestId() { return requestId; }
    public Instant getChangedAt() { return changedAt; }
}
