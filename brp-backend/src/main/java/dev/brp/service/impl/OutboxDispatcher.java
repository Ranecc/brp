package dev.brp.service.impl;

import dev.brp.entity.OutboxMessageEntity;
import dev.brp.repository.OutboxRepository;
import dev.brp.service.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Drains the outbox at-least-once (docs/06 section 3), now for real:
 *
 * - CLAIM before send (audit #2): PENDING -> SENDING via a compare-and-set
 *   update, so two dispatcher instances never double-send the same row.
 * - Per-message commits, NO batch transaction: a crash mid-batch keeps the
 *   rows already marked DELIVERED (the old class-level @Transactional was
 *   dead anyway — scheduledDrain() bypasses the proxy, and honestly the
 *   all-or-nothing rollback would have un-marked deliveries, which is worse).
 * - The sweeper exists now (the comment promised one for a whole P1):
 *   FAILED rows requeue after a backoff until attempts run out, then stay
 *   FAILED for the humans.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepository outbox;
    private final WebhookSender sender;
    private final int maxAttempts;
    private final long backoffSeconds;

    public OutboxDispatcher(OutboxRepository outbox, WebhookSender sender,
                            @Value("${brp.outbox.max-attempts:10}") int maxAttempts,
                            @Value("${brp.outbox.backoff-seconds:60}") long backoffSeconds) {
        this.outbox = outbox;
        this.sender = sender;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
    }

    @Scheduled(fixedDelayString = "${brp.outbox.poll-ms:2000}")
    public void scheduledDrain() {
        drainOnce();
    }

    public int drainOnce() {
        int delivered = 0;
        for (int i = 0; i < 20; i++) {
            Optional<OutboxMessageEntity> next = outbox.findFirstByStatusOrderByIdAsc("PENDING");
            if (next.isEmpty()) {
                break;
            }
            OutboxMessageEntity msg = next.get();
            if (outbox.claim(msg.getId(), Instant.now()) == 0) {
                continue;   // another instance claimed it first — walk on
            }
            boolean ok = sender.send(msg.getEventType(), msg.getPayload());
            OutboxMessageEntity fresh = outbox.findById(msg.getId()).orElse(null);
            if (fresh == null) {
                continue;
            }
            if (ok) {
                fresh.delivered();
                delivered++;
            } else {
                fresh.failed();
            }
            outbox.save(fresh);
        }
        return delivered;
    }

    /** FAILED -> PENDING past the backoff, until attempts run out (audit #2). */
    @Scheduled(fixedDelayString = "${brp.outbox.sweep-ms:60000}")
    public int sweepFailed() {
        Instant now = Instant.now();
        Instant before = now.minusSeconds(backoffSeconds);
        int requeued = outbox.requeueFailed(maxAttempts, before, now);
        if (requeued > 0) {
            log.info("outbox sweeper requeued {} FAILED rows (max attempts {})",
                    requeued, maxAttempts);
        }
        return requeued;
    }
}
