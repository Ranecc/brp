package dev.brp.service.impl;

import dev.brp.common.ConflictException;
import dev.brp.dto.MonetaryDtos.SnapshotResponse;
import dev.brp.dto.MonetaryDtos.SnapshotTenantRow;
import dev.brp.entity.AccountEntity;
import dev.brp.entity.JournalEntryEntity;
import dev.brp.entity.SnapshotTenantEntity;
import dev.brp.entity.WeeklySnapshotEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.JournalEntryRepository;
import dev.brp.repository.SnapshotTenantRepository;
import dev.brp.repository.WeeklySnapshotRepository;
import dev.brp.service.RegimeService;
import dev.brp.service.SnapshotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared weekly snapshot (docs/12 §3): wealth = treasury balance,
 * velocity = week's DEBIT volume / wealth (zero volume is honest hoarding),
 * Gini across tenant wealths with zero-wealth tenants counted — the
 * denominator is the truth, not the marketing.
 */
@Service
public class SnapshotServiceImpl implements SnapshotService {

    private final WeeklySnapshotRepository snapshots;
    private final SnapshotTenantRepository tenants;
    private final AccountRepository accounts;
    private final JournalEntryRepository journal;
    private final RegimeService regime;
    private final ZoneId zone;

    public SnapshotServiceImpl(WeeklySnapshotRepository snapshots,
                               SnapshotTenantRepository tenants,
                               AccountRepository accounts,
                               JournalEntryRepository journal,
                               RegimeService regime,
                               @Value("${brp.reports.zone:UTC}") String zoneId) {
        this.snapshots = snapshots;
        this.tenants = tenants;
        this.accounts = accounts;
        this.journal = journal;
        this.regime = regime;
        this.zone = ZoneId.of(zoneId);
    }

    @Override
    public SnapshotResponse capture() {
        LocalDate weekOf = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        Instant since = weekOf.minusDays(7).atStartOfDay(zone).toInstant();

        Map<String, long[]> money = new HashMap<>();   // [wealth, volume]
        for (AccountEntity a : accounts.findAll()) {
            if ("treasury".equals(a.getAccountNo())) {
                money.computeIfAbsent(a.getTenantId(), k -> new long[2])[0] += a.getBalanceCents();
            }
        }
        for (JournalEntryEntity e : journal.findByCreatedAtGreaterThanEqual(since)) {
            if ("DEBIT".equals(e.getDirection())) {
                money.computeIfAbsent(e.getTenantId(), k -> new long[2])[1] += e.getAmountCents();
            }
        }

        List<SnapshotTenantEntity> rows = new ArrayList<>();
        List<Double> wealths = new ArrayList<>();
        double total = 0;
        double max = 0;
        for (var entry : money.entrySet()) {
            long wealth = entry.getValue()[0];
            long volume = entry.getValue()[1];
            double velocity = wealth <= 0 ? 0 : (double) volume / wealth;
            rows.add(new SnapshotTenantEntity(weekOf.toString(), entry.getKey(), wealth, velocity));
            wealths.add((double) wealth);
            total += wealth;
            max = Math.max(max, wealth);
        }
        double gini = gini(wealths);
        double topShare = total > 0 ? max / total : 0.0;

        if (snapshots.existsById(weekOf)) {
            throw ConflictException.of("monetary.snapshot.duplicate");   // one truth per week
        }
        String stateAfter = regime.afterSnapshot(gini, topShare);
        try {
            snapshots.saveAndFlush(new WeeklySnapshotEntity(weekOf, gini, topShare, stateAfter));
            rows.forEach(tenants::save);
        } catch (DataIntegrityViolationException race) {
            throw ConflictException.of("monetary.snapshot.duplicate");   // concurrent capture lost the race, rightly
        }
        return new SnapshotResponse(weekOf.toString(), gini, topShare, stateAfter,
                rows.stream().map(r -> new SnapshotTenantRow(r.getTenantId(),
                        r.getWealthCents(), r.getVelocity())).toList());
    }

    @Override
    public SnapshotResponse latest() {
        var all = snapshots.findAll();
        return all.isEmpty() ? null
                : toDto(all.stream().max(java.util.Comparator.comparing(WeeklySnapshotEntity::getWeekOf)).orElseThrow());
    }

    private SnapshotResponse toDto(WeeklySnapshotEntity s) {
        return new SnapshotResponse(s.getWeekOf().toString(), s.getGini(), s.getTopShare(),
                s.getRegimeAfter(),
                tenants.findByWeekOf(s.getWeekOf().toString()).stream()
                        .map(t -> new SnapshotTenantRow(t.getTenantId(), t.getWealthCents(), t.getVelocity()))
                        .toList());
    }

    /** Standard Gini over the population INCLUDING zero-wealth tenants. */
    static double gini(List<Double> xs) {
        int n = xs.size();
        if (n < 2) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(xs);
        java.util.Collections.sort(sorted);
        double mean = sorted.stream().mapToDouble(Double::doubleValue).sum() / n;
        if (mean <= 0) {
            return 0.0;
        }
        double mad = 0;
        for (double a : sorted) {
            for (double b : sorted) {
                mad += Math.abs(a - b);
            }
        }
        return mad / (2.0 * n * n * mean);
    }

    /** Weekly, right after the books close on Sunday night somewhere. */
    @Scheduled(cron = "${brp.snapshot.cron:0 5 0 ? * MON}", zone = "${brp.reports.zone:UTC}")
    public void scheduled() {
        capture();
    }
}
