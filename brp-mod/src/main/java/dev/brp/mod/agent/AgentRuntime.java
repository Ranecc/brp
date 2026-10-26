package dev.brp.mod.agent;

import dev.brp.mod.acl.Acl.GameThreads;
import dev.brp.mod.acl.Acl.PlayerDirectory;
import dev.brp.mod.acl.Acl.TeamProvider;
import dev.brp.mod.acl.Acl.WorldAccess;
import dev.brp.mod.client.BrpServerApi;
import dev.brp.mod.client.BrpServerApi.EventDto;
import dev.brp.mod.client.BrpServerApi.PlayerRef;
import dev.brp.mod.queue.DiskBackedEventQueue;

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
                         Duration sendEvery, Duration playerSyncEvery, int batchSize) {
        public static Config defaults(String agentId, String tenantId) {
            return new Config(agentId, tenantId,
                    Duration.ofSeconds(10), Duration.ofSeconds(5),
                    Duration.ofMinutes(5), 200);
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
            wal.enqueue(UUID.randomUUID().toString(), gsonlessJson(Map.of(
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
                .map(e -> new EventDto(e.seq() + ":" + e.eventId(), "agent.heartbeat", 1,
                        cfg.agentId(), cfg.tenantId(), e.reliability(), Map.of()))
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

    private static String gsonlessJson(Map<Object, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        return sb.append('}').toString();
    }
}
