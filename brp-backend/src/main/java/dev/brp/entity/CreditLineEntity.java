package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A credit line (docs/13 §6): BRC-denominated debt. Drawn grows by overdraft
 * sweeps and daily interest; the status machine (GOOD → WATCH → RESTRICTED →
 * DEFAULTED) is the teeth — RESTRICTED closes the collateral (the chunks).
 */
@Entity
@Table(name = "credit_lines")
public class CreditLineEntity {

    public static final String GOOD = "GOOD";
    public static final String WATCH = "WATCH";
    public static final String RESTRICTED = "RESTRICTED";
    public static final String DEFAULTED = "DEFAULTED";

    @Id
    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "limit_cents")
    private long limitCents;
    @Column(name = "drawn_cents")
    private long drawnCents;
    private String status;
    @Column(name = "status_since")
    private OffsetDateTime statusSince;
    @Column(name = "annual_rate_pct")
    private int annualRatePct;
    @Column(name = "restricted_since")
    private OffsetDateTime restrictedSince;
    @Column(name = "limit_pinned")
    private boolean limitPinned;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected CreditLineEntity() {}

    public CreditLineEntity(String tenantId, long limitCents, int annualRatePct) {
        this.tenantId = tenantId;
        this.limitCents = limitCents;
        this.annualRatePct = annualRatePct;
        this.status = GOOD;
        this.statusSince = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public String getTenantId() { return tenantId; }
    public long getLimitCents() { return limitCents; }
    public long getDrawnCents() { return drawnCents; }
    public String getStatus() { return status; }
    public OffsetDateTime getStatusSince() { return statusSince; }
    public int getAnnualRatePct() { return annualRatePct; }
    public OffsetDateTime getRestrictedSince() { return restrictedSince; }
    public boolean isLimitPinned() { return limitPinned; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void draw(long amountCents) {
        this.drawnCents += amountCents;
        this.updatedAt = OffsetDateTime.now();
    }

    /** @return what was actually repaid (never more than drawn). */
    public long repay(long amountCents) {
        long applied = Math.min(amountCents, this.drawnCents);
        this.drawnCents -= applied;
        this.updatedAt = OffsetDateTime.now();
        return applied;
    }

    public void addInterest(long amountCents) {
        this.drawnCents += amountCents;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setLimit(long limitCents, boolean pinned) {
        this.limitCents = limitCents;
        this.limitPinned = pinned;
        this.updatedAt = OffsetDateTime.now();
    }

    /** @return utilization in [0,∞); a zero limit means infinite caution. */
    public double utilization() {
        return limitCents <= 0 ? Double.MAX_VALUE : drawnCents / (double) limitCents;
    }

    /** @return the transition actually taken, or null when the status held. */
    public String transitionTo(double watchAt, double restrictAt) {
        String want = this.status;
        if (DEFAULTED.equals(this.status)) {
            return null;   // only REINSTATE leaves default
        }
        double u = utilization();
        if (u >= restrictAt) {
            want = RESTRICTED;
        } else if (u >= watchAt) {
            want = RESTRICTED.equals(this.status) ? RESTRICTED : WATCH;   // frozen until repaid below
        } else {
            want = GOOD;
        }
        // recovery walks one step at a time: RESTRICTED -> WATCH -> GOOD
        if (RESTRICTED.equals(this.status) && u < restrictAt) {
            want = WATCH;
        }
        if (!want.equals(this.status)) {
            this.status = want;
            this.statusSince = OffsetDateTime.now();
            if (RESTRICTED.equals(want)) {
                this.restrictedSince = this.statusSince;
            }
            this.updatedAt = this.statusSince;
            return want;
        }
        return null;
    }

    public void reinstate() {
        this.status = WATCH;
        this.statusSince = OffsetDateTime.now();
        this.restrictedSince = null;
        this.updatedAt = this.statusSince;
    }

    public void noteDefaulted() {
        this.status = DEFAULTED;
        this.statusSince = OffsetDateTime.now();
        this.updatedAt = this.statusSince;
    }
}
