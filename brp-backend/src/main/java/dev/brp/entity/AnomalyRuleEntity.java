package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** L1 hot rule (docs/05 L1, docs/10 R-2): numeric threshold on a telemetry field. */
@Entity
@Table(name = "anomaly_rules")
public class AnomalyRuleEntity {

    public enum Op { GT, GTE, LT, LTE, EQ }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String eventType;
    private String field;
    private String op;
    private double threshold;
    private String severity = "WARN";
    private String status = "ACTIVE";
    private Instant createdAt = Instant.now();

    protected AnomalyRuleEntity() {}

    public AnomalyRuleEntity(String name, String eventType, String field,
                             Op op, double threshold, String severity) {
        this.name = name;
        this.eventType = eventType;
        this.field = field;
        this.op = op.name();
        this.threshold = threshold;
        this.severity = severity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEventType() { return eventType; }
    public String getField() { return field; }
    public String getOp() { return op; }
    public double getThreshold() { return threshold; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void disable() { this.status = "DISABLED"; }

    /** The rule's one job: does this observed value trip it? */
    public boolean trips(double observed) {
        return switch (Op.valueOf(op)) {
            case GT -> observed > threshold;
            case GTE -> observed >= threshold;
            case LT -> observed < threshold;
            case LTE -> observed <= threshold;
            case EQ -> observed == threshold;
        };
    }
}
