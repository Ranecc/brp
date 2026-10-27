package dev.brp.mod.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JDK-HttpClient implementation of {@link BrpServerApi}. Zero shaded deps:
 * java.net.http + Gson are both on the Minecraft classpath already.
 *
 * Retry discipline (docs/06-transactions.md): POSTs carry idempotency keys /
 * eventIds, so blind retry with exponential backoff is always safe. The
 * agent-side caller owns the disk WAL; this class stays stateless.
 *
 * v0 auth: static bearer token via BRP_TOKEN env / config. P1: HMAC-SHA256
 * signature over (instanceId, timestamp, nonce, bodySha256) per docs/01 §6.
 */
public final class HttpBrpClient implements BrpServerApi {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Gson gson = new Gson();
    private final String baseUrl;
    private final String token;

    public HttpBrpClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.token = token;
    }

    @Override
    public CompletableFuture<TelemetryAck> postTelemetry(List<EventDto> events) {
        return send("POST", "/api/v1/telemetry/batches", Map.of("events", events))
                .thenApply(body -> gson.fromJson(body, TelemetryAck.class));
    }

    @Override
    public CompletableFuture<Map<String, Object>> postJournal(String tenantId, PostJournal request) {
        return send("POST", "/api/v1/tenants/" + tenantId + "/journal", request)
                .thenApply(body -> {
                    Type t = new TypeToken<Map<String, Object>>() {}.getType();
                    return gson.<Map<String, Object>>fromJson(body, t);
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> putInventorySnapshots(
            String tenantId, String batchMode, List<Map<String, Object>> lines) {
        return send("POST", "/api/v1/tenants/" + tenantId + "/inventory/snapshots",
                        Map.of("batchMode", batchMode, "lines", lines))
                .thenApply(body -> {
                    Type t = new TypeToken<Map<String, Object>>() {}.getType();
                    return gson.<Map<String, Object>>fromJson(body, t);
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> postPlayers(
            String batchId, List<BrpServerApi.PlayerRef> players) {
        List<Map<String, String>> body = players.stream()
                .map(p -> Map.of("uuid", p.uuid(), "name", p.name()))
                .toList();
        return send("POST", "/api/v1/players/batch",
                        Map.of("batchId", batchId, "players", body))
                .thenApply(b -> {
                    Type t = new TypeToken<Map<String, Object>>() {}.getType();
                    return gson.<Map<String, Object>>fromJson(b, t);
                });
    }

    @Override
    public CompletableFuture<List<GovernorVerdict>> getGovernor(String tenantId) {
        return send("GET", "/api/v1/chunks/" + tenantId + "/governor", null)
                .thenApply(body -> {
                    Type t = new TypeToken<List<GovernorVerdict>>() {}.getType();
                    return gson.<List<GovernorVerdict>>fromJson(body, t);
                });
    }

    @Override
    public CompletableFuture<List<ChunkSlot>> getChunks(String tenantId) {
        return send("GET", "/api/v1/chunks/" + tenantId, null)
                .thenApply(body -> {
                    Type t = new TypeToken<List<ChunkSlot>>() {}.getType();
                    return gson.<List<ChunkSlot>>fromJson(body, t);
                });
    }

    @Override
    public CompletableFuture<List<FormRow>> getForms(String tenantId) {
        return send("GET", "/api/v1/approvals/requests?teamId=" + tenantId
                        + "&status=PENDING", null)
                .thenApply(body -> {
                    Type t = new TypeToken<List<FormRow>>() {}.getType();
                    return gson.<List<FormRow>>fromJson(body, t);
                });
    }

    @Override
    public CompletableFuture<ApsAllocation> getApsAllocation(String tenantId) {
        return send("GET", "/api/v1/aps/allocations/" + tenantId, null)
                .thenApply(body -> gson.fromJson(body, ApsAllocation.class));
    }

    @Override
    public CompletableFuture<SignalPoll> pollSignals(String tenantId) {
        return send("GET", "/api/v1/tenants/" + tenantId + "/signals/poll", null)
                .thenApply(body -> gson.fromJson(body, SignalPoll.class));
    }

    @Override
    public CompletableFuture<Void> ackSignals(String tenantId, List<Long> ids) {
        return send("POST", "/api/v1/tenants/" + tenantId + "/signals/ack", Map.of("ids", ids))
                .thenApply(b -> null);
    }

    // --- internals ---

    private CompletableFuture<String> send(String method, String path, Object bodyJson) {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token);
        if (bodyJson == null) {
            req = req.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            req = req.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(gson.toJson(bodyJson)));
        }
        return http.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() / 100 != 2) {
                        throw new BrpHttpException(r.statusCode(), r.body());
                    }
                    return r.body();
                });
    }

    public static final class BrpHttpException extends RuntimeException {
        public final int status;
        public BrpHttpException(int status, String body) {
            super("BRP backend " + status + ": " + body);
            this.status = status;
        }
    }
}
