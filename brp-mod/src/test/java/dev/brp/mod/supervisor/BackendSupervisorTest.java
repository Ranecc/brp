package dev.brp.mod.supervisor;

import dev.brp.mod.supervisor.BackendSupervisor.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supervisor lifecycle (audit #4): the watchdog is a renewable resource.
 * Before the fix, shutdown() killed a final executor and the comment lied
 * that start() would rebuild it — stop->start died on
 * RejectedExecutionException with a live child and a dead watchdog.
 */
class BackendSupervisorTest {

    @TempDir
    Path dir;

    private Config config() {
        // sibling mode (no embedded resource) + a jar path that does not exist
        return new Config(dir.resolve("brp").resolve("brp-backend.jar"),
                null, "java", 8080, "1G", List.of(), 5, line -> {});
    }

    @Test
    void stopStartCyclesGetAFreshWatchdogEveryTime() throws IOException {
        BackendSupervisor supervisor = new BackendSupervisor(config());
        assertFalse(supervisor.watchdogAlive());

        // start() recreates the executor BEFORE the jar check, so even a
        // missing jar leaves a usable watchdog — no dead executor survives
        assertThrows(IOException.class, supervisor::start);
        assertTrue(supervisor.watchdogAlive());

        supervisor.shutdown();
        assertFalse(supervisor.watchdogAlive());

        // the actual regression: start again after shutdown must not throw
        // RejectedExecutionException from a dead executor
        assertThrows(IOException.class, supervisor::start);   // jar still missing
        assertTrue(supervisor.watchdogAlive());
        supervisor.shutdown();
        assertFalse(supervisor.watchdogAlive());
    }
}
