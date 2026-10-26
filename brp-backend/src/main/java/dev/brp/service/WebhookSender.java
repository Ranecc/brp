package dev.brp.service;

/** Outbound delivery port: Discord webhook today, more vendors at the next reorg. */
public interface WebhookSender {

    /**
     * @return true when delivered; false lets the outbox retry later.
     * Implementations must be idempotent — the outbox is at-least-once.
     */
    boolean send(String eventType, String payload);
}
