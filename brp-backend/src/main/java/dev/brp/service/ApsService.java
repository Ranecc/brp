package dev.brp.service;

import java.util.Map;

/**
 * APS fair share (docs/09 section 5, P2-15): when global compute capacity is
 * constrained, every tenant is throttled by the SAME factor — proportional
 * fair share, weights = paid + granted chunk credits, never negotiation.
 * One snapshot, many opinions: the weights ride the same credit numbers the
 * governor and the Gini levy use.
 */
public interface ApsService {

    /** @return tenant -> throttle factor in (0, 1]; 1.0 = full speed. */
    Map<String, Double> allocations();

    double factorFor(String tenantId);
}
