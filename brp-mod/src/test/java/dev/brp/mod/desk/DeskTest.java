package dev.brp.mod.desk;

import dev.brp.mod.desk.FormDesk.PendingForm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desks (P2-10): agent-fed caches the terminals read. Empty is an
 * honest answer — no pending forms IS the happy path of bureaucracy; the
 * menu falls back to the sample tray and never pretends otherwise.
 */
class DeskTest {

    @Test
    void formDeskPublishesServesAndReportsLiveness() {
        FormDesk.publish(List.of(new PendingForm("approval.withdrawal", "Coffee budget (5 BRC)", "u-1")));
        assertTrue(FormDesk.live());
        assertEquals(1, FormDesk.forms().size());
        assertEquals("Coffee budget (5 BRC)", FormDesk.forms().get(0).title());

        FormDesk.publish(List.of());
        assertFalse(FormDesk.live());   // empty poll = wire live, tray calm
        assertEquals(List.of(), FormDesk.forms());
    }

    @Test
    void signalDeskCountsNeverGoNegative() {
        SignalDesk.publish(3);
        assertEquals(3, SignalDesk.pendingCount());
        SignalDesk.publish(-7);   // a malformed poll must not invent orders
        assertEquals(0, SignalDesk.pendingCount());
    }
}
