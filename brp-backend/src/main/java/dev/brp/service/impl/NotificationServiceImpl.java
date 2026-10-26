package dev.brp.service.impl;

import dev.brp.common.annotation.Atomic;
import dev.brp.entity.OutboxMessageEntity;
import dev.brp.repository.OutboxRepository;
import dev.brp.service.NotificationService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Enqueues outbound notifications onto the transactional outbox, resolving
 * i18n copy at enqueue time with the server's default locale (ops channel
 * language). The outbox row commits with the caller's transaction.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private final OutboxRepository outbox;
    private final MessageSource messages;

    public NotificationServiceImpl(OutboxRepository outbox, MessageSource messages) {
        this.outbox = outbox;
        this.messages = messages;
    }

    @Override
    @Atomic
    public void enqueue(String eventType, String messageKey, Object... args) {
        String copy = messages.getMessage(messageKey, args, messageKey,
                LocaleContextHolder.getLocale());
        outbox.save(new OutboxMessageEntity(UUID.randomUUID().toString(), eventType, copy));
    }
}
