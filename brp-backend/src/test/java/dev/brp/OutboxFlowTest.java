package dev.brp;

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
@SpringBootTest(properties = "brp.outbox.poll-ms=3600000") // keep the scheduler out of the test's way
class OutboxFlowTest {

    @Autowired NotificationService notifications;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxRepository outbox;

    @MockitoBean WebhookSender sender;

    @Test
    void notificationsDrainToTheWebhookExactlyOncePerEvent() {
        List<String> delivered = new ArrayList<>();
        when(sender.send(org.mockito.ArgumentMatchers.eq("approval.nudged"), anyString()))
                .thenAnswer(inv -> {
                    delivered.add(inv.getArgument(1));
                    return true;
                });

        notifications.enqueue("approval.nudged", "approval.nudge.ok");
        int drained = dispatcher.drainOnce();

        assertThat(drained).isGreaterThanOrEqualTo(1);
        assertThat(delivered).hasSize(1);
        assertThat(outbox.findTop20ByStatusOrderByIdAsc("DELIVERED"))
                .anyMatch(m -> m.getEventType().equals("approval.nudged"));
    }

    @Test
    void failedDeliveryMarksFailedForTheFutureSweeper() {
        when(sender.send(anyString(), anyString())).thenReturn(false);
        notifications.enqueue("approval.nudged", "approval.nudge.ok");
        dispatcher.drainOnce();
        assertThat(outbox.findTop20ByStatusOrderByIdAsc("FAILED"))
                .anyMatch(m -> m.getAttempts() == 1);
    }
}
