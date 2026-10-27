package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A rule that fired (docs/10 R-2): the paperwork of a bad number. */
@Entity
@Table(name = "anomaly_hits")
public class AnomalyHitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ruleId;
    private String ruleName;
    private String eventType;
    private double observed;
    private String severity;
    private Instant createdAt = Instant.now();

    protected AnomalyHitEntity() {}

    public AnomalyHitEntity(Long ruleId, String ruleName, String eventType,
                            double observed, String severity) {
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.eventType = eventType;
        this.observed = observed;
        this.severity = severity;
    }

    public Long getId() { return id; }
    public Long getRuleId() { return ruleId; }
    public String getRuleName() { return ruleName; }
    public String getEventType() { return eventType; }
    public double getObserved() { return observed; }
    public String getSeverity() { return severity; }
    public Instant getCreatedAt() { return createdAt; }
}
