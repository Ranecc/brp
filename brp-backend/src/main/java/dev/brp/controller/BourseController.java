package dev.brp.controller;

import dev.brp.dto.ExchangeDtos.BookResponse;
import dev.brp.dto.ExchangeDtos.CatalogEntryResponse;
import dev.brp.dto.ExchangeDtos.CatalogPushRequest;
import dev.brp.dto.ExchangeDtos.ClearResponse;
import dev.brp.dto.ExchangeDtos.ClearRunResponse;
import dev.brp.dto.ExchangeDtos.ClaimResponse;
import dev.brp.dto.ExchangeDtos.DepositRequest;
import dev.brp.dto.ExchangeDtos.ListingRequest;
import dev.brp.dto.ExchangeDtos.MintStateResponse;
import dev.brp.dto.ExchangeDtos.OrderRequest;
import dev.brp.dto.ExchangeDtos.OrderResponse;
import dev.brp.dto.ExchangeDtos.TradeResponse;
import dev.brp.dto.ExchangeDtos.WarehouseRowResponse;
import dev.brp.dto.ExchangeDtos.WithdrawRequest;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.ExchangeClearingService;
import dev.brp.service.ExchangeMintService;
import dev.brp.service.ExchangeOrderService;
import dev.brp.service.ExchangeWarehouseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The bourse desk (docs/14): catalog and listing governance, warehouse,
 * orders, batch clearing, and the mint's public state. Controllers stay
 * five-line delegations; the economics lives in the services.
 */
@RestController
public class BourseController {

    private final ExchangeCatalogService catalog;
    private final ExchangeWarehouseService warehouse;
    private final ExchangeOrderService orders;
    private final ExchangeClearingService clearing;
    private final ExchangeMintService mint;

    public BourseController(ExchangeCatalogService catalog,
                            ExchangeWarehouseService warehouse,
                            ExchangeOrderService orders,
                            ExchangeClearingService clearing,
                            ExchangeMintService mint) {
        this.catalog = catalog;
        this.warehouse = warehouse;
        this.orders = orders;
        this.clearing = clearing;
        this.mint = mint;
    }

    // --- catalog & listing governance (docs/14 §5) ---

    @PostMapping("/api/v1/exchange/catalog")
    public void pushCatalog(@RequestBody CatalogPushRequest push) {
        catalog.pushCatalog(push.items());
    }

    @GetMapping("/api/v1/exchange/items")
    public List<CatalogEntryResponse> items() {
        return catalog.catalog();
    }

    @GetMapping("/api/v1/exchange/items/{itemId}")
    public CatalogEntryResponse item(@PathVariable String itemId) {
        return catalog.entry(itemId);
    }

    @PutMapping("/api/v1/exchange/items/{itemId}")
    public CatalogEntryResponse list(@PathVariable String itemId,
                                     @RequestBody ListingRequest request) {
        return catalog.list(itemId, request);
    }

    // --- warehouse (docs/14 §8) ---

    @PostMapping("/api/v1/exchange/warehouse/{tenantId}/deposit")
    public WarehouseRowResponse deposit(@PathVariable String tenantId,
                                        @RequestBody DepositRequest req) {
        return warehouse.deposit(tenantId, req.itemId(), req.qty(), req.idemKey());
    }

    @PostMapping("/api/v1/exchange/warehouse/{tenantId}/withdraw")
    public ClaimResponse withdraw(@PathVariable String tenantId,
                                  @RequestBody WithdrawRequest req) {
        return warehouse.withdraw(tenantId, req.itemId(), req.qty(), req.idemKey());
    }

    @PostMapping("/api/v1/exchange/warehouse/{tenantId}/withdraw/{claimId}/confirm")
    public void confirmWithdraw(@PathVariable String tenantId, @PathVariable long claimId) {
        warehouse.confirmWithdraw(tenantId, claimId);
    }

    @PostMapping("/api/v1/exchange/warehouse/{tenantId}/withdraw/{claimId}/release")
    public void releaseWithdraw(@PathVariable String tenantId, @PathVariable long claimId) {
        warehouse.releaseWithdraw(tenantId, claimId);
    }

    @GetMapping("/api/v1/exchange/warehouse/{tenantId}")
    public List<WarehouseRowResponse> holdings(@PathVariable String tenantId) {
        return warehouse.holdings(tenantId);
    }

    @GetMapping("/api/v1/exchange/claims/{tenantId}")
    public List<ClaimResponse> claims(@PathVariable String tenantId) {
        return warehouse.claims(tenantId);
    }

    // --- orders & the book (docs/14 §6) ---

    @PostMapping("/api/v1/exchange/orders")
    public OrderResponse place(@RequestBody OrderRequest request) {
        return orders.place(request);
    }

    @DeleteMapping("/api/v1/exchange/orders/{tenantId}/{orderId}")
    public OrderResponse cancel(@PathVariable String tenantId, @PathVariable long orderId) {
        return orders.cancel(tenantId, orderId);
    }

    @GetMapping("/api/v1/exchange/orders")
    public List<OrderResponse> orders(@RequestParam(required = false) String tenant,
                                      @RequestParam(required = false) String item) {
        return tenant != null && item != null ? orders.ordersFor(tenant, item)
                : tenant != null ? orders.orders(tenant)
                : List.of();
    }

    @GetMapping("/api/v1/exchange/book/{itemId}")
    public BookResponse book(@PathVariable String itemId) {
        return orders.book(itemId);
    }

    // --- clearing (docs/14 §6) ---

    @PostMapping("/api/v1/exchange/clear")
    public ClearRunResponse clear() {
        return clearing.runBatch();
    }

    @GetMapping("/api/v1/exchange/clears")
    public List<ClearResponse> clears(@RequestParam String item,
                                      @RequestParam(defaultValue = "50") int limit) {
        return clearing.clears(item, limit);
    }

    @GetMapping("/api/v1/exchange/trades")
    public List<TradeResponse> trades(@RequestParam String item,
                                      @RequestParam(defaultValue = "50") int limit) {
        return clearing.trades(item, limit);
    }

    // --- the mint's public state (docs/14 §4) ---

    @GetMapping("/api/v1/exchange/mint")
    public MintStateResponse mintState() {
        return mint.state();
    }
}
