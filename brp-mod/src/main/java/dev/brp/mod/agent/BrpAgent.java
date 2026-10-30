package dev.brp.mod.agent;

import dev.brp.mod.BrpMod;
import dev.brp.mod.client.HttpBrpClient;
import dev.brp.mod.platform.neoforge.NeoForgeAcl;
import dev.brp.mod.platform.neoforge.NeoForgeVault;
import dev.brp.mod.queue.DiskBackedEventQueue;
import dev.brp.mod.supervisor.BackendSupervisor;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The dedicated-server boot (P2-12): ACL -> WAL -> HTTP client -> supervisor
 * -> AgentRuntime, in that order, on the server lifecycle. Singleplayer/LAN
 * boots nothing — the backend is not applicable there (docs/01 section 2.5).
 *
 * Configuration, v0: system properties (brp.backend.url, brp.tenant,
 * brp.token, brp.agent-id). A real config screen lands when there is a
 * screen to put it on.
 */
public final class BrpAgent {

    private static final Logger LOG = LoggerFactory.getLogger("BRP");

    private static BackendSupervisor supervisor;
    private static AgentRuntime runtime;
    private static ScheduledExecutorService io;

    private BrpAgent() {}

    public static void boot(MinecraftServer server) {
        if (!Boolean.parseBoolean(prop("brp.agent.enabled", "true"))) {
            LOG.info("[BRP] agent disabled (brp.agent.enabled=false); mod inert");
            return;
        }
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("brp");
            Files.createDirectories(dir);

            io = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "brp-agent-io");
                t.setDaemon(true);
                return t;
            });
            NeoForgeAcl acl = new NeoForgeAcl(server, io);
            DiskBackedEventQueue wal = new DiskBackedEventQueue(dir, 100_000);
            HttpBrpClient api = new HttpBrpClient(
                    prop("brp.backend.url", "http://127.0.0.1:8080"),
                    token(dir));
            String agentId = prop("brp.agent-id",
                    InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8));

            supervisor = new BackendSupervisor(BackendSupervisor.Config.defaults(FMLPaths.GAMEDIR.get()));
            try {
                supervisor.start();
            } catch (IOException | IllegalStateException | InterruptedException sidecarUnavailable) {
                // dev runtime has no embedded backend jar; the game continues
                // on WAL degradation — the agent buffers until it is needed
                LOG.warn("[BRP] backend sidecar not started ({}): degraded mode, WAL buffers",
                        sidecarUnavailable.getMessage());
            }

            // P3-12: the vault dispense port, wired only when a vault
            // container is configured (brp.vault.container = dim|x,y,z).
            // P4: demand-response windows ride the same signal plane —
            // @dr/<jobId>, level 15 = OPEN, 0 = CLOSE (docs/13 §5).
            String vaultRef = prop("brp.vault.container", "");
            java.util.function.BiFunction<String, Integer, Boolean> executor =
                    vaultRef.isBlank() && !drEnabled() ? null
                            : (channel, level) -> {
                                if ("vault.dispense".equals(channel) && !vaultRef.isBlank()) {
                                    return new NeoForgeVault(server, vaultRef).dispense(level);
                                }
                                if (channel.startsWith("@dr/")) {
                                    String jobId = channel.substring("@dr/".length());
                                    if (level > 0) {
                                        dev.brp.mod.governor.DrWindows.open(jobId);
                                    } else {
                                        dev.brp.mod.governor.DrWindows.close(jobId);
                                    }
                                    return true;
                                }
                                return false;
                            };
            runtime = new AgentRuntime(AgentRuntime.Config.defaults(agentId, prop("brp.tenant", "server")),
                    api, wal, acl, acl, acl, msg -> LOG.info("{}", msg), executor,
                    new dev.brp.mod.platform.neoforge.NeoForgeActuator(server));
            runtime.start();

            // P5 (docs/14 §8): the item catalog push runs unconditionally —
            // reading mod items is not the same as selling them. The intake
            // desk (deposit slot + withdraw claims) wakes only when an
            // intake container is configured (brp.bourse.container = dim|x,y,z).
            final dev.brp.mod.agent.ItemCatalog itemCatalog = new dev.brp.mod.agent.ItemCatalog(
                    dev.brp.mod.platform.neoforge.NeoForgeItemCatalog::ids,
                    api, msg -> LOG.info("{}", msg));
            long catalogMin = Long.parseLong(prop("brp.catalog.push-min", "30"));
            io.scheduleWithFixedDelay(itemCatalog::push, 5, catalogMin * 60,
                    java.util.concurrent.TimeUnit.SECONDS);

            String intakeRef = prop("brp.bourse.container", "");
            if (!intakeRef.isBlank()) {
                try {
                    dev.brp.mod.store.SqliteLocalStore bourseStore =
                            new dev.brp.mod.store.SqliteLocalStore(dir.resolve("bourse.db"));
                    bourseStore.init();
                    dev.brp.mod.agent.BourseSync bourseSync = new dev.brp.mod.agent.BourseSync(
                            api, bourseStore, new dev.brp.mod.platform.neoforge.NeoForgeBourse(server, intakeRef),
                            prop("brp.tenant", "server"), msg -> LOG.info("{}", msg));
                    long syncSec = Long.parseLong(prop("brp.bourse.sync-sec", "30"));
                    io.scheduleWithFixedDelay(bourseSync::sync, syncSec, syncSec,
                            java.util.concurrent.TimeUnit.SECONDS);
                    LOG.info("[BRP] bourse intake desk up: {}", intakeRef);
                } catch (Exception deskFailed) {
                    LOG.warn("[BRP] bourse intake desk failed to start: {}", deskFailed.getMessage());
                }
            }

            // P2-14: the machine census — namespace counts over registered
            // chunks, ModList-guarded, one machine.stats event per mod
            final dev.brp.mod.adapters.NamespacePolicy policy = new dev.brp.mod.adapters.NamespacePolicy();
            io.scheduleWithFixedDelay(() -> {
                try {
                    var keys = runtime.registeredChunksView();
                    if (keys.isEmpty()) {
                        return;
                    }
                    for (var entry : dev.brp.mod.adapters.ChunkCensus
                            .byNamespace(server, keys, policy).entrySet()) {
                        if (!dev.brp.mod.adapters.ChunkCensus.modLoaded(entry.getKey())) {
                            continue;   // census counts ghosts otherwise
                        }
                        wal.enqueue(UUID.randomUUID().toString(),
                                AgentRuntime.gsonlessJson(java.util.Map.of(
                                        "type", dev.brp.common.event.BrpEventTypes.MACHINE_STATS,
                                        "machineId", "census:" + entry.getKey(),
                                        "kind", "census",
                                        "count", entry.getValue())),
                                "TELEMETRY").eventId();
                    }
                } catch (Exception ex) {
                    LOG.warn("[BRP] census failed: {}", ex.getMessage());
                }
            }, 60, 60, java.util.concurrent.TimeUnit.SECONDS);

            LOG.info("[BRP] agent booted: agent={} tenant={}", agentId, prop("brp.tenant", "server"));
        } catch (IOException | RuntimeException fatal) {
            LOG.error("[BRP] agent boot failed; mod inert for this session", fatal);
        }
    }

    public static void shutdown() {
        if (runtime != null) {
            runtime.stop();
        }
        if (supervisor != null) {
            supervisor.shutdown();
        }
        if (io != null) {
            io.shutdownNow();
        }
        LOG.info("[BRP] agent shut down; watermark persisted in the WAL");
    }

    private static String prop(String key, String fallback) {
        String v = System.getProperty(key);
        return v != null && !v.isBlank() ? v : fallback;
    }

    /**
     * Zero-arg boot (docs/16 §2): no brp.token configured means the mod
     * mints one on first boot and persists it at brp/agent.token — the
     * operator assigns identity after boot, not before. The "dev" default is
     * retired: a constant printed in source is not a token, it is a sticky
     * note on the vault door. (The backend's v0 posture stays no-auth-
     * internal-only; HMAC instance auth is P1, docs/01 §6.)
     */
    private static String token(Path dir) {
        String configured = System.getProperty("brp.token");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Path file = dir.resolve("agent.token");
        try {
            if (Files.isRegularFile(file)) {
                String stored = Files.readString(file).trim();
                if (!stored.isBlank()) {
                    return stored;
                }
            }
            String minted = UUID.randomUUID().toString();
            Files.writeString(file, minted);
            LOG.info("[BRP] no brp.token configured: minted one at brp/agent.token (zero-arg boot)");
            return minted;
        } catch (IOException e) {
            LOG.warn("[BRP] agent.token persistence failed ({}); a per-boot token will do", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    /** DR window signals are useful even with no vault container configured. */
    private static boolean drEnabled() {
        return Boolean.parseBoolean(prop("brp.dr.enabled", "true"));
    }
}
