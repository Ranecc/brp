package dev.brp.service.impl;

import dev.brp.common.ConflictException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.entity.LevyMinuteEntity;
import dev.brp.entity.PolicyRegimeEntity;
import dev.brp.repository.LevyMinuteRepository;
import dev.brp.repository.PolicyRegimeRepository;
import dev.brp.service.ComputeLevyService;
import dev.brp.service.LedgerService;
import dev.brp.service.RegimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Marginal pricing, income-tax semantics (docs/03 §9): the first share
 * point over a bracket pays the next bracket's rate, so parking at 49.9%
 * forever is not a strategy. Bands: 0-20% @1.0x | 20-35% @1.2x |
 * 35-50% @1.6x | >50% @2.5x — applied as a share-weighted multiplier over
 * the tenant's own units.
 */
@Service
public class ComputeLevyServiceImpl implements ComputeLevyService {

    private static final DateTimeFormatter MINUTE_KEY = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final LevyMinuteRepository minutes;
    private final PolicyRegimeRepository regimes;
    private final RegimeService regime;
    private final LedgerService ledger;
    private final TransactionTemplate tx;
    private final PegServiceImpl peg;
    private final java.util.concurrent.atomic.AtomicInteger failureStreak =
            new java.util.concurrent.atomic.AtomicInteger();
    private final double baseEmeraldsPerMinute;
    private final double brcPerEmerald;

    public ComputeLevyServiceImpl(LevyMinuteRepository minutes,
                                  PolicyRegimeRepository regimes,
                                  RegimeService regime,
                                  LedgerService ledger,
                                  TransactionTemplate tx,
                                  PegServiceImpl peg,
                                  @Value("${brp.levy.compute.base-emeralds-per-minute:0}") double baseEmeraldsPerMinute,
                                  @Value("${brp.peg.emerald.brc-per-emerald:1.0}") double brcPerEmerald) {
        this.minutes = minutes;
        this.regimes = regimes;
        this.regime = regime;
        this.ledger = ledger;
        this.tx = tx;
        this.peg = peg;
        this.baseEmeraldsPerMinute = baseEmeraldsPerMinute;
        this.brcPerEmerald = brcPerEmerald;
    }

    @Override
    public void recordUsage(String tenantId, double minutesRaw, LocalDateTime at) {
        double usage = Math.min(Math.max(minutesRaw, 0), 10_080);
        if (usage <= 0) {
            return;
        }
        LocalDateTime bucket = at.withSecond(0).withNano(0);
        tx.executeWithoutResult(status -> {
            LevyMinuteEntity row = minutes.findForUpdate(bucket, tenantId);
            if (row == null) {
                try {
                    minutes.saveAndFlush(new LevyMinuteEntity(bucket, tenantId, usage));
                } catch (org.springframework.dao.DataIntegrityViolationException raced) {
                    row = minutes.findForUpdate(bucket, tenantId);
                    if (row != null) {
                        row.addUnits(usage);
                        minutes.save(row);
                    }
                }
            } else {
                row.addUnits(usage);
                minutes.save(row);
            }
        });
    }

    @Override
    public int materialize(LocalDateTime bucket) {
        List<LevyMinuteEntity> rows = minutes.findByMinuteBucket(bucket);
        if (rows.isEmpty()) {
            return 0;
        }
        double total = rows.stream().mapToDouble(LevyMinuteEntity::getUnits).sum();
        boolean armed = regime.active() && baseEmeraldsPerMinute > 0;
        int posted = 0;
        for (LevyMinuteEntity row : rows) {
            if (row.isPosted()) {
                continue;   // one price per tenant-minute, replays are free
            }
            double share = total > 0 ? row.getUnits() / total : 0;
            long amount = armed ? amount(row.getUnits(), share) : 0;
            try {
                if (amount > 0) {
                    post(row.getTenantId(), bucket, amount);
                }
            } catch (RuntimeException postFailed) {
                // the row stays unposted (next pass retries); a five-streak
                // means the desk itself is broken — BRP-0906, warn a human
                if (failureStreak.incrementAndGet() >= 5) {
                    peg.file(906L, failureStreak.get());
                }
                continue;
            }
            failureStreak.set(0);
            row.assess(share, amount);
            minutes.save(row);
            posted++;
        }
        return posted;
    }

    /** units × emerald base × marginal band multiplier, in BRC cents. */
    long amount(double units, double share) {
        double mult = effectiveMultiplier(share);
        return Math.round(units * baseEmeraldsPerMinute * mult * brcPerEmerald * 100.0);
    }

    /** Share-weighted average band rate — income-tax semantics, no cliffs. */
    public static double effectiveMultiplier(double share) {
        double[][] bands = {{0.20, 1.0}, {0.35, 1.2}, {0.50, 1.6}, {1.00, 2.5}};
        if (share <= 0) {
            return 0.0;
        }
        double lo = 0;
        double weighted = 0;
        for (double[] band : bands) {
            double hi = Math.min(share, band[0]);
            if (hi > lo) {
                weighted += (hi - lo) * band[1];
            }
            lo = band[0];
            if (share <= band[0]) {
                break;
            }
        }
        return weighted / share;
    }

    private void post(String tenantId, LocalDateTime bucket, long amountCents) {
        ensureSink(tenantId);
        ledger.post(tenantId, new PostJournalRequest(
                "tax:" + tenantId + ":" + MINUTE_KEY.format(bucket), "BRC", List.of(
                        new JournalLine("expense-sink", "DEBIT", amountCents, null),
                        new JournalLine("treasury", "CREDIT", amountCents, null))));
    }

    private void ensureSink(String tenantId) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest("expense-sink", "SYSTEM_SINK", "BRC"));
        } catch (ConflictException alreadyThere) {
            // sinks are patient
        }
    }

    /** Every minute, price the minute that just closed. */
    @Scheduled(fixedDelayString = "${brp.levy.compute.materialize-ms:60000}")
    public void materializeLastMinute() {
        materialize(LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0));
    }
}
