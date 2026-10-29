package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.entity.BondAccrualEntity;
import dev.brp.entity.BondSeriesEntity;
import dev.brp.entity.BondSubscriptionEntity;
import dev.brp.repository.BondAccrualRepository;
import dev.brp.repository.BondSeriesRepository;
import dev.brp.repository.BondSubscriptionRepository;
import dev.brp.repository.CreditLineRepository;
import dev.brp.service.BondService;
import dev.brp.service.LedgerService;
import dev.brp.service.LoadSampleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The bond desk (docs/13 §7). Sign ledger: subscription escrows
 * bond-sink DEBIT / treasury CREDIT (the buyer pays); settlement debits the
 * subscriber's treasury and credits the sink — coupons may drive the sink
 * NEGATIVE, which is the server's bond liability, visible where liabilities
 * belong: in the books, not in a promise.
 */
@Service
public class BondServiceImpl implements BondService {

    private static final String SINK = "bond-sink";
    private static final String TREASURY = "treasury";
    static final double DEFAULT_FACTOR_FLOOR = 0.5;
    static final double MIN_FILL_RATIO = 0.6;

    private final BondSeriesRepository seriesRepo;
    private final BondSubscriptionRepository subs;
    private final BondAccrualRepository accruals;
    private final LedgerService ledger;
    private final LoadSampleService loadSamples;
    private final CreditLineRepository creditLines;
    private final Clock clock;
    private final double factorFloor;
    private final double defaultThreshold;

    public BondServiceImpl(BondSeriesRepository seriesRepo,
                           BondSubscriptionRepository subs,
                           BondAccrualRepository accruals,
                           LedgerService ledger,
                           LoadSampleService loadSamples,
                           CreditLineRepository creditLines,
                           Clock clock,
                           @Value("${brp.bond.threshold-mspt:45}") double defaultThreshold,
                           @Value("${brp.bond.factor-floor:0.5}") double factorFloor) {
        this.seriesRepo = seriesRepo;
        this.subs = subs;
        this.accruals = accruals;
        this.ledger = ledger;
        this.loadSamples = loadSamples;
        this.creditLines = creditLines;
        this.clock = clock;
        this.factorFloor = factorFloor;
        this.defaultThreshold = defaultThreshold;
    }

    @Override
    public BondSeriesEntity issue(String name, long faceCents, int couponPctAnnual,
                                  int termDays, long totalUnits, double thresholdMspt) {
        if (faceCents <= 0 || termDays <= 0 || totalUnits <= 0 || couponPctAnnual < 0 || couponPctAnnual > 1000) {
            throw new BadRequestException("bond.issue.invalid");
        }
        double threshold = thresholdMspt > 0 ? thresholdMspt : defaultThreshold;
        return seriesRepo.save(new BondSeriesEntity(name, faceCents, couponPctAnnual,
                termDays, totalUnits, threshold));
    }

    @Override
    public List<BondSeriesEntity> series() {
        return seriesRepo.findAll();
    }

    @Override
    public BondSeriesEntity get(long seriesId) {
        return seriesRepo.findById(seriesId).orElseThrow(() -> new BadRequestException("bond.unknown"));
    }

    @Override
    public BondSubscriptionEntity subscribe(long seriesId, String tenantId, long units) {
        if (units <= 0) {
            throw new BadRequestException("bond.units.invalid");
        }
        // docs/13 §6: the credit desk blocks new commitments — buying bonds
        // while RESTRICTED is locking the collateral in a drawer
        creditLines.findById(tenantId).ifPresent(line -> {
            String st = line.getStatus();
            if ("RESTRICTED".equals(st) || "DEFAULTED".equals(st)) {
                throw new BadRequestException("credit.restricted");
            }
        });
        BondSeriesEntity series = get(seriesId);
        if (!BondSeriesEntity.OPEN.equals(series.getStatus())) {
            throw new BadRequestException("bond.not.open");
        }
        if (series.getSoldUnits() + units > series.getTotalUnits()) {
            throw new BadRequestException("bond.oversubscribed");
        }
        long filled = Math.multiplyExact(units, series.getFaceCents());
        ensureSink(tenantId);
        ledger.post(tenantId, new PostJournalRequest(
                "bond-sub:" + seriesId + ":" + tenantId, "BRC", List.of(
                new JournalLine(SINK, "DEBIT", filled, null),
                new JournalLine(TREASURY, "CREDIT", filled, null))));
        try {
            BondSubscriptionEntity sub = subs.save(new BondSubscriptionEntity(seriesId, tenantId, units, filled));
            series.addUnits(units);
            seriesRepo.save(series);
            return sub;
        } catch (DataIntegrityViolationException duplicate) {
            // one position per tenant per series: replay the escrow (it was keyed identically)
            throw new BadRequestException("bond.already.subscribed");
        }
    }

    @Override
    public BondSeriesEntity close(long seriesId) {
        BondSeriesEntity series = get(seriesId);
        if (!BondSeriesEntity.OPEN.equals(series.getStatus())) {
            throw new BadRequestException("bond.not.open");
        }
        series.close();
        series = seriesRepo.save(series);
        if (BondSeriesEntity.FAILED.equals(series.getStatus())) {
            for (BondSubscriptionEntity sub : subs.findBySeriesIdOrderByIdAsc(seriesId)) {
                refund(seriesId, sub);
            }
        }
        return series;
    }

    @Override
    @Scheduled(cron = "${brp.bond.accrual-cron:0 45 0 * * *}", zone = "${brp.reports.zone:UTC}")
    public boolean accrueDaily() {
        boolean any = false;
        for (BondSeriesEntity series : seriesRepo.findAll()) {
            if (!BondSeriesEntity.AWARDED.equals(series.getStatus())) {
                continue;
            }
            LocalDate today = LocalDate.now(clock);
            for (BondSubscriptionEntity sub : subs.findBySeriesIdOrderByIdAsc(series.getId())) {
                long day = BondMath.couponPerDay(sub.getUnits(), series.getFaceCents(),
                        series.getCouponPctAnnual());
                if (day <= 0) {
                    continue;
                }
                try {
                    accruals.saveAndFlush(new BondAccrualEntity(series.getId(), sub.getTenantId(), today, day));
                    any = true;
                } catch (DataIntegrityViolationException alreadyAccrued) {
                    // the PK is the idempotency key, as paperwork should be
                }
            }
        }
        return any;
    }

    @Override
    @Scheduled(cron = "${brp.bond.settle-cron:0 55 0 * * *}", zone = "${brp.reports.zone:UTC}")
    public List<Long> settleMatured() {
        List<Long> settled = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (BondSeriesEntity series : seriesRepo.findAll()) {
            if (!BondSeriesEntity.AWARDED.equals(series.getStatus()) || series.getMaturesAt() == null) {
                continue;
            }
            if (series.getMaturesAt().toLocalDateTime().isAfter(now)) {
                continue;
            }
            settle(series);
            settled.add(series.getId());
        }
        return settled;
    }

    // --- internals ---

    private void settle(BondSeriesEntity series) {
        double avgMspt = loadSamples.avgMsptBetween(
                series.getIssuedAt().toLocalDateTime(), series.getMaturesAt().toLocalDateTime());
        double factor = BondMath.performanceFactor(avgMspt, series.getThresholdMspt(), factorFloor);
        for (BondSubscriptionEntity sub : subs.findBySeriesIdOrderByIdAsc(series.getId())) {
            long accrued = accruals.findBySeriesIdAndTenantId(series.getId(), sub.getTenantId()).stream()
                    .mapToLong(BondAccrualEntity::getAmountCents).sum();
            long couponPaid = Math.round(accrued * factor);
            ensureSink(sub.getTenantId());
            if (couponPaid > 0) {
                ledger.post(sub.getTenantId(), new PostJournalRequest(
                        "bond-coupon:" + series.getId() + ":" + sub.getTenantId(), "BRC", List.of(
                        new JournalLine(TREASURY, "DEBIT", couponPaid, null),
                        new JournalLine(SINK, "CREDIT", couponPaid, null))));
            }
            if (sub.getFilledCents() > 0) {
                ledger.post(sub.getTenantId(), new PostJournalRequest(
                        "bond-principal:" + series.getId() + ":" + sub.getTenantId(), "BRC", List.of(
                        new JournalLine(TREASURY, "DEBIT", sub.getFilledCents(), null),
                        new JournalLine(SINK, "CREDIT", sub.getFilledCents(), null))));
            }
            sub.settle(couponPaid, sub.getFilledCents());
            subs.save(sub);
        }
        series.markRedeemed();
        seriesRepo.save(series);
    }

    private void refund(long seriesId, BondSubscriptionEntity sub) {
        if (sub.getFilledCents() <= 0) {
            return;
        }
        ensureSink(sub.getTenantId());
        ledger.post(sub.getTenantId(), new PostJournalRequest(
                "bond-refund:" + seriesId + ":" + sub.getTenantId(), "BRC", List.of(
                new JournalLine(TREASURY, "DEBIT", sub.getFilledCents(), null),
                new JournalLine(SINK, "CREDIT", sub.getFilledCents(), null))));
    }

    private void ensureSink(String tenantId) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest(SINK, "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // the sink predates the bond, as sinks do
        }
    }
}
