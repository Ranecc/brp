package dev.brp.service.impl;

import java.util.List;

/**
 * Pure forecasting math (docs/13 §2-3): a normalized EWMA over sample values
 * and the damped profile-plus-deviation predictor. Static so cold starts and
 * smoothing behavior are table-driven-testable without a database.
 */
final class LoadForecastMath {

    private LoadForecastMath() {}

    /**
     * Normalized EWMA over an ascending time series. The normalization keeps
     * a short window honest at cold start (no "invisible history" weight).
     */
    static double ewma(List<Double> valuesAscending, double alpha) {
        if (valuesAscending.isEmpty()) {
            return 0.0;
        }
        double weight = 1.0;
        double weighted = 0.0;
        double total = 0.0;
        for (int i = valuesAscending.size() - 1; i >= 0; i--) {
            weighted += valuesAscending.get(i) * weight;
            total += weight;
            weight *= (1 - alpha);
        }
        return weighted / total;
    }

    /**
     * predict(m) = profile(m) + damp * (live - profile(now)). When the
     * profile has no reading for the target minute, the current minute's
     * profile stands in — seasonality missing means flat, not zero.
     */
    static double predict(double profileMinute, double live,
                          double profileNow, double damp) {
        return profileMinute + damp * (live - profileNow);
    }

    /** Capacity utilization against the 50 ms tick budget (docs/13 §1). */
    static double utilization(double mspt) {
        return mspt / 50.0;
    }
}
