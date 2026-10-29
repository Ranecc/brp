package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.LoadDtos.MarketOrderResponse;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.QuotaBucketEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.MarketOrderRepository;
import dev.brp.repository.QuotaBucketRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.CreditService;
import dev.brp.service.LedgerService;
import dev.brp.service.QuotaMarketService;
import dev.brp.service.impl.QuotaMarketServiceImpl;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order book (docs/13 §8): peg-band admission, locked collateral, crosses
 * at the resting price, the treasury's fee, and self-trades skipped. Quota
 * capacity lives in the market_quota bucket — provisioned, never shorted.
 */
@SpringBootTest(properties = "brp.market.fee-bips=50")
class QuotaMarketTest {

    @Autowired QuotaMarketService market;
    @Autowired QuotaBucketRepository buckets;
    @Autowired MarketOrderRepository orders;
    @Autowired LedgerService ledger;
    @Autowired CreditService credit;
    @Autowired dev.brp.repository.CreditAccrualRunRepository creditRuns;
    @Autowired Clock clock;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired dev.brp.repository.PegStateRepository pegLegs;

    /** The band moves under other tests' repegs — price from the LIVE band. */
    private long bandPrice() {
        var leg = pegLegs.findById(dev.brp.entity.PegStateEntity.QUOTA).orElseThrow();
        return Math.round((leg.getBandLow() + leg.getBandHigh()) / 2 * 100.0);
    }

    private long bandPriceBelow() {
        var leg = pegLegs.findById(dev.brp.entity.PegStateEntity.QUOTA).orElseThrow();
        return Math.round(leg.getBandLow() * 100.0);
    }

    /** Orders this test created: (tenant, id) — the shared book demands cleanup. */
    private final List<String[]> mine = new ArrayList<>();

    private MarketOrderResponse place(String tenant, String key, String side, long qty, long price) {
        MarketOrderResponse o = market.place(tenant, key, side, qty, price);
        mine.add(new String[]{tenant, String.valueOf(o.id())});
        return o;
    }

    @AfterEach
    void drain() {
        for (String[] o : mine) {
            try {
                market.cancel(o[0], Long.parseLong(o[1]));
            } catch (RuntimeException alreadyDone) {
                // filled or cancelled: exactly what the book wanted
            }
        }
        mine.clear();
    }

    /** Seeds a tenant with a BRC wallet and N units of sellable capacity. */
    private String provisionedTenant(long capacity) {
        String tenant = "mq-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Market Co", "MQT", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        if (capacity > 0) {
            buckets.save(new QuotaBucketEntity(tenant, QuotaMarketServiceImpl.RESOURCE, capacity));
        }
        return tenant;
    }

    private long locked(String tenant) {
        return buckets.findById(new QuotaBucketEntity.Pk(tenant, QuotaMarketServiceImpl.RESOURCE))
                .map(QuotaBucketEntity::getUsed).orElse(0L);
    }

    private long limit(String tenant) {
        return buckets.findById(new QuotaBucketEntity.Pk(tenant, QuotaMarketServiceImpl.RESOURCE))
                .map(QuotaBucketEntity::getLimitAmount).orElse(0L);
    }

    private long treasury(String tenant) {
        return ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }

    @Test
    void pricesOutsideThePegBandAreRefused() {
        String tenant = provisionedTenant(1_000);
        assertThatThrownBy(() -> place(tenant, "k1-" + UUID.randomUUID(), "ASK", 10, bandPrice() * 100))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("market.price.outside.band");
        assertThatThrownBy(() -> place(tenant, "k2-" + UUID.randomUUID(), "BID", 10, Math.max(1, bandPrice() / 100)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("market.price.outside.band");
    }

    @Test
    void unprovisionedSellersCannotShortTheMarket() {
        String tenant = provisionedTenant(0);
        assertThatThrownBy(() -> place(tenant, "k3-" + UUID.randomUUID(), "ASK", 10, bandPrice()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("market.cant.cover");
    }

    @Test
    void askLocksCapacityBidEscrowsCash() {
        String seller = provisionedTenant(100);
        String buyer = provisionedTenant(0);

        long px = bandPrice();
        place(seller, "a-" + UUID.randomUUID(), "ASK", 40, px);
        assertThat(locked(seller)).isEqualTo(40);   // the ASK locks capacity on placement

        MarketOrderResponse bid = place(buyer, "b-" + UUID.randomUUID(), "BID", 40, px);

        assertThat(bid.status()).isEqualTo("FILLED");
        // the placement DTO is a snapshot; the book is the truth
        assertThat(market.orders(seller).get(0).status()).isEqualTo("FILLED");
        // buyer paid 40 x px; the escrow journal took it at placement
        assertThat(treasury(buyer)).isEqualTo(1_000_000L - 40 * px);
        // seller collected, minus the 50-bip fee
        assertThat(treasury(seller)).isEqualTo(1_000_000L + 40 * px - (40 * px) * 50 / 10_000L);
        // delivered capacity landed in the buyer's bucket
        assertThat(limit(buyer)).isEqualTo(40);
        // the seller's lock cleared out on the fill
        assertThat(locked(seller)).isZero();
    }

    @Test
    void tradesPrintAtTheRestingPrice() {
        String seller = provisionedTenant(100);
        String buyer = provisionedTenant(0);
        long maker = Math.max(bandPriceBelow() + 1, bandPrice() - 30);   // inside the band, below any taker
        place(seller, "maker-" + UUID.randomUUID(), "ASK", 10, maker);   // resting at maker
        MarketOrderResponse taker = place(buyer, "taker-" + UUID.randomUUID(), "BID", 10, bandPrice());

        assertThat(taker.status()).isEqualTo("FILLED");
        long notional = 10 * maker;
        assertThat(treasury(seller)).isEqualTo(1_000_000L + notional - notional * 50 / 10_000L);
        assertThat(treasury(buyer)).isEqualTo(1_000_000L - notional);
    }

    @Test
    void selfTradesAreSkippedNotMatched() {
        String lonely = provisionedTenant(100);
        // an ASK resting, then the SAME tenant bids across it: skipped, both stay resting
        place(lonely, "self-ask-" + UUID.randomUUID(), "ASK", 10, bandPrice());
        MarketOrderResponse bid = place(lonely, "self-bid-" + UUID.randomUUID(), "BID", 10, bandPrice());
        assertThat(bid.status()).isEqualTo("NEW");
        assertThat(market.book()).hasSize(2);
    }

    @Test
    void cancelReleasesTheCollateral() {
        String seller = provisionedTenant(100);
        String buyer = provisionedTenant(0);
        // non-crossing prices (bid below ask): cancel must release both collaterals
        long px = bandPrice();
        MarketOrderResponse ask = place(seller, "c-ask-" + UUID.randomUUID(), "ASK", 30, px);
        MarketOrderResponse bid = place(buyer, "c-bid-" + UUID.randomUUID(), "BID", 30, bandPriceBelow());
        assertThat(locked(seller)).isEqualTo(30);
        assertThat(treasury(buyer)).isEqualTo(1_000_000L - 30 * bandPriceBelow());   // escrowed

        market.cancel(seller, ask.id());
        market.cancel(buyer, bid.id());
        mine.clear();   // cancelled on purpose; afterEach would only re-fail

        assertThat(locked(seller)).isZero();
        assertThat(treasury(buyer)).isEqualTo(1_000_000L);   // escrow came home
        assertThat(market.book()).isEmpty();

        assertThat(locked(seller)).isZero();
        assertThat(treasury(buyer)).isEqualTo(1_000_000L);   // escrow came home
        assertThat(market.book()).isEmpty();
    }

    @Test
    void partialFillsLeaveBothOrdersResting() {
        String seller = provisionedTenant(100);
        String buyer = provisionedTenant(0);
        place(seller, "p-ask-" + UUID.randomUUID(), "ASK", 50, bandPrice());
        MarketOrderResponse bid = place(buyer, "p-bid-" + UUID.randomUUID(), "BID", 20, bandPrice());

        assertThat(bid.status()).isEqualTo("FILLED");
        List<MarketOrderResponse> sellerOrders = market.orders(seller);
        assertThat(sellerOrders.get(0).status()).isEqualTo("PARTIAL");
        assertThat(sellerOrders.get(0).qtyRemaining()).isEqualTo(30);
        assertThat(locked(seller)).isEqualTo(30);
    }

    @Test
    void restrictedCreditCannotPlaceOrders() {
        String tenant = provisionedTenant(100);
        // overdraft into RESTRICTED (mirrors CreditCutoffTest's plumbing)
        String member = memberRepo.findAll().stream()
                .filter(m -> m.getTeamId().equals(tenant) && "MEMBER".equals(m.getRole()))
                .findFirst().orElseThrow().getPlayerUuid();
        credit.setLimit(tenant, 500_000L);
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(member, "DEBIT", 1_500_000L, "spend"),
                new JournalLine("treasury", "CREDIT", 1_500_000L, "spend"))));
        // sweep the overdraft into RESTRICTED (own the day's guard first)
        creditRuns.deleteGuard(java.time.LocalDate.now(clock));
        credit.runDailyDesk();
        assertThat(credit.blocked(tenant)).isTrue();

        assertThatThrownBy(() -> place(tenant, "r-" + UUID.randomUUID(), "BID", 10, bandPrice()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("credit.restricted");
    }
}
