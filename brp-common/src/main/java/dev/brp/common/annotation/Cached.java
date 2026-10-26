package dev.brp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Read-through cache marker. Backend: Caffeine (in-process). Agent: Caffeine
 * wrapper class so a future relocation of the shaded package touches one file.
 *
 * - TTL fixed from write time (no read-refresh), matching telemetry staleness
 *   expectations;
 * - null results are NOT cached;
 * - keys are namespaced automatically by class + method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cached {

    /** SpEL key appended to the automatic class.method namespace. */
    String key() default "";

    /** Seconds since write. Default 60. */
    long ttlSeconds() default 60;

    /** Max entries of the shared annotation cache. Default 10_000. */
    long maxSize() default 10_000;
}
