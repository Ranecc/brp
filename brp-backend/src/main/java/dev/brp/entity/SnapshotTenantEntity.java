package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/** Per-tenant row of the shared weekly snapshot: wealth + velocity. */
@Entity
@Table(name = "snapshot_tenants")
@IdClass(SnapshotTenantEntity.Pk.class)
public class SnapshotTenantEntity {

    public record Pk(String weekOf, String tenantId) implements Serializable {}

    @Id private String weekOf;
    @Id private String tenantId;

    private long wealthCents;
    private double velocity;

    protected SnapshotTenantEntity() {}

    public SnapshotTenantEntity(String weekOf, String tenantId, long wealthCents, double velocity) {
        this.weekOf = weekOf;
        this.tenantId = tenantId;
        this.wealthCents = wealthCents;
        this.velocity = velocity;
    }

    public String getWeekOf() { return weekOf; }
    public String getTenantId() { return tenantId; }
    public long getWealthCents() { return wealthCents; }
    public double getVelocity() { return velocity; }
}
