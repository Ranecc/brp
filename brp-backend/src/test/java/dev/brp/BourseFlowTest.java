package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ExchangeDtos.BookResponse;
import dev.brp.dto.ExchangeDtos.ClearRunResponse;
import dev.brp.dto.ExchangeDtos.ClaimResponse;
import dev.brp.dto.ExchangeDtos.OrderRequest;
import dev.brp.dto.ExchangeDtos.OrderResponse;
import dev.brp.dto.ExchangeDtos.WarehouseRowResponse;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.ExchangeItemEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.ExchangeBatchRepository;
import dev.brp.repository.ExchangeClaimRepository;
import dev.brp.repository.ExchangeClearRepository;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.repository.ExchangeMovementRepository;
import dev.brp.repository.ExchangeOrderRepository;
import dev.brp.repository.ExchangeTradeRepository;
import dev.brp.repository.PegStateRepository;
import dev.brp.repository.WarehouseRepository;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.ExchangeClearingService;
import dev.brp.service.ExchangeMintService;
import dev.brp.service.ExchangeOrderService;
import dev.brp.service.ExchangeWarehouseService;
import dev.brp.service.LedgerService;
import dev.brp.service.TeamService;
import dev.brp.service.impl.ExchangeMintServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bourse end to end (docs/14 §6-7): collateral at filing, uniform-price
 * batches, the mint as the floor both ways, the crawl, and the emerald leg
 * phoning the peg band home. Fee at 1% so the arithmetic stays visible.
 */
@SpringBootTest(properties = {
        "brp.exchange.fee-bips=100",
        "brp.exchange.ref-min-qty=1"})
class BourseFlowTest {

    @Autowired ExchangeOrderService orders;
    @Autowired ExchangeClearingService clearing;
    @Autowired ExchangeWarehouseService warehouse;
    @Autowired ExchangeCatalogService catalog;
    @Autowired ExchangeMintService mint;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired AccountRepository accounts;
    @Autowired ExchangeItemRepository items;
    @Autowired ExchangeOrderRepository orderRepo;
    @Autowired ExchangeTradeRepository tradeRepo;
    @Autowired ExchangeClearRepository clearRepo;
    @Autowired ExchangeBatchRepository batchRepo;
    @Autowired ExchangeMovementRepository movementRepo;
    @Autowired ExchangeClaimRepository claimRepo;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired PegStateRepository pegLegs;
    @Autowired dev.brp.repository.PriceObservationRepository observationsRepo;

    private final List<String> myItems = new java.util.ArrayList<>();
    private final List<String> myTenants = new java.util.ArrayList<>();

    // --- fixtures ---

    private String stockTenant(long seedCents) {
        String tenant = "bf-" + UUID.randomUUID().toString().substring(0, 8);
        myTenants.add(tenant);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Bourse Co", "BRS", UUID.randomUUID().toString()));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", seedCents, "seed"),
                new JournalLine(member, "CREDIT", seedCents, "seed"))));
        return tenant;
    }

    private String listedItem(String id, long ref, boolean pinned) {
        myItems.add(id);
        catalog.pushCatalog(List.of(new dev.brp.dto.ExchangeDtos.CatalogEntryRequest(id, null)));
        catalog.list(id, new dev.brp.dto.ExchangeDtos.ListingRequest("LISTED", ref, pinned, null));
        return id;
    }

    private void deposit(String tenant, String itemId, long qty) {
        warehouse.deposit(tenant, itemId, qty, UUID.randomUUID().toString());
    }

    private OrderResponse place(String tenant, String itemId, String side, long qty, long price) {
        return orders.place(new OrderRequest(tenant, itemId, side, qty, price,
                UUID.randomUUID().toString()));
    }

    private long treasury(String tenant) {
        return accounts.findByTenantIdAndAccountNo(tenant, "treasury")
                .map(a -> a.getBalanceCents()).orElse(0L);
    }

    private long account(String tenant, String accountNo) {
        return accounts.findByTenantIdAndAccountNo(tenant, accountNo)
                .map(a -> a.getBalanceCents()).orElse(0L);
    }

    private WarehouseRowResponse shelf(String tenant, String itemId) {
        return warehouse.holdings(tenant).stream()
                .filter(r -> r.itemId().equals(itemId)).findFirst()
                .orElse(new WarehouseRowResponse(tenant, itemId, 0, 0));
    }

    private void fundMint(long cents) {
        mint.ensureAccounts();
        try {
            ledger.createAccount(ExchangeMintServiceImpl.TENANT,
                    new CreateAccountRequest("seed-faucet", "SYSTEM_FAUCET", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // faucets are patient
        }
        ledger.post(ExchangeMintServiceImpl.TENANT, new PostJournalRequest(
                "mint-seed:" + UUID.randomUUID(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", cents, "seed"),
                new JournalLine("seed-faucet", "CREDIT", cents, "seed"))));
    }

    @AfterEach
    void sweep() {
        orderRepo.deleteAll();
        tradeRepo.deleteAll();
        clearRepo.deleteGuard();
        batchRepo.deleteGuard();
        claimRepo.deleteAll();
        movementRepo.deleteAll();
        warehouseRepo.deleteAll();
        // the clears fed CPI: bourse-venue observations must not move the
        // shared index's base period out from under the monetary desk
        observationsRepo.deleteAll(observationsRepo.findByVenue("bourse"));
        // the founding listing returns to its seeded state
        items.findById("minecraft:emerald").ifPresent(e -> {
            e.setRefCents(6400L);
            e.setPinned(false);
            e.setListedState(ExchangeItemEntity.LISTED);
            items.save(e);
        });
        items.deleteAllById(myItems);
        myItems.clear();
        myTenants.clear();
        // the mint goes home broke: zero its treasury whichever way it leans
        long bal = account(ExchangeMintServiceImpl.TENANT, "treasury");
        if (bal > 0) {
            ledger.post(ExchangeMintServiceImpl.TENANT, new PostJournalRequest(
                    "mint-reset:" + UUID.randomUUID(), "BRC", List.of(
                    new JournalLine("expense-sink", "DEBIT", bal, "reset"),
                    new JournalLine("treasury", "CREDIT", bal, "reset"))));
        } else if (bal < 0) {
            ledger.post(ExchangeMintServiceImpl.TENANT, new PostJournalRequest(
                    "mint-reset:" + UUID.randomUUID(), "BRC", List.of(
                    new JournalLine("treasury", "DEBIT", -bal, "reset"),
                    new JournalLine("expense-sink", "CREDIT", -bal, "reset"))));
        }
        pegLegs.findById(dev.brp.entity.PegStateEntity.EMERALD).ifPresent(leg -> {
            leg.streak(0);
            pegLegs.save(leg);
        });
    }

    // --- filing ---

    @Test
    void collateralMovesAtFilingAndCancelReturnsIt() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_filing", 6400, false);
        deposit(seller, item, 10);

        long buyerBefore = treasury(buyer);
        OrderResponse bid = place(buyer, item, "BID", 10, 6400);
        assertThat(treasury(buyer)).isEqualTo(buyerBefore - 64_000);
        assertThat(account(buyer, "market-escrow")).isEqualTo(64_000);

        long sellerFree = shelf(seller, item).qtyFree();
        OrderResponse ask = place(seller, item, "ASK", 10, 6400);
        assertThat(shelf(seller, item).qtyFree()).isEqualTo(sellerFree - 10);
        assertThat(shelf(seller, item).qtyHeld()).isEqualTo(10);

        orders.cancel(buyer, bid.id());
        assertThat(treasury(buyer)).isEqualTo(buyerBefore);
        orders.cancel(seller, ask.id());
        assertThat(shelf(seller, item).qtyFree()).isEqualTo(sellerFree);
        assertThat(shelf(seller, item).qtyHeld()).isZero();
    }

    @Test
    void filingRefusesStrangersAndGhostItems() {
        String outsider = stockTenant(1_000);
        String item = listedItem("testmod:flow_ghost", 6400, false);
        catalog.list(item, new dev.brp.dto.ExchangeDtos.ListingRequest("BLOCKED", null, null, null));
        assertThatThrownBy(() -> place(outsider, item, "BID", 1, 6400))
                .isInstanceOf(BadRequestException.class);   // not listed anymore
        assertThatThrownBy(() -> place(outsider, "testmod:never_seen", "BID", 1, 6400))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> orders.place(new OrderRequest(outsider, item, "STRADDLE", 1, 6400,
                        UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class);
    }

    // --- the batch ---

    @Test
    void batchClearsAtOneUniformPriceWithTheFee() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_clear", 6400, false);
        deposit(seller, item, 10);

        place(seller, item, "ASK", 10, 6000);
        place(buyer, item, "BID", 10, 6400);
        ClearRunResponse run = clearing.runBatch();

        assertThat(run.itemsCleared()).isEqualTo(1);
        assertThat(run.tradesCleared()).isEqualTo(1);
        // anchor ref 6400: the buyer pays the reference, the seller gets improvement
        var clear = clearing.clears(item, 5).get(0);
        assertThat(clear.priceCents()).isEqualTo(6400);
        assertThat(clear.clearedQty()).isEqualTo(10);

        long fee = 640;   // 1% of 64_000
        assertThat(treasury(seller)).isEqualTo(1_000_000 + 64_000 - fee);
        assertThat(treasury(buyer)).isEqualTo(1_000_000 - 64_000);
        assertThat(account(seller, "market-fee")).isEqualTo(fee);
        assertThat(account(buyer, "market-escrow")).isZero();
        assertThat(shelf(seller, item).qtyHeld()).isZero();
        assertThat(shelf(buyer, item).qtyFree()).isEqualTo(10);
        assertThat(orderRepo.findById(orders.ordersFor(buyer, item).get(0).id()).orElseThrow().getStatus())
                .isEqualTo("FILLED");
    }

    @Test
    void priceImprovementRefundsTheBidSurplus() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_surplus", 6400, false);
        deposit(seller, item, 10);

        place(seller, item, "ASK", 10, 6400);
        place(buyer, item, "BID", 10, 7000);   // escrows 70_000, clears at 64_000
        clearing.runBatch();

        assertThat(treasury(buyer)).isEqualTo(1_000_000 - 64_000);
        assertThat(account(buyer, "market-escrow")).isZero();   // 70k in, 64k out, 6k refunded
    }

    @Test
    void theMintAskIsTheFloorWhenNobodySells() {
        String buyer = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_floor", 6400, false);

        place(buyer, item, "BID", 10, 6500);
        clearing.runBatch();
        assertThat(clearing.clears(item, 1).get(0).priceCents()).isNull();  // bare warehouse: no print

        deposit(ExchangeMintServiceImpl.TENANT, item, 20);
        clearing.runBatch();
        var clear = clearing.clears(item, 1).get(0);
        assertThat(clear.priceCents()).isEqualTo(6464);   // ref + 1% half-spread (200 bips total)
        assertThat(clear.mintAskQty()).isEqualTo(10);

        long fee = 646;   // 1% of 64_640
        assertThat(treasury(ExchangeMintServiceImpl.TENANT)).isEqualTo(64_640 - fee);
        assertThat(shelf(buyer, item).qtyFree()).isEqualTo(10);
        assertThat(shelf(ExchangeMintServiceImpl.TENANT, item).qtyFree()).isEqualTo(10);
    }

    @Test
    void theMintBidIsTheFloorWhenNobodyBuys() {
        String seller = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_bidfloor", 6400, false);
        fundMint(1_000_000);
        deposit(seller, item, 5);

        place(seller, item, "ASK", 5, 6300);
        clearing.runBatch();
        var clear = clearing.clears(item, 1).get(0);
        assertThat(clear.priceCents()).isEqualTo(6336);   // ref − 1% half-spread
        assertThat(clear.mintBidQty()).isEqualTo(5);

        long fee = 316;   // 1% of 31_680
        assertThat(treasury(seller)).isEqualTo(1_000_000 + 31_680 - fee);
        assertThat(treasury(ExchangeMintServiceImpl.TENANT)).isEqualTo(1_000_000 - 31_680);
        assertThat(shelf(ExchangeMintServiceImpl.TENANT, item).qtyFree()).isEqualTo(5);
    }

    @Test
    void remaindersPersistUntilTheNextBell() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_remainder", 6400, false);
        deposit(seller, item, 10);

        place(seller, item, "ASK", 4, 6400);
        place(buyer, item, "BID", 10, 6400);
        clearing.runBatch();
        OrderResponse bid = orders.ordersFor(buyer, item).get(0);
        assertThat(bid.status()).isEqualTo("PARTIAL");
        assertThat(bid.qtyRemaining()).isEqualTo(6);

        deposit(seller, item, 6);
        place(seller, item, "ASK", 6, 6400);
        clearing.runBatch();
        assertThat(orders.ordersFor(buyer, item).get(0).status()).isEqualTo("FILLED");
        // escrow fully consumed: 10 × 6400 filed, 10 × 6400 settled, nothing to refund
        assertThat(account(buyer, "market-escrow")).isZero();
    }

    @Test
    void batchSequenceIncrementsMonotonically() {
        long before = clearing.runBatch().batchSeq();
        long after = clearing.runBatch().batchSeq();
        assertThat(after).isEqualTo(before + 1);
    }

    // --- price evolution & the bimetallic closure ---

    @Test
    void theReferenceCrawlsTowardTheClearUnlessPinned() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        String free = listedItem("testmod:flow_crawl", 6400, false);
        String pinned = listedItem("testmod:flow_pinned", 6400, true);
        deposit(seller, free, 10);
        deposit(seller, pinned, 10);

        place(seller, free, "ASK", 10, 6496);
        place(buyer, free, "BID", 10, 6496);
        place(seller, pinned, "ASK", 10, 6496);
        place(buyer, pinned, "BID", 10, 6496);
        clearing.runBatch();

        // 6400 + 0.25 × (6496 − 6400) = 6424
        assertThat(catalog.entry(free).refCents()).isEqualTo(6424);
        assertThat(clearing.clears(free, 1).get(0).refAfter()).isEqualTo(6424);
        assertThat(catalog.entry(pinned).refCents()).isEqualTo(6400);   // the owner's list is sovereign
    }

    @Test
    void theEmeraldLegPhonesThePegBand() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        deposit(seller, "minecraft:emerald", 10);

        place(seller, "minecraft:emerald", "ASK", 10, 7000);
        place(buyer, "minecraft:emerald", "BID", 10, 7000);
        clearing.runBatch();

        assertThat(clearing.clears("minecraft:emerald", 1).get(0).priceCents()).isEqualTo(7000);
        var leg = pegLegs.findById(dev.brp.entity.PegStateEntity.EMERALD).orElseThrow();
        assertThat(leg.getOutOfBandStreak()).isEqualTo(1);   // 70.0 vs band [60.8, 67.2]
    }

    @Test
    void theBookReadsLikeABook() {
        String seller = stockTenant(1_000_000);
        String buyer = stockTenant(1_000_000);
        String item = listedItem("testmod:flow_book", 6400, false);
        deposit(seller, item, 20);

        place(seller, item, "ASK", 5, 6300);
        place(seller, item, "ASK", 5, 6500);
        place(buyer, item, "BID", 5, 6200);
        place(buyer, item, "BID", 5, 6400);
        BookResponse book = orders.book(item);
        assertThat(book.bids()).hasSize(2);
        assertThat(book.bids().get(0).priceCents()).isEqualTo(6400);   // richest first
        assertThat(book.asks().get(0).priceCents()).isEqualTo(6300);   // cheapest first
        assertThat(book.refCents()).isEqualTo(6400);
    }

    @Test
    void withdrawClaimsRoundTripForTheWarehouseSide() {
        String holder = stockTenant(1_000);
        String item = listedItem("testmod:flow_claim", 6400, false);
        deposit(holder, item, 10);
        ClaimResponse claim = warehouse.withdraw(holder, item, 4, "w1");
        warehouse.confirmWithdraw(holder, claim.id());
        assertThat(warehouse.claims(holder).stream()
                .filter(c -> c.id().equals(claim.id())).findFirst().orElseThrow().status())
                .isEqualTo("CONFIRMED");
    }
}
