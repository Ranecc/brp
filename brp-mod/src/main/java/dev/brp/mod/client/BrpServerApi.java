package dev.brp.mod.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Declarative backend API surface, in the spirit of Feign: one method per
 * endpoint, DTOs in/out, transport is an implementation detail.
 *
 * Why not literal Feign: shading feign-core into a NeoForge jar is legal but
 * buys us classloader risk for zero feature gain over JDK HttpClient + Gson
 * (both already on the Minecraft classpath). The declarative shape below
 * keeps the feign ergonomics — swap HttpBrpClient for a real Feign
 * implementation behind this interface if we ever need Spring Cloud tooling.
 *
 * Contract source of truth: brp-contract/openapi.yaml + events.md.
 */
public interface BrpServerApi {

    // --- telemetry plane (batched, at-least-once, eventId dedup) ---

    record EventDto(String eventId, String type, int schemaVersion, String agentId,
                    String tenantId, String reliability, Object payload) {
        public static EventDto telemetry(String agentId, String tenantId, String type, Object payload) {
            return new EventDto(UUID.randomUUID().toString(), type, 1, agentId, tenantId,
                    "TELEMETRY", payload);
        }

        public static EventDto critical(String agentId, String tenantId, String type, Object payload) {
            return new EventDto(UUID.randomUUID().toString(), type, 1, agentId, tenantId,
                    "CRITICAL", payload);
        }
    }

    record TelemetryAck(int accepted, int duplicates) {}

    CompletableFuture<TelemetryAck> postTelemetry(List<EventDto> events);

    // --- command plane (idempotent writes) ---

    record JournalLine(String accountNo, String direction, long amountCents, String memo) {}

    record PostJournal(String idempotencyKey, String currency, List<JournalLine> lines) {}

    CompletableFuture<Map<String, Object>> postJournal(String tenantId, PostJournal request);

    // --- inventory plane (WMS read model upsert) ---

    CompletableFuture<Map<String, Object>> putInventorySnapshots(
            String tenantId, String batchMode, List<Map<String, Object>> lines);

    // --- push plane (v0 poll + ack) ---

    record SignalCommand(long id, String channel, int level) {}

    record SignalPoll(List<SignalCommand> pending, long cursor) {}

    CompletableFuture<SignalPoll> pollSignals(String tenantId);

    CompletableFuture<Void> ackSignals(String tenantId, List<Long> ids);
}
