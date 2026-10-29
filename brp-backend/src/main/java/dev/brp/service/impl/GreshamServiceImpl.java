package dev.brp.service.impl;

import dev.brp.entity.JournalEntryEntity;
import dev.brp.repository.JournalEntryRepository;
import dev.brp.service.GreshamService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gresham, REBASELINED (docs/03 §9, docs/12 §10): the emerald rail is the
 * CONSUMER currency, the quota rail is the INDUSTRIAL one players hoard to
 * mint emeralds with. Consumer dominance is Tuesday. The alarm is
 * behavioral — industrial throughput collapsing against its OWN moving
 * baseline while consumer spending holds — spend the bad coin, hoard the
 * good one. Absolute share thresholds fired every week; of course they did.
 */
@Service
public class GreshamServiceImpl implements GreshamService {

    static final String CONSUMER = "emerald";
    static final String INDUSTRIAL = "quota";

    record Signal(boolean alarming, double industrialRatio, double consumerRatio) {}

    private final JournalEntryRepository journal;
    private final PegServiceImpl peg;
    private final int baselineWeeks;
    private final double dropRatio;
    private final double consumerFloor;
    private final ZoneId zone;
    private final AtomicInteger alarmStreak = new AtomicInteger();

    GreshamServiceImpl(JournalEntryRepository journal, PegServiceImpl peg,
                       @Value("${brp.gresham.baseline-weeks:4}") int baselineWeeks,
                       @Value("${brp.gresham.drop-ratio:0.5}") double dropRatio,
                       @Value("${brp.gresham.consumer-floor:1.0}") double consumerFloor,
                       @Value("${brp.reports.zone:UTC}") String zoneId) {
        this.journal = journal;
        this.peg = peg;
        this.baselineWeeks = baselineWeeks;
        this.dropRatio = dropRatio;
        this.consumerFloor = consumerFloor;
        this.zone = ZoneId.of(zoneId);
    }

    @Override
    public Map<String, Double> scan() {
        Map<LocalDate, Map<String, Long>> weeks = bucket();
        Signal signal = evaluate(weeks, baselineWeeks, dropRatio, consumerFloor);
        if (signal.alarming()) {
            if (alarmStreak.incrementAndGet() >= 2) {
                peg.file(903L, signal.industrialRatio());
            }
        } else {
            alarmStreak.set(0);
        }
        return currentShares(weeks);
    }

    /**
     * Pure evaluator: week bucket (Monday-aligned) -> rail -> DEBIT volume.
     * Missing weeks count as zero flow (true silence); a leg with an EMPTY
     * baseline stays silent — no history, no accusation.
     */
    static Signal evaluate(Map<LocalDate, Map<String, Long>> weeks, int baselineWeeks,
                           double dropRatio, double consumerFloor) {
        LocalDate current = weeks.keySet().stream().max(LocalDate::compareTo).orElse(null);
        if (current == null || baselineWeeks <= 0) {
            return new Signal(false, 1, 1);
        }
        long indCur = vol(weeks, current, INDUSTRIAL);
        long conCur = vol(weeks, current, CONSUMER);
        long indBase = 0;
        long conBase = 0;
        for (int i = 1; i <= baselineWeeks; i++) {
            LocalDate w = current.minusWeeks(i);
            indBase += vol(weeks, w, INDUSTRIAL);
            conBase += vol(weeks, w, CONSUMER);
        }
        double indRatio = indBase > 0 ? indCur / ((double) indBase / baselineWeeks) : 1;
        double conRatio = conBase > 0 ? conCur / ((double) conBase / baselineWeeks) : 1;
        boolean industrialCollapsing = indBase > 0 && indRatio <= dropRatio;
        boolean consumerHolding = conBase <= 0 || conRatio >= consumerFloor;
        return new Signal(industrialCollapsing && consumerHolding, indRatio, conRatio);
    }

    private static long vol(Map<LocalDate, Map<String, Long>> weeks, LocalDate w, String leg) {
        return weeks.getOrDefault(w, Map.of()).getOrDefault(leg, 0L);
    }

    /** Bucket the last (baseline+1) weeks of rail-memoed DEBIT volume. */
    private Map<LocalDate, Map<String, Long>> bucket() {
        LocalDate thisWeek = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        Instant since = thisWeek.minusWeeks(baselineWeeks).atStartOfDay(zone).toInstant();
        Map<LocalDate, Map<String, Long>> weeks = new HashMap<>();
        for (JournalEntryEntity e : journal.findByCreatedAtGreaterThanEqual(since)) {
            String memo = e.getMemo();
            if (memo == null || !memo.startsWith("rail:")) {
                continue;
            }
            int secondColon = memo.indexOf(':', 5);
            if (secondColon <= 5) {
                continue;   // "rail:" with no leg is nobody's language
            }
            String leg = memo.substring(5, secondColon);
            LocalDate week = e.getCreatedAt().atZone(zone).toLocalDate().with(DayOfWeek.MONDAY);
            weeks.computeIfAbsent(week, k -> new HashMap<>())
                    .merge(leg, e.getAmountCents(), Long::sum);
        }
        return weeks;
    }

    private Map<String, Double> currentShares(Map<LocalDate, Map<String, Long>> weeks) {
        LocalDate current = LocalDate.now(zone).with(DayOfWeek.MONDAY);
        Map<String, Long> cur = weeks.getOrDefault(current, Map.of());
        long total = cur.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> shares = new HashMap<>();
        for (var entry : cur.entrySet()) {
            shares.put(entry.getKey(), total > 0 ? (double) entry.getValue() / total : 0);
        }
        return shares;
    }

    /** Hourly patrol; prescription stays human. */
    @Scheduled(fixedDelayString = "${brp.gresham.scan-ms:3600000}")
    public void scheduled() {
        scan();
    }
}
