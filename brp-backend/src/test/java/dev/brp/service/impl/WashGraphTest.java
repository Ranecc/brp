package dev.brp.service.impl;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The graph machinery (docs/12 §11): Kosaraju finds the club, the bounded
 * count finds the round-trips, and the score stays in [0,1].
 */
class WashGraphTest {

    private static Map<String, Set<String>> graph(Object... pairs) {
        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            adj.computeIfAbsent((String) pairs[i], k -> new HashSet<>())
                    .add((String) pairs[i + 1]);
        }
        return adj;
    }

    @Test
    void kosarajuFindsTheTwoMemberClubAndIgnoresBystanders() {
        var components = WashServiceImpl.kosaraju(graph(
                "a", "b", "b", "a", "b", "c", "c", "d"));
        // {a,b} is a real SCC; {c}, {d} are loners
        assertThat(components.values()).anyMatch(c -> c.size() == 2 && c.containsAll(Set.of("a", "b")));
        assertThat(components.values()).anyMatch(c -> c.equals(Set.of("d")));
    }

    @Test
    void shortCyclesAreCountedOnceEach() {
        // a<->b is one 2-cycle; a->b->c->a is one 3-cycle
        long twoCycle = WashServiceImpl.countShortCycles(
                Set.of("a", "b"), graph("a", "b", "b", "a"));
        assertThat(twoCycle).isEqualTo(1);

        long mixed = WashServiceImpl.countShortCycles(
                Set.of("a", "b", "c"), graph("a", "b", "b", "a", "b", "c", "c", "a"));
        assertThat(mixed).isEqualTo(2);
    }

    @Test
    void oneWayFlowsNeverBecomeFindings() {
        var components = WashServiceImpl.kosaraju(graph("a", "b", "b", "c", "c", "d"));
        assertThat(components.values()).noneMatch(c -> c.size() >= 2);
    }

    @Test
    void selfLoopsAreNotCommerce() {
        long cycles = WashServiceImpl.countShortCycles(
                Set.of("a"), graph("a", "a"));
        assertThat(cycles).isZero();
    }
}
