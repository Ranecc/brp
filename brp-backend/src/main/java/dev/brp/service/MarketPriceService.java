package dev.brp.service;

import java.util.List;

/**
 * Price observations + the daily CPI (docs/12 §12): the feed is manual or
 * telemetry; the index is a geometric mean of item relatives vs each
 * item's first observed price, weights from the configured basket.
 */
public interface MarketPriceService {

    void record(String itemId, long priceCents, String currency, long qty,
                String venue, String source);

    /** Compute today's print; one truth per day. @return the index value. */
    double materializeToday();

    List<dev.brp.entity.CpiDailyEntity> history();

    java.util.Map<String, Double> shares();
}
