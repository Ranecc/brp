package dev.brp.dto;

/** Bourse DTOs (docs/14): catalog, listing governance, warehouse, orders, clears. */
public final class ExchangeDtos {

    private ExchangeDtos() {}

    public record CatalogEntryRequest(String itemId, String displayName) {}

    public record CatalogPushRequest(java.util.List<CatalogEntryRequest> items) {}

    public record CatalogEntryResponse(String itemId, String displayName,
                                       String listedState, long refCents, boolean pinned) {}

    public record ListingRequest(String listedState, Long refCents, Boolean pinned,
                                 String displayName) {}

    public record WarehouseRowResponse(String tenantId, String itemId,
                                       long qtyFree, long qtyHeld) {}

    public record DepositRequest(String itemId, long qty, String idemKey) {}

    public record WithdrawRequest(String itemId, long qty, String idemKey) {}

    public record ClaimResponse(Long id, String tenantId, String itemId,
                                long qty, String status) {}

    public record OrderRequest(String tenantId, String itemId, String side,
                               long qty, long priceCents, String idemKey) {}

    public record OrderResponse(Long id, String tenantId, String itemId, String side,
                                long qty, long qtyRemaining, long priceCents,
                                String status) {}

    public record BookEntry(Long orderId, String side, long qtyRemaining,
                            long priceCents, String tenantId) {}

    public record BookResponse(String itemId, long refCents, boolean pinned,
                               String listedState,
                               java.util.List<BookEntry> bids,
                               java.util.List<BookEntry> asks) {}

    public record ClearResponse(long batchSeq, String itemId, Long priceCents,
                                long clearedQty, long bidVol, long askVol,
                                long mintBidQty, long mintAskQty,
                                Long refBefore, Long refAfter) {}

    public record TradeResponse(Long id, long batchSeq, String itemId, String buyer,
                                String seller, long qty, long priceCents, long feeCents) {}

    public record MintStateResponse(String tenantId, long floatCents, long treasuryCents,
                                    long warehouseItems, int spreadBips, int depth) {}

    public record RefCrawlResponse(String itemId, long refBefore, long refAfter,
                                   boolean moved) {}
}
