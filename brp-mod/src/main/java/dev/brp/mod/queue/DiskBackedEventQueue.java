package dev.brp.mod.queue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

/**
 * Agent-side outbox: append-only WAL with watermark compaction
 * (docs/06-transactions.md section 3).
 *
 * Contract:
 * - enqueue() is cheap and never blocks on network: IO threads only touch
 *   an in-memory queue + one file append. Game threads are REFUSED at the
 *   door (the thread guard is the mechanism behind red line 3 — the old
 *   posture was "nobody happens to call this from the server thread").
 * - ackThrough(seq) advances the durable watermark after the backend acked
 *   ALL eventIds up to that sequence, then compacts the segment.
 * - Crash safety vs latency: appends reach the OS page cache before
 *   enqueue() returns; a hard crash can lose the tail (at-least-once
 *   delivery tolerates re-send, CRITICAL events are re-derivable from the
 *   game). True fsync (SYNC/DSYNC open option) is a config flag for the
 *   paranoid, paid in tick latency. A TORN tail line (soft crash mid-write)
 *   is detected at recovery and reported through the log consumer — audit
 *   #21: silently eating half a line is how "never dropped" becomes a lie.
 */
public final class DiskBackedEventQueue {

    /** 5 columns since the ts addition; 4 columns = pre-ts WAL, still readable. */
    public record Envelope(long seq, String eventId, String json, String reliability, long tsEpochMs) {

        public Envelope(long seq, String eventId, String json, String reliability) {
            this(seq, eventId, json, reliability, 0L);
        }
    }

    private final Path walFile;
    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<Envelope> pending = new ArrayDeque<>();
    private final Predicate<Thread> gameThread;
    private final java.util.function.Consumer<String> log;
    private long nextSeq = 1;
    private long ackedSeq = 0;
    private final long maxTelemetryBacklog;

    public DiskBackedEventQueue(Path dir, long maxTelemetryBacklog) throws IOException {
        this(dir, maxTelemetryBacklog, t -> t.getName().startsWith("Server"), m -> {});
    }

    public DiskBackedEventQueue(Path dir, long maxTelemetryBacklog,
                                Predicate<Thread> gameThread,
                                java.util.function.Consumer<String> log) throws IOException {
        this.walFile = dir.resolve("brp-agent.wal");
        this.maxTelemetryBacklog = maxTelemetryBacklog;
        this.gameThread = gameThread;
        this.log = log;
        Files.createDirectories(dir);
        if (Files.exists(walFile)) {
            byte[] raw = Files.readAllBytes(walFile);
            // a torn write is a file whose last line never got its newline —
            // column shapes cannot tell (a json cut mid-field still "parses"),
            // the newline is the commit mark of a line-based log
            boolean tornTail = raw.length > 0 && raw[raw.length - 1] != '\n';
            List<String> lines = Files.readAllLines(walFile, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                boolean last = i == lines.size() - 1;
                Envelope e = parse(lines.get(i));
                if (e != null && !(tornTail && last)) {
                    pending.add(e);
                    nextSeq = Math.max(nextSeq, e.seq() + 1);
                } else if (!lines.get(i).isBlank()) {
                    // audit #21: a torn tail is the crash signature, a torn
                    // middle is corruption — neither may vanish without a word
                    log.accept("[BRP] WAL " + (tornTail && last
                            ? "torn tail (crash mid-write), " : "corrupt line ")
                            + "dropped: " + abbreviate(lines.get(i)));
                }
            }
        }
    }

    /** Thread-safe from IO threads. Game threads are refused (red line 3). */
    public Envelope enqueue(String eventId, String json, String reliability) throws IOException {
        if (gameThread.test(Thread.currentThread())) {
            throw new IllegalStateException(
                    "wal.enqueue called on the game thread (" + Thread.currentThread().getName()
                            + ") — red line 3: hand the event to the agent IO path instead");
        }
        lock.lock();
        try {
            boolean dropped = dropOldTelemetryUnderPressure();
            if (dropped) {
                compact();   // audit #21: the file must agree with memory now,
                             // or a restart resurrects the condemned
            }
            Envelope e = new Envelope(nextSeq++, eventId, json, reliability,
                    System.currentTimeMillis());
            Files.writeString(walFile, e.seq() + "\t" + e.eventId() + "\t"
                            + e.reliability() + "\t" + e.tsEpochMs() + "\t" + sanitize(json) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            pending.add(e);
            return e;
        } finally {
            lock.unlock();
        }
    }

    /** Snapshot of unacked envelopes for the sender loop (bounded). */
    public List<Envelope> drain(int limit) {
        lock.lock();
        try {
            List<Envelope> out = new ArrayList<>(Math.min(limit, pending.size()));
            for (Envelope e : pending) {
                if (out.size() >= limit) break;
                out.add(e);
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    /** Advance watermark after backend confirmed every eventId <= seq, then compact. */
    public void ackThrough(long seq) throws IOException {
        lock.lock();
        try {
            ackedSeq = Math.max(ackedSeq, seq);
            while (!pending.isEmpty() && pending.peek().seq() <= ackedSeq) {
                pending.poll();
            }
            compact();
        } finally {
            lock.unlock();
        }
    }

    public int backlogSize() {
        lock.lock();
        try {
            return pending.size();
        } finally {
            lock.unlock();
        }
    }

    // --- internals ---

    /**
     * Disk-watermark protection: when the backlog exceeds the cap, drop the
     * OLDEST TELEMETRY events. CRITICAL (monetary/command) events are never
     * dropped; if the backlog is all-critical we grow the file and alert.
     * Returns true when anything was dropped — the caller persists the
     * verdict, otherwise restart brings the condemned back from the dead.
     */
    private boolean dropOldTelemetryUnderPressure() {
        if (pending.size() < maxTelemetryBacklog) {
            return false;
        }
        boolean droppedAny = false;
        while (pending.size() >= maxTelemetryBacklog) {
            Envelope head = pending.peek();
            if (head == null || !"TELEMETRY".equals(head.reliability())) {
                break; // head is CRITICAL: stop rather than reorder past it
            }
            pending.poll();
            droppedAny = true;
        }
        return droppedAny;
    }

    private void compact() throws IOException {
        Path tmp = walFile.resolveSibling(walFile.getFileName() + ".tmp");
        StringBuilder sb = new StringBuilder();
        for (Envelope e : pending) {
            sb.append(e.seq()).append('\t').append(e.eventId()).append('\t')
                    .append(e.reliability()).append('\t').append(e.tsEpochMs()).append('\t')
                    .append(e.json()).append('\n');
        }
        Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, walFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static String sanitize(String json) {
        // WAL is line-based: one event == one line. Escaping is reversible.
        if (json == null) return "";
        return json.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    private static String unsanitize(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        StringBuilder sb = new StringBuilder(stored.length());
        for (int i = 0; i < stored.length(); i++) {
            char c = stored.charAt(i);
            if (c == '\\' && i + 1 < stored.length()) {
                char next = stored.charAt(++i);
                if (next == 'n') sb.append('\n');
                else sb.append(next);   // \\ -> \ , any other pair -> literal
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Envelope parse(String line) {
        String[] parts = line.split("\t", 5);
        try {
            if (parts.length == 5) {
                // current format: seq, eventId, reliability, ts, json
                return new Envelope(Long.parseLong(parts[0]), parts[1],
                        unsanitize(parts[4]), parts[2], Long.parseLong(parts[3]));
            }
            if (parts.length == 4) {
                // pre-ts format: same columns minus ts; old WALs stay readable
                return new Envelope(Long.parseLong(parts[0]), parts[1],
                        unsanitize(parts[3]), parts[2], 0L);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }
}
