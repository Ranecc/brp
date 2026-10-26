package dev.brp.aspect;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import dev.brp.common.annotation.Cached;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caffeine-backed read-through cache for {@link Cached}. Outermost aspect so
 * cache hits short-circuit everything else. TTL fixed from write time; null
 * results not cached. Reads do NOT extend TTL (returning the remaining
 * duration, not 0 — 0 meant "expire on read", which made the cache
 * single-use; found in review, fixed with prejudice).
 */
@Aspect
@Order(0)
@Component
public class CachedAspect {

    private record CachedValue(Object value, long ttlNanos) {}

    private static final Expiry<String, CachedValue> TTL =
            new Expiry<>() {
                @Override public long expireAfterCreate(String k, CachedValue v, long now) {
                    return v.ttlNanos();
                }
                @Override public long expireAfterUpdate(String k, CachedValue v, long now, long d) {
                    return v.ttlNanos();
                }
                @Override public long expireAfterRead(String k, CachedValue v, long now, long d) {
                    return d; // keep the remaining TTL; a read is not a renewal
                }
            };

    /** One cache per declared maxSize bucket (keeps the attribute meaningful). */
    private final Map<Long, Cache<String, CachedValue>> byMaxSize = new ConcurrentHashMap<>();

    @Around("@annotation(cached)")
    public Object around(ProceedingJoinPoint pjp, Cached cached) throws Throwable {
        String key = SpelKeys.method(pjp).getDeclaringClass().getName()
                + "#" + SpelKeys.method(pjp).getName()
                + (cached.key().isEmpty()
                        // no SpEL key: fall back to argument identity — a bare
                        // class#method key would collide across parameters
                        ? ":" + Arrays.deepHashCode(pjp.getArgs())
                        : ":" + SpelKeys.eval(cached.key(), pjp));
        Cache<String, CachedValue> cache = byMaxSize.computeIfAbsent(cached.maxSize(),
                max -> Caffeine.newBuilder().maximumSize(max).expireAfter(TTL).build());

        CachedValue hit = cache.getIfPresent(key);
        if (hit != null) {
            return hit.value();
        }
        Object result = pjp.proceed();
        if (result != null) {
            cache.put(key, new CachedValue(result, cached.ttlSeconds() * 1_000_000_000L));
        }
        return result;
    }
}
