package dev.brp.service;

/** Notification domain contract (impl: {@link dev.brp.service.impl.NotificationServiceImpl}). */
public interface NotificationService {

    /** Enqueue a localized notification; delivery is the dispatcher's problem. */
    void enqueue(String eventType, String messageKey, Object... args);
}
