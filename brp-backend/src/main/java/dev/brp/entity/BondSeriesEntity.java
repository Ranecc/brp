package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A bond series (docs/13 §7): the treasury borrows BRC from players and pays
 * coupons in ticks it actually had — performanceFactor at settlement.
 */
@Entity
@Table(name = "bond_series")
public class BondSeriesEntity {

    public static final String OPEN = "OPEN";
    public static final String AWARDED = "AWARDED";
    public static final String MATURED = "MATURED";
    public static final String REDEEMED = "REDEEMED";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(name = "face_cents")
    private long faceCents;             // per unit
    @Column(name = "coupon_pct_annual")
    private int couponPctAnnual;
    @Column(name = "term_days")
    private int termDays;
    @Column(name = "total_units")
    private long totalUnits;
    @Column(name = "sold_units")
    private long soldUnits;
    @Column(name = "threshold_mspt")
    private double thresholdMspt;
    private String status;
    @Column(name = "issued_at")
    private OffsetDateTime issuedAt;
    @Column(name = "matures_at")
    private OffsetDateTime maturesAt;

    protected BondSeriesEntity() {}

    public BondSeriesEntity(String name, long faceCents, int couponPctAnnual,
                            int termDays, long totalUnits, double thresholdMspt) {
        this.name = name;
        this.faceCents = faceCents;
        this.couponPctAnnual = couponPctAnnual;
        this.termDays = termDays;
        this.totalUnits = totalUnits;
        this.thresholdMspt = thresholdMspt;
        this.status = OPEN;
        this.issuedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public long getFaceCents() { return faceCents; }
    public int getCouponPctAnnual() { return couponPctAnnual; }
    public int getTermDays() { return termDays; }
    public long getTotalUnits() { return totalUnits; }
    public long getSoldUnits() { return soldUnits; }
    public double getThresholdMspt() { return thresholdMspt; }
    public String getStatus() { return status; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getMaturesAt() { return maturesAt; }

    public void addUnits(long units) {
        this.soldUnits += units;
    }

    /** Full book closes to AWARDED with a real maturity date; a starving book fails. */
    public void close() {
        this.maturesAt = this.issuedAt.plusDays(termDays);
        this.status = soldUnits >= totalUnits ? AWARDED : FAILED;
    }

    public void markRedeemed() {
        this.status = REDEEMED;
    }

    /** Tests re-date maturity to settle without waiting out the term. */
    public void setMaturesAt(OffsetDateTime maturesAt) {
        this.maturesAt = maturesAt;
    }
}
