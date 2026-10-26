package dev.brp.mod.supervisor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Sidecar lifecycle manager: the Minecraft server (via the agent) launches
 * brp-backend as a CHILD PROCESS and keeps it healthy (docs/01 section 2.5).
 *
 * Why child-process instead of in-process: full JVM/classloader isolation at
 * zero cost — a Spring crash can never take the game down, and vice versa the
 * game's bundled libraries can't poison ours.
 *
 * Tradeoffs we accept (documented, not hidden):
 * - MC death kills the child: fine — the backend is stateless, state lives in
 *   PostgreSQL; the agent WAL replays anything missed.
 * - Ports/heap must be configured once (brp.agent.supervisor in the config).
 * - Upgrades still mean replacing the backend jar (deliberately decoupled
 *   from mod releases).
 *
 * Pure Java — no Minecraft imports; the mod calls start()/shutdown() from its
 * own lifecycle hooks, tests can drive it against any jar.
 */
public final class BackendSupervisor {

    public record Config(
            Path backendJar,            // extraction target: <server>/brp/brp-backend.jar
            String embeddedResource,    // null = sibling file mode; else resource path in the mod jar
            String javaExecutable,      // defaults to current JVM's java
            int port,
            String xmx,                 // e.g. "1G"
            List<String> extraArgs,
            int maxRestarts,            // restart budget within backoff window
            Consumer<String> log) {

        public static Config defaults(Path serverDir) {
            // Single-file distribution: the mod jar embeds the backend fat jar
            // at resources/backend/brp-backend.jar (see build.gradle.kts); the
            // supervisor extracts it on first run / version change (sha256).
            return new Config(
                    serverDir.resolve("brp").resolve("brp-backend.jar"),
                    "/backend/brp-backend.jar",
                    ProcessHandle.current().info().command().orElse("java"),
                    8080, "1G", List.of(), 5, line -> {});
        }
    }

    private final Config cfg;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicInteger restarts = new AtomicInteger();
    private final ScheduledExecutorService watchdog =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "brp-supervisor");
                t.setDaemon(true);
                return t;
            });
    private volatile Process child;

    public BackendSupervisor(Config cfg) {
        this.cfg = cfg;
    }

    /** Launch the backend and block (bounded) until its health endpoint answers. */
    public synchronized void start() throws IOException, InterruptedException {
        if (running.get()) {
            return;
        }
        if (cfg.embeddedResource() != null) {
            extractEmbedded(cfg.embeddedResource(), cfg.backendJar());
        }
        if (!Files.isRegularFile(cfg.backendJar())) {
            throw new IOException("backend jar not found: " + cfg.backendJar()
                    + " (single-file build embeds it; else drop brp-backend.jar there)");
        }
        spawn();
        watchdog.scheduleWithFixedDelay(this::watch, 5, 5, TimeUnit.SECONDS);
        if (!awaitHealthy(Duration.ofSeconds(90))) {
            throw new IllegalStateException("backend did not become healthy within 90s");
        }
    }

    /**
     * Single-file distribution step: extract the embedded backend jar unless
     * the on-disk copy already matches (sha256). Still a CHILD PROCESS after
     * extraction — the "single file" is the distribution artifact, not the
     * runtime topology (docs/01 section 2.5).
     */
    static void extractEmbedded(String resource, Path target) throws IOException {
        byte[] bytes;
        try (var in = BackendSupervisor.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("embedded backend not found in mod jar: " + resource);
            }
            bytes = in.readAllBytes();
        }
        String want = sha256(bytes);
        if (Files.isRegularFile(target)) {
            try {
                if (sha256(Files.readAllBytes(target)).equals(want)) {
                    return; // same version already extracted
                }
            } catch (IOException e) {
                cfgLog("re-extracting (unreadable target): " + e.getMessage());
            }
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        cfgLog("extracted embedded backend to " + target + " (" + bytes.length / 1024 / 1024 + " MB)");
    }

    private static String sha256(byte[] data) throws java.security.NoSuchAlgorithmException {
        byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void cfgLog(String msg) {
        try {
            cfg.log().accept("[BRP] " + msg);
        } catch (RuntimeException ignored) {
            // logging consumer is optional plumbing, never fatal
        }
    }

    /** Stop the child gracefully (SIGTERM then force after grace). */
    public synchronized void shutdown() {
        running.set(false);
        watchdog.shutdownNow();
        Process p = child;
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                if (!p.waitFor(10, TimeUnit.SECONDS)) {
                    p.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
    }

    public boolean healthy() {
        try {
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + cfg.port() + "/actuator/health"))
                            .timeout(Duration.ofSeconds(2)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // --- internals ---

    private void spawn() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.javaExecutable());
        cmd.add("-Xmx" + cfg.xmx());
        cmd.addAll(List.of("-jar", cfg.backendJar().toString()));
        cmd.addAll(cfg.extraArgs());
        cfg.log().accept("[BRP] launching backend: " + String.join(" ", cmd));
        child = new ProcessBuilder(cmd).inheritIO().start();
        running.set(true);
    }

    private void watch() {
        Process p = child;
        if (p == null || !running.get()) {
            return;
        }
        if (!p.isAlive() || !healthy()) {
            if (restarts.incrementAndGet() > cfg.maxRestarts()) {
                cfg.log().accept("[BRP] backend restart budget exhausted, giving up"
                        + " (game continues on WAL degradation — docs/01 section 5)");
                running.set(false);
                return;
            }
            long backoff = Math.min(60, 1L << Math.min(restarts.get(), 6));
            cfg.log().accept("[BRP] backend unhealthy (exit="
                    + (p.isAlive() ? "alive-but-sick" : p.exitValue()) + "), restart in " + backoff + "s");
            try {
                TimeUnit.SECONDS.sleep(backoff);
                if (running.get()) {
                    spawn();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                cfg.log().accept("[BRP] respawn failed: " + e.getMessage());
            }
        }
    }

    private boolean awaitHealthy(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (healthy()) {
                cfg.log().accept("[BRP] backend healthy on port " + cfg.port());
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(500);
        }
        return false;
    }
}
