package dev.brp.service;

import dev.brp.dto.LoadDtos.MarketOrderResponse;

import java.util.List;

/**
 * The quota market (docs/13 §8): a secondary market for compute capacity,
 * anchored to the peg, cleared price-time priority, settled in single
 * transactions. The fee is the treasury's only market income.
 */
public interface QuotaMarketService {

    MarketOrderResponse place(String tenantId, String idemKey, String side, long qty, long priceCents);

    MarketOrderResponse cancel(String tenantId, long orderId);

    List<MarketOrderResponse> book();

    List<MarketOrderResponse> orders(String tenantId);

    /** The tape: the most recent executions, newest first. */
    List<dev.brp.entity.MarketTradeEntity> trades();

    /** Expire stale orders and release their escrows. */
    void expireStale();

    int expireStaleCount();
}
