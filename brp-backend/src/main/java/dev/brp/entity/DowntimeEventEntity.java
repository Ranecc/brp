package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One stoppage (docs/13 §9): the grid operator's hand is a row, not a rumor. */
@Entity
@Table(name = "downtime_events")
public class DowntimeEventEntity {

    public static final String DR_SHED = "DR_SHED";
    public static final String CREDIT_CUTOFF = "CREDIT_CUTOFF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    private String machine;
    private String reason;
    @Column(name = "idem_key")
    private String idemKey;
    private LocalDateTime since;
    private LocalDateTime until;
    private long minutes;

    protected DowntimeEventEntity() {}

    public DowntimeEventEntity(String tenantId, String machine, String reason,
                               String idemKey, LocalDateTime since, LocalDateTime until) {
        this.tenantId = tenantId;
        this.machine = machine;
        this.reason = reason;
        this.idemKey = idemKey;
        this.since = since;
        this.until = until;
        this.minutes = java.time.Duration.between(since, until).toMinutes();
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getMachine() { return machine; }
    public String getReason() { return reason; }
    public String getIdemKey() { return idemKey; }
    public LocalDateTime getSince() { return since; }
    public LocalDateTime getUntil() { return until; }
    public long getMinutes() { return minutes; }
}
