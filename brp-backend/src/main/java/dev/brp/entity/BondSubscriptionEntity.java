package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

/** One tenant's position in a series; the escrow receipt and the settlement record. */
@Entity
@Table(name = "bond_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "tenant_id"}))
public class BondSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "series_id")
    private Long seriesId;
    @Column(name = "tenant_id")
    private String tenantId;
    private long units;
    @Column(name = "filled_cents")
    private long filledCents;
    @Column(name = "coupon_paid_cents")
    private long couponPaidCents;
    @Column(name = "principal_paid_cents")
    private long principalPaidCents;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected BondSubscriptionEntity() {}

    public BondSubscriptionEntity(Long seriesId, String tenantId, long units, long filledCents) {
        this.seriesId = seriesId;
        this.tenantId = tenantId;
        this.units = units;
        this.filledCents = filledCents;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSeriesId() { return seriesId; }
    public String getTenantId() { return tenantId; }
    public long getUnits() { return units; }
    public long getFilledCents() { return filledCents; }
    public long getCouponPaidCents() { return couponPaidCents; }
    public long getPrincipalPaidCents() { return principalPaidCents; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void settle(long couponCents, long principalCents) {
        this.couponPaidCents = couponCents;
        this.principalPaidCents = principalCents;
    }
}
