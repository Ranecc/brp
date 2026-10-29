package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * A deferrable-load job (docs/13 §5): tenant-declared flexible work — "run
 * the mob farm for 90 minutes sometime before Saturday". The scheduler walks
 * it into the cheapest feasible hours; the governor holds the line closed
 * outside the awarded window.
 */
@Entity
@Table(name = "deferred_jobs")
public class DeferredJobEntity {

    public static final String FILED = "FILED";
    public static final String AWARDED = "AWARDED";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String EXPIRED = "EXPIRED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "idem_key")
    private String idemKey;
    private String resource;
    private long quantity;
    @Column(name = "est_minutes")
    private long estMinutes;
    private LocalDateTime earliest;
    private LocalDateTime deadline;
    private String status;
    @Column(name = "awarded_start")
    private LocalDateTime awardedStart;
    @Column(name = "awarded_minutes")
    private long awardedMinutes;
    @Column(name = "window_seq")
    private int windowSeq;
    @Column(name = "shed_count")
    private int shedCount;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected DeferredJobEntity() {}

    public DeferredJobEntity(String tenantId, String idemKey, String resource, long quantity,
                             long estMinutes, LocalDateTime earliest, LocalDateTime deadline) {
        this.tenantId = tenantId;
        this.idemKey = idemKey;
        this.resource = resource;
        this.quantity = quantity;
        this.estMinutes = estMinutes;
        this.earliest = earliest;
        this.deadline = deadline;
        this.status = FILED;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getIdemKey() { return idemKey; }
    public String getResource() { return resource; }
    public long getQuantity() { return quantity; }
    public long getEstMinutes() { return estMinutes; }
    public LocalDateTime getEarliest() { return earliest; }
    public LocalDateTime getDeadline() { return deadline; }
    public String getStatus() { return status; }
    public LocalDateTime getAwardedStart() { return awardedStart; }
    public long getAwardedMinutes() { return awardedMinutes; }
    public int getWindowSeq() { return windowSeq; }
    public int getShedCount() { return shedCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void award(LocalDateTime start, long minutes) {
        this.status = AWARDED;
        this.awardedStart = start;
        this.awardedMinutes = minutes;
        this.windowSeq++;
    }

    public void markRunning() {
        this.status = RUNNING;
    }

    /** A shed closes the window, extends the deadline, and re-queues the job. */
    public void shed(java.time.Duration extension) {
        this.status = AWARDED;
        this.awardedStart = null;
        this.awardedMinutes = 0;
        this.shedCount++;
        this.deadline = this.deadline.plus(extension);
    }

    public void close(String finalStatus) {
        this.status = finalStatus;
        this.awardedStart = null;
        this.awardedMinutes = 0;
    }
}
