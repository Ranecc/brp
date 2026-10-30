package dev.brp.service.impl;

import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ExchangeDtos.MintStateResponse;
import dev.brp.entity.AccountEntity;
import dev.brp.entity.ExchangeItemEntity;
import dev.brp.entity.WarehouseRowEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.WarehouseRepository;
import dev.brp.service.ExchangeMintService;
import dev.brp.service.ExchangeWarehouseService;
import dev.brp.service.LedgerService;
import dev.brp.service.impl.AuctionMath.Quote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The listed mint (docs/14 §4). Quotes derive from the reference and the
 * spread; quantities derive from the reserve — depth, warehouse stock, and
 * the treasury plus whatever credit the owner extended. The daily sweep is
 * brassage made visible: the spread goes to the sink, the float stays.
 */
@Service
public class ExchangeMintServiceImpl implements ExchangeMintService {

    public static final String TENANT = "mint";
    static final String TREASURY = "treasury";
    static final String SINK = "expense-sink";
    /** Mint quotes yield time priority to every player order in the batch. */
    static final long MINT_SEQ = Long.MAX_VALUE / 2;

    private final AccountRepository accounts;
    private final WarehouseRepository warehouse;
    private final ExchangeWarehouseService warehouseService;
    private final LedgerService ledger;
    private final Clock clock;
    private final int spreadBips;
    private final long depth;
    private final long creditCents;
    private final long floatCents;
    private final String zone;

    public ExchangeMintServiceImpl(AccountRepository accounts,
                                   WarehouseRepository warehouse,
                                   ExchangeWarehouseService warehouseService,
                                   LedgerService ledger,
                                   Clock clock,
                                   @Value("${brp.exchange.mint-spread-bips:200}") int spreadBips,
                                   @Value("${brp.exchange.mint-depth:64}") long depth,
                                   @Value("${brp.exchange.mint-credit-cents:0}") long creditCents,
                                   @Value("${brp.exchange.mint-float-cents:0}") long floatCents,
                                   @Value("${brp.reports.zone:UTC}") String zone) {
        this.accounts = accounts;
        this.warehouse = warehouse;
        this.warehouseService = warehouseService;
        this.ledger = ledger;
        this.clock = clock;
        this.spreadBips = spreadBips;
        this.depth = depth;
        this.creditCents = creditCents;
        this.floatCents = floatCents;
        this.zone = zone;
    }

    @Override
    public Quote bidQuote(ExchangeItemEntity item) {
        long price = item.mintBidCents(spreadBips);
        if (price <= 0) {
            return null;   // a mispriced reference posts no bid
        }
        long affordable = treasury() + creditCents;
        long qty = Math.min(depth, affordable / price);
        return qty > 0 ? new Quote(MINT_SEQ, TENANT, null, qty, price) : null;
    }

    @Override
    public Quote askQuote(ExchangeItemEntity item) {
        long price = item.mintAskCents(spreadBips);
        long free = warehouse.findById(new WarehouseRowEntity.Pk(TENANT, item.getItemId()))
                .map(WarehouseRowEntity::getQtyFree).orElse(0L);
        long qty = Math.min(depth, free);
        return qty > 0 ? new Quote(MINT_SEQ, TENANT, null, qty, price) : null;
    }

    @Override
    public MintStateResponse state() {
        long stock = warehouse.findByTenantId(TENANT).stream()
                .mapToLong(WarehouseRowEntity::getQtyFree).sum();
        return new MintStateResponse(TENANT, floatCents, treasury(), stock, spreadBips,
                Math.toIntExact(depth));
    }

    @Override
    public void ensureAccounts() {
        ensureAccount(TENANT, TREASURY, "SYSTEM_FAUCET");
        ensureAccount(TENANT, SINK, "SYSTEM_SINK");
    }

    @Override
    @Scheduled(cron = "0 30 0 * * *", zone = "${brp.reports.zone:UTC}")
    public void sweepSeigniorage() {
        ensureAccounts();
        long excess = treasury() - floatCents;
        if (excess <= 0) {
            return;   // nothing captured, or the owner keeps a working float
        }
        ledger.post(TENANT, new PostJournalRequest(
                "mint-seigniorage:" + LocalDate.now(clock),
                "BRC", List.of(
                new JournalLine(SINK, "DEBIT", excess, "seigniorage"),
                new JournalLine(TREASURY, "CREDIT", excess, "seigniorage"))));
    }

    private long treasury() {
        return accounts.findByTenantIdAndAccountNo(TENANT, TREASURY)
                .map(AccountEntity::getBalanceCents).orElse(0L);
    }

    private void ensureAccount(String tenantId, String accountNo, String type) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest(accountNo, type, "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // sinks are patient
        }
    }
}
