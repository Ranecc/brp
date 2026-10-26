package dev.brp.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Effect-idempotency for command-plane operations: at most one effect per key,
 * replays return the stored first response.
 *
 * Semantics (docs/06-transactions.md section 7):
 * - key is a SpEL expression over method parameters (backend) or the
 *   annotated runner argument (agent);
 * - TTL bounds replayability; after expiry a repeat is treated as new;
 * - delivery stays at-least-once — this annotation makes the EFFECT once.
 *
 * IMPORTANT: this is post-hoc idempotency (store response after success).
 * Financial mutations must additionally use the ledger's same-transaction
 * idempotency row (see LedgerService) which is crash-safe by construction.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    /** SpEL key, e.g. "#req.batchId()". */
    String key();

    /** Seconds a stored response stays replayable. Default 24h. */
    long ttlSeconds() default 86_400;
}
