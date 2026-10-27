package dev.brp.mod.adapters;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * P2-14 census policy (pure): which mod namespaces count as BRP machines.
 * The API-deep adapters (KineticBlockEntity rpm/su, ME network streams —
 * see Adapters.java) remain the documented transcription exercise; the
 * census is the metering hook that ships NOW, behind ModList guards.
 */
public final class NamespacePolicy {

    /** The tech mods BRP meters out of the box. */
    public static final Set<String> DEFAULT_NAMESPACES = Set.of(
            "create", "ae2", "computercraft");

    private final Set<String> namespaces;

    public NamespacePolicy() {
        this(DEFAULT_NAMESPACES);
    }

    public NamespacePolicy(Set<String> namespaces) {
        this.namespaces = Set.copyOf(namespaces);
    }

    public Set<String> namespaces() {
        return namespaces;
    }

    /** @return counts for watched namespaces only, zeros dropped. */
    public Map<String, Integer> watched(Map<String, Integer> namespaceTotals) {
        Map<String, Integer> out = new TreeMap<>();
        for (var entry : namespaceTotals.entrySet()) {
            if (namespaces.contains(entry.getKey()) && entry.getValue() > 0) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }
}
