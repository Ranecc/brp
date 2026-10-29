package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.LoadDtos.CreditStatusResponse;
import dev.brp.entity.CreditAccrualRunEntity;
import dev.brp.entity.CreditEventEntity;
import dev.brp.entity.CreditLineEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.CreditAccrualRunRepository;
import dev.brp.repository.CreditEventRepository;
import dev.brp.repository.CreditLineRepository;
import dev.brp.repository.JournalEntryRepository;
import dev.brp.service.ChunkService;
import dev.brp.service.CreditService;
import dev.brp.service.DemandResponseService;
import dev.brp.service.LedgerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The credit desk (docs/13 §6). Debt EMERGES: a negative treasury is swept
 * into drawn once a day, priced at simple interest (compound is a wanted
 * offense here), and gated by a limit. The ledger never mints — the sweep
 * moves existing BRC from the (SYSTEM) lender leg; zero-mint invariant holds.
 */
@Service
public class CreditServiceImpl implements CreditService {

    private static final String TREASURY = "treasury";
    private static final String LENDER = "credit-lender";

    private final CreditLineRepository lines;
    private final CreditEventRepository events;
    private final CreditAccrualRunRepository runs;
    private final AccountRepository accounts;
    private final JournalEntryRepository journal;
    private final LedgerService ledger;
    private final DemandResponseService demandResponse;
    private final ChunkService chunks;   // @Lazy: the cutoff closes collateral; Chunk -> repo only, never back
    private final Clock clock;
    private final double watchAt;
    private final double restrictAt;
    private final int graceDays;
    private final double limitFactor;
    private final long limitFloorCents;
    private final int defaultRatePct;

    public CreditServiceImpl(CreditLineRepository lines,
                             CreditEventRepository events,
                             CreditAccrualRunRepository runs,
                             AccountRepository accounts,
                             JournalEntryRepository journal,
                             LedgerService ledger,
                             DemandResponseService demandResponse,
                             @Lazy ChunkService chunks,
                             Clock clock,
                             @Value("${brp.credit.utilization-watch:0.8}") double watchAt,
                             @Value("${brp.credit.utilization-restrict:1.0}") double restrictAt,
                             @Value("${brp.credit.grace-days:7}") int graceDays,
                             @Value("${brp.credit.limit-factor:0.25}") double limitFactor,
                             @Value("${brp.credit.limit-floor-cents:50000}") long limitFloorCents,
                             @Value("${brp.credit.annual-rate-percent:12}") int defaultRatePct) {
        this.lines = lines;
        this.events = events;
        this.runs = runs;
        this.accounts = accounts;
        this.journal = journal;
        this.ledger = ledger;
        this.demandResponse = demandResponse;
        this.chunks = chunks;
        this.clock = clock;
        this.watchAt = watchAt;
        this.restrictAt = restrictAt;
        this.graceDays = graceDays;
        this.limitFactor = limitFactor;
        this.limitFloorCents = limitFloorCents;
        this.defaultRatePct = defaultRatePct;
    }

    /** Daily simple interest, floor-rounded — the debtor-friendly rounding. */
    static long interestFor(long drawnCents, int annualRatePct) {
        if (drawnCents <= 0 || annualRatePct <= 0) {
            return 0;
        }
        try {
            return Math.multiplyExact(drawnCents, annualRatePct) / 36_500L;
        } catch (ArithmeticException overflow) {
            throw BadRequestException.of("credit.overflow");
        }
    }

    @Override
    public CreditStatusResponse status(String tenantId) {
        return toDto(line(tenantId));
    }

    @Override
    public CreditStatusResponse setLimit(String tenantId, long limitCents) {
        if (limitCents < 0) {
            throw BadRequestException.of("credit.limit.invalid");
        }
        CreditLineEntity line = line(tenantId);
        line.setLimit(limitCents, true);
        lines.save(line);
        event(line.getTenantId(), CreditEventEntity.LIMIT_SET, limitCents, "limit pinned by admin");
        return recompute(line);
    }

    @Override
    public CreditStatusResponse repay(String tenantId, long amountCents) {
        if (amountCents <= 0) {
            throw BadRequestException.of("credit.repay.invalid");
        }
        CreditLineEntity line = line(tenantId);
        if (line.getDrawnCents() <= 0) {
            throw BadRequestException.of("credit.nothing.drawn");
        }
        long cash = accounts.findByTenantIdAndAccountNo(tenantId, TREASURY)
                .map(a -> a.getBalanceCents()).orElse(0L);
        long payable = Math.min(amountCents, cash);
        if (payable <= 0) {
            throw BadRequestException.of("credit.no.cash");
        }
        ensureLender(tenantId);
        ledger.post(tenantId, new PostJournalRequest(
                "credit-repay:" + tenantId + ":" + java.util.UUID.randomUUID(), "BRC", List.of(
                new JournalLine(LENDER, "DEBIT", payable, null),
                new JournalLine(TREASURY, "CREDIT", payable, null))));
        long applied = line.repay(payable);
        lines.save(line);
        event(tenantId, CreditEventEntity.REPAY, applied, "repaid from treasury");
        return recompute(line);
    }

    @Override
    public CreditStatusResponse reinstate(String tenantId) {
        CreditLineEntity line = line(tenantId);
        if (!CreditLineEntity.DEFAULTED.equals(line.getStatus())) {
            throw BadRequestException.of("credit.not.defaulted");
        }
        line.reinstate();
        lines.save(line);
        event(tenantId, CreditEventEntity.STATUS, 0, "REINSTATE -> WATCH");
        return recompute(line);
    }

    @Override
    public boolean blocked(String tenantId) {
        return lines.findById(tenantId)
                .map(l -> CreditLineEntity.RESTRICTED.equals(l.getStatus())
                        || CreditLineEntity.DEFAULTED.equals(l.getStatus()))
                .orElse(false);
    }

    @Override
    public List<CreditEventEntity> events(String tenantId) {
        return events.findByTenantIdOrderByIdDesc(tenantId);
    }

    /**
     * The daily desk (docs/13 §6): sweep, interest, status, default. Guarded
     * insert-only per date; the guard commits alone (no rollback-only traps).
     */
    @Override
    @Scheduled(cron = "${brp.credit.accrual-cron:0 40 0 * * *}", zone = "${brp.reports.zone:UTC}")
    public boolean runDailyDesk() {
        LocalDate today = LocalDate.now(clock);
        try {
            runs.saveAndFlush(new CreditAccrualRunEntity(today));
        } catch (DataIntegrityViolationException alreadyRan) {
            return false;
        }
        sweepOverdrafts(today);
        accrueInterest();
        walkStatusMachine(today);
        return true;
    }

    @Override
    @Scheduled(cron = "${brp.credit.limit-cron:0 50 1 ? * MON}", zone = "${brp.reports.zone:UTC}")
    public void recomputeLimits() {
        Instant since = Instant.now().minus(Duration.ofDays(30));
        for (CreditLineEntity line : lines.findAll()) {
            if (line.isLimitPinned()) {
                continue;
            }
            long income = trailingIncome(line.getTenantId(), since);
            long want = Math.max(limitFloorCents, (long) (income * limitFactor));
            if (want != line.getLimitCents()) {
                line.setLimit(want, false);
                lines.save(line);
                event(line.getTenantId(), CreditEventEntity.LIMIT_SET, want,
                        "auto: 30d income x factor, weekly");
            }
        }
    }

    // --- internals ---

    private CreditLineEntity line(String tenantId) {
        return lines.findById(tenantId).orElseGet(() -> {
            long auto = Math.max(limitFloorCents,
                    (long) (trailingIncome(tenantId, Instant.now().minus(Duration.ofDays(30))) * limitFactor));
            CreditLineEntity created = lines.save(new CreditLineEntity(tenantId, auto, defaultRatePct));
            event(tenantId, CreditEventEntity.LIMIT_SET, auto, "auto limit on first look");
            return created;
        });
    }

    /** Negative treasuries become DRAWN debt, up to the available line. */
    private void sweepOverdrafts(LocalDate today) {
        for (var acc : accounts.findByAccountNoAndBalanceCentsLessThan(TREASURY, 0)) {
            long overdraft = -acc.getBalanceCents();
            CreditLineEntity line = line(acc.getTenantId());
            long available = line.getLimitCents() - line.getDrawnCents();
            long cover = Math.min(overdraft, Math.max(available, 0));
            if (cover <= 0) {
                continue;   // the hole stays visible; the status machine escalates
            }
            ensureLender(acc.getTenantId());
            // DEBIT is +: the sweep REFILLS the overdrafted treasury; the
            // lender leg accumulates the outstanding loan on CREDIT.
            ledger.post(acc.getTenantId(), new PostJournalRequest(
                    "credit-sweep:" + acc.getTenantId() + ":" + today, "BRC", List.of(
                    new JournalLine(TREASURY, "DEBIT", cover, null),
                    new JournalLine(LENDER, "CREDIT", cover, null))));
            line.draw(cover);
            lines.save(line);
            event(acc.getTenantId(), CreditEventEntity.DRAW, cover, "overdraft swept");
            recompute(line);
        }
    }

    private void accrueInterest() {
        for (CreditLineEntity line : lines.findAll()) {
            long interest = interestFor(line.getDrawnCents(), line.getAnnualRatePct());
            if (interest > 0) {
                line.addInterest(interest);
                lines.save(line);
                event(line.getTenantId(), CreditEventEntity.INTEREST, interest,
                        "daily simple interest, day count 365");
            }
        }
    }

    private void walkStatusMachine(LocalDate today) {
        for (CreditLineEntity line : lines.findAll()) {
            if (CreditLineEntity.DEFAULTED.equals(line.getStatus())) {
                continue;
            }
            if (CreditLineEntity.RESTRICTED.equals(line.getStatus()) && line.getRestrictedSince() != null) {
                Instant deadline = line.getRestrictedSince().toInstant().plus(Duration.ofDays(graceDays));
                if (Instant.now(clock).isAfter(deadline)) {
                    line.noteDefaulted();
                    lines.save(line);
                    event(line.getTenantId(), CreditEventEntity.STATUS, 0,
                            "DEFAULTED: grace of " + graceDays + "d expired");
                    continue;
                }
            }
            recompute(line);
        }
    }

    /** Recompute the status; escalate cutoff actions on a fresh RESTRICTED. */
    private CreditStatusResponse recompute(CreditLineEntity line) {
        boolean wasRestricted = CreditLineEntity.RESTRICTED.equals(line.getStatus());
        String moved = line.transitionTo(watchAt, restrictAt);
        if (moved != null) {
            lines.save(line);
            event(line.getTenantId(), CreditEventEntity.STATUS, 0, "-> " + moved);
            if (CreditLineEntity.RESTRICTED.equals(moved) && !wasRestricted) {
                cutoff(line.getTenantId());
            }
        }
        return toDto(line);
    }

    /**
     * The teeth (docs/13 §6): the P2 promise holds — movement and play stay
     * free. The collateral closes, flexible work suspends, new commitments
     * (chunk purchases, market orders) are refused at the desk.
     */
    private void cutoff(String tenantId) {
        try {
            chunks.closeAllForCredit(tenantId);
        } catch (RuntimeException noChunks) {
            // no collateral to close is the cheapest kind of bankruptcy
        }
        demandResponse.suspendTenant(tenantId);
        event(tenantId, CreditEventEntity.STATUS, 0, "cutoff: chunks closed, DR suspended");
    }

    private long trailingIncome(String tenantId, Instant since) {
        var treasury = accounts.findByTenantIdAndAccountNo(tenantId, TREASURY);
        if (treasury.isEmpty()) {
            return 0;
        }
        return journal.findByTenantIdAndAccountIdAndDirectionAndCurrencyAndCreatedAtGreaterThanEqual(
                        tenantId, treasury.get().getId(), "DEBIT", "BRC", since).stream()
                .mapToLong(e -> e.getAmountCents()).sum();
    }

    private void ensureLender(String tenantId) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest(LENDER, "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // the lender predates the debt, as lenders do
        }
    }

    private void event(String tenantId, String kind, long amountCents, String memo) {
        events.save(new CreditEventEntity(tenantId, kind, amountCents, memo));
    }

    private static CreditStatusResponse toDto(CreditLineEntity l) {
        long available = l.getLimitCents() - l.getDrawnCents();
        double utilPct = l.utilization() == Double.MAX_VALUE
                ? Double.MAX_VALUE : Math.round(l.utilization() * 10_000.0) / 100.0;
        return new CreditStatusResponse(l.getTenantId(), l.getLimitCents(), l.getDrawnCents(),
                available, utilPct, l.getStatus(),
                String.valueOf(l.getStatusSince()), l.getAnnualRatePct(), l.isLimitPinned());
    }
}
