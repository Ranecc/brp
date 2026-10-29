package dev.brp.service;

import dev.brp.entity.BondSeriesEntity;
import dev.brp.entity.BondSubscriptionEntity;

import java.util.List;

/**
 * TPS bonds (docs/13 §7, R-7): issue, subscribe, accrue daily, settle at
 * maturity against MEASURED ticks. Options stay permanently sealed.
 */
public interface BondService {

    BondSeriesEntity issue(String name, long faceCents, int couponPctAnnual,
                           int termDays, long totalUnits, double thresholdMspt);

    List<BondSeriesEntity> series();

    BondSeriesEntity get(long seriesId);

    /** Escrows BRC into the bond sink; one position per tenant per series. */
    BondSubscriptionEntity subscribe(long seriesId, String tenantId, long units);

    /** Close the book: full -> AWARDED (maturity dated), starving -> FAILED + refunds. */
    BondSeriesEntity close(long seriesId);

    /** Daily coupon accrual for awarded series; idempotent per (series, tenant, day). */
    boolean accrueDaily();

    /** Settle matured series: coupons x performanceFactor, principal in full. */
    List<Long> settleMatured();
}
