package dev.brp.mod.governor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P4 demand-response window registry (docs/13 section 5): OPEN/CLOSE ride
 * the signal plane, lines consult here. Unknown jobs are CLOSED — the grid
 * owes nobody a default-open window.
 */
class DrWindowsTest {

    @AfterEach
    void cleanup() {
        DrWindows.clear();
    }

    @Test
    void unknownJobsAreClosed() {
        assertFalse(DrWindows.isOpen("nobody"));
    }

    @Test
    void openAndCloseRoundTrip() {
        DrWindows.open("42");
        assertTrue(DrWindows.isOpen("42"));
        DrWindows.close("42");
        assertFalse(DrWindows.isOpen("42"));
    }

    @Test
    void closeIsIdempotentAndOpenOverwrites() {
        DrWindows.open("7");
        DrWindows.open("7");
        assertTrue(DrWindows.isOpen("7"));
        DrWindows.close("7");
        DrWindows.close("7");
        assertFalse(DrWindows.isOpen("7"));
    }

    @Test
    void channelIsTheDispatchNamespace() {
        assertEquals("@dr/42", DrWindows.channel("42"));
    }
}
