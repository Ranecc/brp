package dev.brp;

import dev.brp.entity.OutboxMessageEntity;
import dev.brp.repository.OutboxRepository;
import dev.brp.service.NotificationService;
import dev.brp.service.WebhookSender;
import dev.brp.service.impl.OutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.when;

/** Transactional outbox: enqueue -> dispatcher -> webhook, at-least-once. */
@SpringBootTest(properties = {"brp.outbox.poll-ms=3600000", "brp.outbox.sweep-ms=3600000"}) // schedulers stay out of the test's way
class OutboxFlowTest {

    @Autowired NotificationService notifications;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxRepository outbox;

    @MockitoBean WebhookSender sender;

    /** Shared testdb discipline: our row is the newest of its kind — scope by id. */
    private long ourNudgedRow() {
        return outbox.findAll().stream()
                .filter(m -> "approval.nudged".equals(m.getEventType()))
                .mapToLong(OutboxMessageEntity::getId).max().orElseThrow();
    }

    /** Other contexts park rows ahead of ours; drain until OUR row settles. */
    private void drainUntilSettled(long rowId) {
        for (int i = 0; i < 60; i++) {
            dispatcher.drainOnce();
            var state = outbox.findById(rowId).map(OutboxMessageEntity::getStatus).orElse("GONE");
            if (!"PENDING".equals(state) && !"SENDING".equals(state)) {
                return;
            }
        }
    }

    @Test
    void notificationsDrainToTheWebhookExactlyOncePerEvent() {
        List<String> delivered = new ArrayList<>();
        when(sender.send(org.mockito.ArgumentMatchers.eq("approval.nudged"), anyString()))
                .thenAnswer(inv -> {
                    delivered.add(inv.getArgument(1));
                    return true;
                });

        notifications.enqueue("approval.nudged", "approval.nudge.ok");
        long ours = ourNudgedRow();
        drainUntilSettled(ours);

        var row = outbox.findById(ours).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("DELIVERED");
        assertThat(row.getAttempts()).isEqualTo(1);   // exactly once per event
        assertThat(delivered).isNotEmpty();
    }

    @Test
    void failedDeliveryMarksFailedForTheFutureSweeper() {
        when(sender.send(anyString(), anyString())).thenReturn(false);
        notifications.enqueue("approval.nudged", "approval.nudge.ok");
        long ours = ourNudgedRow();
        drainUntilSettled(ours);
        var row = outbox.findById(ours).orElseThrow();
        assertThat(row.getStatus()).isEqualTo("FAILED");
        assertThat(row.getAttempts()).isEqualTo(1);
    }

    @Test
    void failedRowsRequeueAfterBackoffAndDeliverOnTheSecondLife() throws Exception {
        // first life: sender down -> FAILED with attempts=1
        when(sender.send(anyString(), anyString())).thenReturn(false);
        notifications.enqueue("approval.nudged", "approval.nudge.ok");
        dispatcher.drainOnce();
        var failedRows = outbox.findTop20ByStatusOrderByIdAsc("FAILED");
        assertThat(failedRows).isNotEmpty();

        long rowId = ourNudgedRow();   // ours, not some other context's stale corpse

        // second life: sender healthy; rows newer than the backoff stay FAILED
        when(sender.send(anyString(), anyString())).thenReturn(true);
        assertThat(dispatcher.sweepFailed()).isEqualTo(0);   // backoff not elapsed
        dispatcher.drainOnce();
        assertThat(outbox.findById(rowId)).map(OutboxMessageEntity::getStatus).contains("FAILED");

        // after the backoff the sweeper requeues and the drain delivers
        var stillFailed = outbox.findById(rowId).orElseThrow();
        java.lang.reflect.Field f = OutboxMessageEntity.class.getDeclaredField("updatedAt");
        f.setAccessible(true);
        f.set(stillFailed, java.time.Instant.now().minusSeconds(3600));
        outbox.save(stillFailed);

        assertThat(dispatcher.sweepFailed()).isGreaterThanOrEqualTo(1);
        dispatcher.drainOnce();
        assertThat(outbox.findById(rowId)).map(OutboxMessageEntity::getStatus).contains("DELIVERED");
    }
}
