package dev.brp.mod.agent;

import dev.brp.mod.client.BrpServerApi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The intake desk state machine (docs/14 §8): goods leave the world before
 * the books move, staging survives a backend outage, claims ship before
 * they confirm, and a short shelf is reported rather than absorbed.
 */
class BourseSyncTest {

    private final BourseTestKit.TestApi api = new BourseTestKit.TestApi();
    private final BourseTestKit.FakePort port = new BourseTestKit.FakePort();
    private final BourseTestKit.FakeStore store = new BourseTestKit.FakeStore();
    private final List<String> log = new ArrayList<>();

    private BourseSync desk() {
        return new BourseSync(api, store, port, "tenant-a", log::add);
    }

    @Test
    void arrivalsStageExtractThenFileExactlyOnce() {
        port.contents.put("testmod:copper_ingot", 10L);

        // pass 1: backend down — the goods are OUT of the container and staged
        api.failDeposits = true;
        BourseSync.Summary first = desk().sync();
        assertEquals(1, first.staged());
        assertEquals(0, first.filed());
        assertFalse(port.contents.containsKey("testmod:copper_ingot"));
        assertTrue(api.deposits.isEmpty());
        assertEquals(1, store.tables.get(BourseSync.NS_STAGE).size());

        // pass 2: backend back — the staged row files under its fixed idem key
        api.failDeposits = false;
        BourseSync.Summary second = desk().sync();
        assertEquals(1, second.filed());
        assertEquals(1, api.deposits.size());
        assertEquals("testmod:copper_ingot", api.deposits.get(0).itemId());
        assertEquals(10, api.deposits.get(0).qty());
        assertTrue(api.deposits.get(0).idemKey().startsWith("bourse-dep:tenant-a:"));
        assertTrue(store.tables.get(BourseSync.NS_STAGE).isEmpty());

        // pass 3: nothing new, nothing re-filed
        BourseSync.Summary third = desk().sync();
        assertEquals(0, third.staged());
        assertEquals(0, third.filed());
        assertEquals(1, api.deposits.size());
    }

    @Test
    void claimsShipThenConfirm() {
        port.contents.put("testmod:iron_ingot", 10L);
        store.put(BourseSync.NS_BASELINE, "testmod:iron_ingot", "10");
        api.pendingClaims = List.of(new BrpServerApi.ClaimRow(7, "testmod:iron_ingot", 4, "PENDING"));

        BourseSync.Summary s = desk().sync();
        assertEquals(1, s.confirmed());
        assertEquals(List.of(7L), api.confirmed);
        assertEquals(6L, port.contents.get("testmod:iron_ingot"));
        assertEquals("6", store.get(BourseSync.NS_BASELINE, "testmod:iron_ingot").orElse(""));
    }

    @Test
    void aClaimWithoutGoodsReleasesInsteadOfLying() {
        store.put(BourseSync.NS_BASELINE, "testmod:gold_ingot", "10");
        port.contents.put("testmod:gold_ingot", 10L);
        port.failExtract = true;
        api.pendingClaims = List.of(new BrpServerApi.ClaimRow(9, "testmod:gold_ingot", 4, "PENDING"));

        BourseSync.Summary s = desk().sync();
        assertEquals(1, s.released());
        assertEquals(List.of(9L), api.released);
        assertTrue(api.confirmed.isEmpty());
        // the release puts the goods back; the baseline keeps expecting them
        assertEquals(10L, port.contents.get("testmod:gold_ingot"));
        assertEquals("10", store.get(BourseSync.NS_BASELINE, "testmod:gold_ingot").orElse(""));
    }

    @Test
    void aShortShelfIsReportedNotAbsorbed() {
        store.put(BourseSync.NS_BASELINE, "testmod:diamond", "10");
        port.contents.put("testmod:diamond", 6L);   // somebody nicked four

        BourseSync.Summary s = desk().sync();
        assertEquals(1, s.drift());
        assertEquals(0, s.staged());
        assertTrue(api.deposits.isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("SHORT by 4")));
    }

    @Test
    void stagingSurvivesADeskReboot() {
        port.contents.put("testmod:copper_ore", 3L);
        api.failDeposits = true;
        desk().sync();

        // a fresh desk over the same store (the reboot) picks the row up
        api.failDeposits = false;
        BourseSync.Summary after = desk().sync();
        assertEquals(1, after.filed());
        assertEquals(3, api.deposits.get(0).qty());
    }
}
