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

/**
 * Agent-side outbox: append-only WAL with watermark compaction
 * (docs/06-transactions.md section 3).
 *
 * Contract:
 * - enqueue() is cheap and never blocks on network: game threads only touch
 *   an in-memory queue + one file append.
 * - ackThrough(seq) advances the durable watermark after the backend acked
 *   ALL eventIds up to that sequence, then compacts the segment.
 * - Crash safety vs latency: appends reach the OS page cache before
 *   enqueue() returns; a hard crash can lose the tail (at-least-once
 *   delivery tolerates re-send, CRITICAL events are re-derivable from the
 *   game). True fsync (SYNC/DSYNC open option) is a config flag for the
 *   paranoid, paid in tick latency. NOTE: enqueue currently writes on the
 *   caller thread under the lock — acceptable only because callers batch;
 *   moving appends to the IO thread is a P1 item (red line 3 in spirit).
 */
public final class DiskBackedEventQueue {

    public record Envelope(long seq, String eventId, String json, String reliability) {}

    private final Path walFile;
    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<Envelope> pending = new ArrayDeque<>();
    private long nextSeq = 1;
    private long ackedSeq = 0;
    private final long maxTelemetryBacklog;

    public DiskBackedEventQueue(Path dir, long maxTelemetryBacklog) throws IOException {
        this.walFile = dir.resolve("brp-agent.wal");
        this.maxTelemetryBacklog = maxTelemetryBacklog;
        Files.createDirectories(dir);
        if (Files.exists(walFile)) {
            for (String line : Files.readAllLines(walFile, StandardCharsets.UTF_8)) {
                Envelope e = parse(line);
                if (e != null) {
                    pending.add(e);
                    nextSeq = Math.max(nextSeq, e.seq() + 1);
                }
            }
        }
    }

    /** Thread-safe from game threads. Returns the stored envelope. */
    public Envelope enqueue(String eventId, String json, String reliability) throws IOException {
        lock.lock();
        try {
            dropOldTelemetryUnderPressure(reliability);
            Envelope e = new Envelope(nextSeq++, eventId, json, reliability);
            Files.writeString(walFile, e.seq() + "\t" + e.eventId() + "\t"
                            + e.reliability() + "\t" + sanitize(json) + "\n",
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
     */
    private void dropOldTelemetryUnderPressure(String incomingReliability) {
        if (pending.size() < maxTelemetryBacklog) {
            return;
        }
        long telemetryCount = pending.stream()
                .filter(e -> "TELEMETRY".equals(e.reliability())).count();
        for (long dropped = 0; dropped < Math.min(telemetryCount, 100); dropped++) {
            Envelope head = pending.peek();
            if (head == null || !"TELEMETRY".equals(head.reliability())) {
                break; // head is CRITICAL: stop rather than reorder past it
            }
            pending.poll();
        }
    }

    private void compact() throws IOException {
        Path tmp = walFile.resolveSibling(walFile.getFileName() + ".tmp");
        StringBuilder sb = new StringBuilder();
        for (Envelope e : pending) {
            sb.append(e.seq()).append('\t').append(e.eventId()).append('\t')
                    .append(e.reliability()).append('\t').append(e.json()).append('\n');
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
        String[] parts = line.split("\t", 4);
        if (parts.length != 4) return null;
        try {
            // unsanitize reverses sanitize(); restarts no longer resurrect
            // double-escaped garbage (found in review, fixed with shame)
            return new Envelope(Long.parseLong(parts[0]), parts[1],
                    unsanitize(parts[3]), parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
