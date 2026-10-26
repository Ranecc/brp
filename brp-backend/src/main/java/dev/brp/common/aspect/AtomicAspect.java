package dev.brp.common.aspect;

import dev.brp.common.annotation.Atomic;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Local all-or-nothing: wraps the method body in one database transaction
 * (docs/06 section 7). Inner than @Idempotent, outer than @Compensable —
 * a saga step's writes should be atomic, while replay stays outside.
 */
@Aspect
@Order(2)
@Component
public class AtomicAspect {

    private static final class Wrapped extends RuntimeException {
        Wrapped(Throwable cause) { super(cause); }
    }

    private final TransactionTemplate tx;

    public AtomicAspect(TransactionTemplate tx) {
        this.tx = tx;
    }

    @Around("@annotation(atomic)")
    public Object around(ProceedingJoinPoint pjp, Atomic atomic) throws Throwable {
        try {
            return tx.execute(status -> {
                try {
                    return pjp.proceed();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new Wrapped(t);
                }
            });
        } catch (Wrapped w) {
            throw w.getCause();
        }
    }
}
