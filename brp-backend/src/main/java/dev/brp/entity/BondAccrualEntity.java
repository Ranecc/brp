package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDate;

/** Daily coupon accrual, insert-only per (series, tenant, day) — the PK is the idem. */
@Entity
@Table(name = "bond_accruals")
@IdClass(BondAccrualEntity.Pk.class)
public class BondAccrualEntity {

    public record Pk(Long seriesId, String tenantId, LocalDate accrualDate) implements Serializable {}

    @Id @Column(name = "series_id") private Long seriesId;
    @Id @Column(name = "tenant_id") private String tenantId;
    @Id @Column(name = "accrual_date") private LocalDate accrualDate;

    @Column(name = "amount_cents")
    private long amountCents;

    protected BondAccrualEntity() {}

    public BondAccrualEntity(Long seriesId, String tenantId, LocalDate accrualDate, long amountCents) {
        this.seriesId = seriesId;
        this.tenantId = tenantId;
        this.accrualDate = accrualDate;
        this.amountCents = amountCents;
    }

    public Long getSeriesId() { return seriesId; }
    public String getTenantId() { return tenantId; }
    public LocalDate getAccrualDate() { return accrualDate; }
    public long getAmountCents() { return amountCents; }
}
