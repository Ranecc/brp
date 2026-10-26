package dev.brp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * All-or-nothing marker for multi-step writes.
 *
 * Backend semantics: the method body runs inside one database transaction —
 * any exception rolls back every write.
 * Agent semantics: the method body runs inside one SQLite transaction.
 *
 * This is LOCAL atomicity only; cross-system consistency is @Compensable
 * (saga) territory. Stacking both is the standard shape:
 * @Atomic @Compensable(...) for steps that write and participate in a saga.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Atomic {

    /** Fail fast instead of waiting on a contended row; advisory only. */
    long timeoutMs() default 5_000;
}
