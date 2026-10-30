package dev.brp;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ExchangeDtos.CatalogEntryRequest;
import dev.brp.dto.ExchangeDtos.ListingRequest;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.repository.ExchangeMovementRepository;
import dev.brp.repository.WarehouseRepository;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.ExchangeMintService;
import dev.brp.service.ExchangeWarehouseService;
import dev.brp.service.LedgerService;
import dev.brp.service.impl.AuctionMath.Quote;
import dev.brp.service.impl.ExchangeMintServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The listed mint (docs/14 §4): quotes derive from the reference, quantities
 * from the reserve. Credit lets the mint bid beyond its float — a knob the
 * owner turns deliberately, never a silent overdraft.
 */
@SpringBootTest(properties = {
        "brp.exchange.mint-credit-cents=100000",
        "brp.exchange.mint-float-cents=5000"})
class ExchangeMintTest {

    @Autowired ExchangeMintService mint;
    @Autowired ExchangeCatalogService catalog;
    @Autowired ExchangeWarehouseService warehouse;
    @Autowired LedgerService ledger;
    @Autowired AccountRepository accounts;
    @Autowired ExchangeItemRepository items;
    @Autowired WarehouseRepository warehouseRepo;
    @Autowired ExchangeMovementRepository movementRepo;

    private static final String ITEM = "testmod:mint_probe";

    @AfterEach
    void sweep() {
        warehouseRepo.deleteAll();
        movementRepo.deleteAll();
        items.deleteAllById(List.of(ITEM));
        long bal = accounts.findByTenantIdAndAccountNo(ExchangeMintServiceImpl.TENANT, "treasury")
                .map(a -> a.getBalanceCents()).orElse(0L);
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
    }

    private long refItem() {
        catalog.pushCatalog(List.of(new CatalogEntryRequest(ITEM, null)));
        catalog.list(ITEM, new ListingRequest("LISTED", 6400L, null, null));
        return 6400L;
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

    private long sink() {
        return accounts.findByTenantIdAndAccountNo(ExchangeMintServiceImpl.TENANT, "expense-sink")
                .map(a -> a.getBalanceCents()).orElse(0L);
    }

    @Test
    void theAskFollowsTheWarehouseNotTheWish() {
        long ref = refItem();
        var item = items.findById(ITEM).orElseThrow();
        assertThat(mint.askQuote(item)).isNull();   // bare shelves, no quotes

        warehouse.deposit(ExchangeMintServiceImpl.TENANT, ITEM, 100, "d1");
        Quote ask = mint.askQuote(item);
        assertThat(ask).isNotNull();
        assertThat(ask.priceCents()).isEqualTo(ref + ref * 200 / 20000);   // 6464
        assertThat(ask.qtyRemaining()).isEqualTo(64);                      // depth caps the wish
    }

    @Test
    void creditExtendsTheBidBeyondTheFloat() {
        long ref = refItem();
        var item = items.findById(ITEM).orElseThrow();
        // treasury 0, credit 100_000: the owner's deliberate overdraft line
        Quote bid = mint.bidQuote(item);
        assertThat(bid).isNotNull();
        assertThat(bid.priceCents()).isEqualTo(ref - ref * 200 / 20000);   // 6336
        assertThat(bid.qtyRemaining()).isEqualTo(15);                      // floor(100_000 / 6336)
    }

    @Test
    void theBidDisappearsWhenTheReserveIsEmpty() {
        // treasury 0, credit 0 is the default elsewhere — here the probe item
        // priced so high that even credit buys nothing
        catalog.pushCatalog(List.of(new CatalogEntryRequest(ITEM, null)));
        catalog.list(ITEM, new ListingRequest("LISTED", 10_000_000L, null, null));
        var item = items.findById(ITEM).orElseThrow();
        assertThat(mint.bidQuote(item)).isNull();   // 100_000 / 9_900_000 = 0 lots
    }

    @Test
    void seigniorageSweepsAboveTheFloatOncePerDay() {
        fundMint(50_000);
        long sinkBefore = sink();
        mint.sweepSeigniorage();
        long swept = sink() - sinkBefore;
        assertThat(swept).isEqualTo(45_000);   // 50_000 − 5_000 float
        assertThat(accounts.findByTenantIdAndAccountNo(ExchangeMintServiceImpl.TENANT, "treasury")
                .orElseThrow().getBalanceCents()).isEqualTo(5_000);

        mint.sweepSeigniorage();   // same day, same idem key: the ledger replays
        assertThat(sink() - sinkBefore).isEqualTo(45_000);
    }

    @Test
    void stateReportsTheDesk() {
        fundMint(12_345);
        var state = mint.state();
        assertThat(state.tenantId()).isEqualTo("mint");
        assertThat(state.treasuryCents()).isEqualTo(12_345);
        assertThat(state.floatCents()).isEqualTo(5_000);
        assertThat(state.spreadBips()).isEqualTo(200);
        assertThat(state.depth()).isEqualTo(64);
    }
}
