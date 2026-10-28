package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Compute levy minute row (docs/12 §5): insert-only idem, per tenant-minute. */
@Entity
@Table(name = "levy_minutes")
@IdClass(LevyMinuteEntity.Pk.class)
public class LevyMinuteEntity {

    public record Pk(LocalDateTime minuteBucket, String tenantId) implements Serializable {}

    @Id @Column(name = "minute_bucket") private LocalDateTime minuteBucket;
    @Id private String tenantId;

    private double units;
    private double sharePct;
    private long amountCents;
    private boolean posted;

    protected LevyMinuteEntity() {}

    public LevyMinuteEntity(LocalDateTime minuteBucket, String tenantId, double units) {
        this.minuteBucket = minuteBucket;
        this.tenantId = tenantId;
        this.units = units;
    }

    public LocalDateTime getMinuteBucket() { return minuteBucket; }
    public String getTenantId() { return tenantId; }
    public double getUnits() { return units; }
    public double getSharePct() { return sharePct; }
    public long getAmountCents() { return amountCents; }
    public boolean isPosted() { return posted; }

    public void addUnits(double more) { this.units += more; }

    public void assess(double sharePct, long amountCents) {
        this.sharePct = sharePct;
        this.amountCents = amountCents;
        this.posted = true;
    }
}
