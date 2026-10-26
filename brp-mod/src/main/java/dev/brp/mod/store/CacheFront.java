package dev.brp.mod.store;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cache port for the agent core. Default impl wraps Caffeine (jar-in-jar);
 * the CORE imports only this interface, so relocating the shaded Caffeine
 * package (to dodge clashes with other mods) touches exactly one impl file.
 *
 * This file ships with a dependency-free ConcurrentHashMap fallback so the
 * skeleton compiles before Caffeine is wired; the CaffeineFront impl lands
 * with the jar-in-jar setup (build.gradle.kts notes).
 */
public interface CacheFront {

    <T> Optional<T> get(String ns, String key, Class<T> type);

    <T> T getOrLoad(String ns, String key, Class<T> type,
                    Duration ttl, Function<String, T> loader);

    /** Default in-memory fallback (no TTL eviction thread; size-capped). */
    static CacheFront inMemory(long maxSize) {
        return new CacheFront() {
            private record Val(Object v, long expiresAt) {}
            private final ConcurrentHashMap<String, Val> map = new ConcurrentHashMap<>();

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<T> get(String ns, String key, Class<T> type) {
                Val v = map.get(ns + "/" + key);
                if (v == null || v.expiresAt() < System.currentTimeMillis()) {
                    return Optional.empty();
                }
                return Optional.of((T) v.v());
            }

            @Override
            public <T> T getOrLoad(String ns, String key, Class<T> type,
                                   Duration ttl, Function<String, T> loader) {
                return get(ns, key, type).orElseGet(() -> {
                    T loaded = loader.apply(key);
                    if (map.size() > maxSize) {
                        map.clear(); // crude; CaffeineFront does this properly
                    }
                    map.put(ns + "/" + key,
                            new Val(loaded, System.currentTimeMillis() + ttl.toMillis()));
                    return loaded;
                });
            }
        };
    }
}
