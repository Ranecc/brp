package dev.brp.service.impl;

import dev.brp.service.WebhookSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Discord webhook delivery. Without a configured URL it logs and reports
 * success — dev mode must not brick the outbox pipeline. With a URL, a 204
 * is success; anything else returns false so the dispatcher retries.
 */
@Component
public class DiscordWebhookSender implements WebhookSender {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookSender.class);

    private final String webhookUrl;
    private final RestClient http;

    public DiscordWebhookSender(@Value("${brp.discord.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.http = RestClient.create();
    }

    @Override
    public boolean send(String eventType, String payload) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[outbox/dev] {} -> {}", eventType, payload);
            return true;
        }
        try {
            http.post().uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("content", "[" + eventType + "] " + payload))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("webhook delivery failed for {}: {}", eventType, e.getMessage());
            return false;
        }
    }
}
