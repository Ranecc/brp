package dev.brp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Saga step marker: names the compensation that undoes this step when a later
 * step fails (docs/06-transactions.md section 4). There is NO distributed
 * rollback; compensations are themselves idempotent commands.
 *
 * The runtime records (saga, step, correlationId, state) durably before the
 * body runs, marks DONE on success, FAILED on exception, and — from P1's saga
 * engine onward — dispatches the named compensation. In P0 the record is the
 * audit trail; humans/ops drive compensation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Compensable {

    /** Saga name, e.g. "vault-withdraw". */
    String saga();

    /** Step name within the saga, e.g. "freeze". */
    String step();

    /** Compensation step name registered for the same saga, e.g. "unfreeze". */
    String compensation();

    /** SpEL correlation key tying the saga's steps together (optional). */
    String correlation() default "";
}
