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

    /** Governor read model row (docs/09 section 4): verdict per managed chunk. */
    record GovernorVerdict(String dimension, String chunkKey, String verdict, String reason) {}

    /** Managed chunk slot row: what the meter reports minutes against. */
    record ChunkSlot(String dimension, String chunkKey, String status, long creditMinutes) {}

    CompletableFuture<TelemetryAck> postTelemetry(List<EventDto> events);

    /** P2-12: the poll that feeds GovernorFeed — every terminal gate reads it. */
    CompletableFuture<List<GovernorVerdict>> getGovernor(String tenantId);

    /** P2-12: registered slots; the meter reports loaded ∩ registered only. */
    CompletableFuture<List<ChunkSlot>> getChunks(String tenantId);

    /** P2-10: the tenant's PENDING forms — the Form Terminal's paper tray. */
    record FormRow(String id, String memo, long amountCents, String requesterUuid) {}

    CompletableFuture<List<FormRow>> getForms(String tenantId);

    /** P2-15: this tenant's fair-share factor in (0,1]; 1.0 = full speed. */
    record ApsAllocation(double factor) {}

    CompletableFuture<ApsAllocation> getApsAllocation(String tenantId);

    // --- command plane (idempotent writes) ---

    record JournalLine(String accountNo, String direction, long amountCents, String memo) {}

    record PostJournal(String idempotencyKey, String currency, List<JournalLine> lines) {}

    CompletableFuture<Map<String, Object>> postJournal(String tenantId, PostJournal request);

    // --- inventory plane (WMS read model upsert) ---

    CompletableFuture<Map<String, Object>> putInventorySnapshots(
            String tenantId, String batchMode, List<Map<String, Object>> lines);

    // --- player identity sync (agent pulls uuid/name from the MC server) ---

    CompletableFuture<Map<String, Object>> postPlayers(String batchId, List<PlayerRef> players);

    record PlayerRef(String uuid, String name) {}

    // --- push plane (v0 poll + ack) ---

    record SignalCommand(long id, String channel, int level) {}

    record SignalPoll(List<SignalCommand> pending, long cursor) {}

    CompletableFuture<SignalPoll> pollSignals(String tenantId);

    CompletableFuture<Void> ackSignals(String tenantId, List<Long> ids);

    // --- bourse plane (P5, docs/14 §8) ---

    /** Catalog push: namespaced item ids, 500 per call, backend prettifies names. */
    CompletableFuture<Map<String, Object>> postCatalog(List<Map<String, String>> items);

    record WarehouseDeposit(String itemId, long qty, String idemKey) {}

    CompletableFuture<Map<String, Object>> postDeposit(String tenantId, WarehouseDeposit deposit);

    record ClaimRow(long id, String itemId, long qty, String status) {}

    CompletableFuture<List<ClaimRow>> getPendingClaims(String tenantId);

    CompletableFuture<Void> confirmClaim(String tenantId, long claimId);

    CompletableFuture<Void> releaseClaim(String tenantId, long claimId);
}
