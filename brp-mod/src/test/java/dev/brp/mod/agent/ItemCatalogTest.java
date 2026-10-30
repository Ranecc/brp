package dev.brp.mod.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalog push (docs/14 §8): 500 per batch, silent when the registry
 * has not changed, and a registry read failure is weather, not a crash.
 */
class ItemCatalogTest {

    private final BourseTestKit.TestApi api = new BourseTestKit.TestApi();
    private final List<String> log = new ArrayList<>();

    private ItemCatalog catalog(java.util.function.Supplier<List<String>> ids) {
        return new ItemCatalog(ids, api, log::add);
    }

    @Test
    void pushesInBatchesOfFiveHundred() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            ids.add(String.format("testmod:item_%04d", i));
        }
        catalog(() -> ids).push();
        assertEquals(3, api.catalogBatches.size());
        assertEquals(500, api.catalogBatches.get(0).size());
        assertEquals(500, api.catalogBatches.get(1).size());
        assertEquals(200, api.catalogBatches.get(2).size());
        long total = api.catalogBatches.stream().mapToLong(List::size).sum();
        assertEquals(1200, total);
    }

    @Test
    void anUnchangedRegistryStaysSilent() {
        List<String> ids = List.of("minecraft:emerald", "ae2:certus_quartz_crystal");
        ItemCatalog c = catalog(() -> ids);
        c.push();
        c.push();
        c.push();
        assertEquals(1, api.catalogBatches.size());
    }

    @Test
    void theSameInstanceReppliesWhenTheRegistryChanges() {
        List<String> ids = new ArrayList<>(List.of("minecraft:emerald"));
        ItemCatalog c = catalog(() -> List.copyOf(ids));
        c.push();
        ids.add("testmod:late_arrival");
        c.push();
        assertEquals(2, api.catalogBatches.size());
    }

    @Test
    void aBrokenRegistryIsWeather() {
        assertDoesNotThrow(() -> catalog(() -> {
            throw new IllegalStateException("registries offline");
        }).push());
        assertTrue(api.catalogBatches.isEmpty());
        assertTrue(log.stream().anyMatch(l -> l.contains("catalog read failed")));
    }

    @Test
    void anEmptyRegistryShipsNothing() {
        catalog(List::of).push();
        assertTrue(api.catalogBatches.isEmpty());
    }
}
