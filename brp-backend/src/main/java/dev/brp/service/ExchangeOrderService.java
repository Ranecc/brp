package dev.brp.service;

import dev.brp.dto.ExchangeDtos.BookResponse;
import dev.brp.dto.ExchangeDtos.OrderRequest;
import dev.brp.dto.ExchangeDtos.OrderResponse;

import java.util.List;

/**
 * Order filing (docs/14 §6): BIDs escrow BRC, ASKs hold warehouse stock,
 * nothing crosses until the batch says so. Same admission rule as every
 * desk in this building — RESTRICTED credit files no commitments.
 */
public interface ExchangeOrderService {

    OrderResponse place(OrderRequest request);

    OrderResponse cancel(String tenantId, long orderId);

    List<OrderResponse> orders(String tenantId);

    List<OrderResponse> ordersFor(String tenantId, String itemId);

    BookResponse book(String itemId);

    void expireStale();

    int expireStaleCount();
}
