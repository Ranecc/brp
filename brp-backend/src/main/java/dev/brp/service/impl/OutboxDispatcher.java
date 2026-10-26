package dev.brp.service.impl;

import dev.brp.entity.OutboxMessageEntity;
import dev.brp.repository.OutboxRepository;
import dev.brp.service.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Drains the outbox at-least-once (docs/06 section 3). Failures mark FAILED
 * with attempts counted; a P1 sweeper requeues FAILED rows past a backoff.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxRepository outbox;
    private final WebhookSender sender;

    public OutboxDispatcher(OutboxRepository outbox, WebhookSender sender) {
        this.outbox = outbox;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${brp.outbox.poll-ms:2000}")
    public void scheduledDrain() {
        drainOnce();
    }

    @Transactional
    public int drainOnce() {
        List<OutboxMessageEntity> batch = outbox.findTop20ByStatusOrderByIdAsc("PENDING");
        int delivered = 0;
        for (OutboxMessageEntity msg : batch) {
            if (sender.send(msg.getEventType(), msg.getPayload())) {
                msg.delivered();
                delivered++;
            } else {
                msg.failed();
            }
        }
        if (!batch.isEmpty()) {
            log.debug("outbox drained: {} delivered / {} failed of {}", delivered,
                    batch.size() - delivered, batch.size());
        }
        return delivered;
    }
}
