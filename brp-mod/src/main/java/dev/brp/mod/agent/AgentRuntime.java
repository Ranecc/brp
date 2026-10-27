package dev.brp.mod.agent;

import dev.brp.mod.acl.Acl.GameThreads;
import dev.brp.mod.acl.Acl.PlayerDirectory;
import dev.brp.mod.acl.Acl.TeamProvider;
import dev.brp.mod.acl.Acl.WorldAccess;
import dev.brp.mod.client.BrpServerApi;
import dev.brp.mod.client.BrpServerApi.EventDto;
import dev.brp.mod.client.BrpServerApi.PlayerRef;
import dev.brp.common.event.BrpEventTypes;
import dev.brp.mod.desk.FormDesk;
import dev.brp.mod.desk.SignalDesk;
import dev.brp.mod.governor.ChunkGovernor;
import dev.brp.mod.governor.GovernorFeed;
import dev.brp.mod.queue.DiskBackedEventQueue;
import java.util.Set;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The upload pipeline (P1): everything flows ONE way —
 *   game (via ACL snapshots) -> WAL -> IO pool batches -> backend -> ack -> watermark.
 *
 * Iron rules honored here (docs/01 section 5): the game thread NEVER touches
 * network or disk-waits — sampling is a lambda handed to the IO scheduler;
 * the WAL is the only durability boundary before the backend ack.
 * Write-only skeleton: compiled and wired against NeoForge in the P1 mod build.
 */
public final class AgentRuntime {

    public record Config(String agentId, String tenantId, Duration heartbeatEvery,
                         Duration sendEvery, Duration playerSyncEvery,
                         Duration governorEvery, int batchSize) {
        public static Config defaults(String agentId, String tenantId) {
            return new Config(agentId, tenantId,
                    Duration.ofSeconds(10), Duration.ofSeconds(5),
                    Duration.ofMinutes(5), Duration.ofSeconds(30), 200);
        }
    }

    private final Config cfg;
    private final BrpServerApi api;
    private final DiskBackedEventQueue wal;
    private final PlayerDirectory players;
    private final WorldAccess world;
    private final GameThreads threads;
    private final Consumer<String> log;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "brp-agent-io");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean();

    public AgentRuntime(Config cfg, BrpServerApi api, DiskBackedEventQueue wal,
                        PlayerDirectory players, WorldAccess world,
                        GameThreads threads, Consumer<String> log) {
        this.cfg = cfg;
        this.api = api;
        this.wal = wal;
        this.players = players;
        this.world = world;
        this.threads = threads;
        this.log = log;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        schedule(this::sampleHeartbeat, cfg.heartbeatEvery());
        schedule(this::drainWal, cfg.sendEvery());
        schedule(this::syncPlayers, cfg.playerSyncEvery());
        schedule(this::pollGovernor, cfg.governorEvery());
        schedule(this::reportChunkMinutes, Duration.ofSeconds(60));
        schedule(this::pollDesks, cfg.governorEvery());   // P2-10: paper and orders
        log.accept("[BRP] agent runtime up: agent=" + cfg.agentId()
                + " wal=" + wal.backlogSize());
    }

    public void stop() {
        running.set(false);
        io.shutdownNow();
    }

    // --- pipeline stages (IO thread only) ---

    /** Stage 1: sample fleet telemetry through the ACL (no game-thread IO). */
    private void sampleHeartbeat() {
        try {
            // "type" travels INSIDE the payload: the envelope stays schema-less
            // and drainWal cannot lose it (audit #3).
            wal.enqueue(UUID.randomUUID().toString(), gsonlessJson(Map.of(
                            "type", "agent.heartbeat",
                            "mspt", world.mspt(),
                            "tps", world.tps(),
                            "backlog", wal.backlogSize())),
                    "TELEMETRY").eventId();
        } catch (IOException e) {
            log.accept("[BRP] heartbeat enqueue failed: " + e.getMessage());
        }
    }

    /** Stage 2: WAL -> batch POST -> ack -> watermark (at-least-once). */
    private void drainWal() {
        List<DiskBackedEventQueue.Envelope> batch = wal.drain(cfg.batchSize());
        if (batch.isEmpty()) {
            return;
        }
        List<EventDto> events = batch.stream()
                .map(e -> {
                    // audit #3: the WAL's REAL payload rides the wire now —
                    // no more empty Map.of() over a full warehouse of samples.
                    Map<String, Object> payload = payloadOf(e.json());
                    Object declared = payload.remove("type");
                    String type = declared instanceof String str && !str.isBlank()
                            ? str : "agent.heartbeat";
                    return new EventDto(e.seq() + ":" + e.eventId(), type, 1,
                            cfg.agentId(), cfg.tenantId(), e.reliability(), payload);
                })
                .toList();
        api.postTelemetry(events).thenAccept(ack -> {
            try {
                // watermark only what the backend confirmed (dupes count as confirmed)
                wal.ackThrough(batch.get(batch.size() - 1).seq());
            } catch (IOException e) {
                log.accept("[BRP] watermark advance failed: " + e.getMessage());
            }
        }).exceptionally(t -> {
            log.accept("[BRP] batch send failed; WAL retains " + wal.backlogSize());
            return null;
        });
    }

    /** Stage 3: uuid/name roster sync, idempotent per batchId (docs/07). */
    private void syncPlayers() {
        List<PlayerRef> roster = players.known().stream()
                .map(p -> new PlayerRef(p.uuid(), p.name()))
                .toList();
        if (roster.isEmpty()) {
            return;
        }
        api.postPlayers("ps-" + UUID.randomUUID(), roster)
                .exceptionally(t -> {
                    log.accept("[BRP] player sync failed: " + t.getMessage());
                    return null;
                });
    }

    /**
     * Stage 4 (P2-12): poll the governor read model, publish verdicts into
     * GovernorFeed (every terminal gate reads it) and refresh the registered
     * chunk set the meter reports against. Backend down = cache stays as-is;
     * a stale verdict is still a verdict, an exception is not.
     */
    private void pollGovernor() {
        api.getGovernor(cfg.tenantId()).thenAccept(verdicts -> {
            for (var v : verdicts) {
                GovernorFeed.publish(v.dimension(), v.chunkKey(),
                        ChunkGovernor.Verdict.valueOf(v.verdict()));
            }
        }).exceptionally(t -> {
            log.accept("[BRP] governor poll failed: " + t.getMessage());
            return null;
        });
        api.getChunks(cfg.tenantId()).thenAccept(slots -> {
            Set<String> registered = new java.util.HashSet<>();
            for (var slot : slots) {
                registered.add(slot.dimension() + "|" + slot.chunkKey());
            }
            this.registeredChunks = registered;
        }).exceptionally(t -> {
            log.accept("[BRP] chunk slot poll failed: " + t.getMessage());
            return null;
        });
    }

    /**
     * Stage 5 (P2-12): the meter — one chunk.load.minutes event per managed
     * AND loaded chunk per minute (docs/09 section 3). Loaded-but-unregistered
     * chunks are the rogue-loader detective's case file (P2-14+), not the
     * meter's.
     */
    private void reportChunkMinutes() {
        Set<String> loaded = world.forcedChunks();
        for (Map<String, Object> payload : chunkMinutePayloads(loaded, registeredChunks)) {
            try {
                wal.enqueue(UUID.randomUUID().toString(),
                        gsonlessJson(payload), "TELEMETRY").eventId();
            } catch (IOException e) {
                log.accept("[BRP] chunk minute enqueue failed: " + e.getMessage());
            }
        }
    }

    /** Pure intersection: loaded ∩ registered -> typed payloads, "dim|x,z" split. */
    static List<Map<String, Object>> chunkMinutePayloads(Set<String> loaded, Set<String> registered) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String key : registered) {
            if (!loaded.contains(key)) {
                continue;
            }
            int split = key.indexOf('|');
            out.add(Map.of(
                    "type", BrpEventTypes.CHUNK_LOAD_MINUTES,
                    "dimension", key.substring(0, split),
                    "chunkKey", key.substring(split + 1),
                    "minutes", 1L));
        }
        return out;
    }

    /**
     * Stage 6 (P2-10): feed the Form Terminal's paper tray and the Dispatch
     * Terminal's order count from the backend. Empty results are honest —
     * no pending forms IS the happy path of bureaucracy.
     */
    private void pollDesks() {
        api.getForms(cfg.tenantId()).thenAccept(rows ->
                FormDesk.publish(rows.stream()
                        .map(r -> new FormDesk.PendingForm(
                                "approval.withdrawal",
                                (r.memo() == null || r.memo().isBlank()
                                        ? "Withdrawal request" : r.memo())
                                        + " (" + (r.amountCents() / 100) + " BRC)",
                                r.requesterUuid()))
                        .toList()))
                .exceptionally(t -> {
                    log.accept("[BRP] form desk poll failed: " + t.getMessage());
                    return null;
                });
        api.pollSignals(cfg.tenantId()).thenAccept(poll ->
                        SignalDesk.publish(poll.pending() == null ? 0 : poll.pending().size()))
                .exceptionally(t -> {
                    log.accept("[BRP] signal desk poll failed: " + t.getMessage());
                    return null;
                });
    }

    private volatile Set<String> registeredChunks = Set.of();

    private void schedule(Runnable task, Duration every) {
        long millis = every.toMillis();
        io.scheduleWithFixedDelay(() -> {
            if (running.get()) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.accept("[BRP] pipeline stage error: " + e);
                }
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }

    static String gsonlessJson(Map<String, Object> m) {   // package-private: units verify the wire format
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            Object v = e.getValue();
            sb.append('"').append(e.getKey()).append("\":");
            // numbers/booleans stay bare — a quoted "12.5" is a string, and
            // the backend's num() rightly refuses to read strings as numbers
            sb.append(v instanceof Number || v instanceof Boolean ? v : '"' + String.valueOf(v) + '"');
        }
        return sb.append('}').toString();
    }

    /** Envelope payload -> map; malformed lines yield what parsed, never an exception. */
    static Map<String, Object> payloadOf(String json) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (var entry : obj.entrySet()) {
                var v = entry.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                    out.put(entry.getKey(), v.getAsDouble());
                } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
                    out.put(entry.getKey(), v.getAsBoolean());
                } else {
                    out.put(entry.getKey(), v.getAsString());
                }
            }
        } catch (RuntimeException malformed) {
            // a garbage line ships as {} — the pipeline never dies of one bad row
        }
        return out;
    }
}
