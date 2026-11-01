package dev.brp.mod.queue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbox's crash contract (audit #21): a torn tail is reported, never
 * silently eaten; pressure drops persist so restart cannot resurrect the
 * condemned; CRITICAL events survive the waterline; the ts column round-trips
 * and pre-ts WALs still load. Plus the red line 3 guard: the game thread does
 * not touch the WAL, mechanically.
 */
class DiskBackedEventQueueTest {

    @TempDir
    Path dir;

    private DiskBackedEventQueue open(long cap, List<String> log) throws Exception {
        return new DiskBackedEventQueue(dir, cap,
                t -> t.getName().startsWith("Server thread"), log::add);
    }

    @Test
    void tornTailIsReportedNotSwallowed() throws Exception {
        Files.writeString(dir.resolve("brp-agent.wal"),
                "1\tev-1\tTELEMETRY\t1700000000000\t{\"a\":1}\n"
                + "2\tev-2\tCRITICAL\t1700000000001\t{\"a\":2}\n"
                + "3\tev-3\tCRITICAL\t1700000000002\t{\"bro");
        List<String> log = new ArrayList<>();
        DiskBackedEventQueue wal = open(100, log);
        assertEquals(2, wal.backlogSize(), "the readable prefix survives");
        assertTrue(log.stream().anyMatch(l -> l.contains("torn tail")),
                "a torn write must be announced, not eaten");
    }

    @Test
    void corruptMiddleLineIsReportedAndSkipped() throws Exception {
        Files.writeString(dir.resolve("brp-agent.wal"),
                "1\tev-1\tTELEMETRY\t1700000000000\t{\"a\":1}\n"
                + "not-a-wal-line\n"
                + "2\tev-2\tTELEMETRY\t1700000000001\t{\"a\":2}\n");
        List<String> log = new ArrayList<>();
        DiskBackedEventQueue wal = open(100, log);
        assertEquals(2, wal.backlogSize());
        assertTrue(log.stream().anyMatch(l -> l.contains("corrupt line")));
    }

    @Test
    void pressureDropsPersistAcrossRestart() throws Exception {
        DiskBackedEventQueue wal = open(2, new ArrayList<>());
        for (int i = 1; i <= 4; i++) {
            wal.enqueue("ev-" + i, "{\"n\":" + i + "}", "TELEMETRY");
        }
        assertEquals(2, wal.backlogSize());
        // restart over the same file: the dropped pair stays dropped
        DiskBackedEventQueue reopened = open(2, new ArrayList<>());
        assertEquals(2, reopened.backlogSize(), "condemned events must not resurrect");
        assertEquals(4, reopened.drain(10).get(1).seq(), "the newest survive");
    }

    @Test
    void criticalSurvivesTheWaterline() throws Exception {
        DiskBackedEventQueue wal = open(2, new ArrayList<>());
        for (int i = 1; i <= 4; i++) {
            wal.enqueue("ev-" + i, "{\"n\":" + i + "}", "CRITICAL");
        }
        assertEquals(4, wal.backlogSize(), "money events are never dropped for space");
    }

    @Test
    void tsColumnRoundTripsAndOldFormatStillLoads() throws Exception {
        DiskBackedEventQueue wal = open(100, new ArrayList<>());
        DiskBackedEventQueue.Envelope e = wal.enqueue("ev-1", "{\"a\":1}", "TELEMETRY");
        assertTrue(e.tsEpochMs() > 0);
        DiskBackedEventQueue.Envelope drained = wal.drain(10).get(0);
        assertEquals(e.tsEpochMs(), drained.tsEpochMs());

        // a pre-ts WAL (4 columns) still loads, ts unknown
        Path file = dir.resolve("brp-agent.wal");
        Files.writeString(file, "9\told-ev\tCRITICAL\t{\"legacy\":true}\n");
        DiskBackedEventQueue reopened = open(100, new ArrayList<>());
        DiskBackedEventQueue.Envelope legacy = reopened.drain(10).get(0);
        assertNotNull(legacy);
        assertEquals("old-ev", legacy.eventId());
        assertEquals(0L, legacy.tsEpochMs());
    }

    @Test
    void gameThreadIsRefusedAtTheDoor() throws Exception {
        DiskBackedEventQueue wal = open(100, new ArrayList<>());
        Thread fakeServerThread = Thread.currentThread();
        String realName = fakeServerThread.getName();
        fakeServerThread.setName("Server thread");
        try {
            assertThrows(IllegalStateException.class,
                    () -> wal.enqueue("ev-x", "{}", "TELEMETRY"));
        } finally {
            fakeServerThread.setName(realName);
        }
        assertNotNull(wal.enqueue("ev-y", "{}", "TELEMETRY"));
    }
}
