package dev.brp.mod.adapters;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Census policy (P2-14): watched namespaces only, zeros dropped — counting
 * every chest on the server is not metering, it is inventory.
 */
class NamespacePolicyTest {

    @Test
    void watchedNamespacesSurviveAndNoiseDrops() {
        var policy = new NamespacePolicy();
        var watched = policy.watched(Map.of(
                "create", 12, "minecraft", 9001, "ae2", 7, "computercraft", 3,
                "somelib", 404));
        assertEquals(Map.of("create", 12, "ae2", 7, "computercraft", 3), watched);
    }

    @Test
    void zeroCountsAreSilence() {
        assertEquals(Map.of(), new NamespacePolicy().watched(Map.of("create", 0, "minecraft", 5)));
    }
}
