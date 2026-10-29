package dev.brp.service.impl;

import dev.brp.entity.JournalEntryEntity;
import dev.brp.repository.JournalEntryRepository;
import dev.brp.service.GreshamService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Legs are PAYMENT RAILS (docs/03 §9), never item movement: 7-day window
 * over journals memoed rail:emerald: / rail:quota:. One leg over 95% files
 * BRP-0903 and the crisis is declared — the prescription stays human.
 */
@Service
public class GreshamServiceImpl implements GreshamService {

    private final JournalEntryRepository journal;
    private final PegServiceImpl peg;   // for the 0903 filing helper

    GreshamServiceImpl(JournalEntryRepository journal, PegServiceImpl peg) {
        this.journal = journal;
        this.peg = peg;
    }

    @Override
    public Map<String, Double> scan() {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        Map<String, Long> volume = new HashMap<>();
        for (JournalEntryEntity e : journal.findByCreatedAtGreaterThanEqual(since)) {
            String memo = e.getMemo();
            if (memo != null && memo.startsWith("rail:")) {
                String leg = memo.substring(5, memo.indexOf(':', 5));
                volume.merge(leg, e.getAmountCents(), Long::sum);
            }
        }
        long total = volume.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Double> shares = new HashMap<>();
        for (var entry : volume.entrySet()) {
            double share = total > 0 ? (double) entry.getValue() / total : 0;
            shares.put(entry.getKey(), share);
            if (share > 0.95) {
                peg.file(903L, share);
            }
        }
        return shares;
    }
}
