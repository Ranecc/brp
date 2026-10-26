package dev.brp.common.aspect;

import dev.brp.common.annotation.Compensable;
import dev.brp.common.aspect.store.SagaInstanceRepository;
import dev.brp.common.aspect.store.SagaInstanceRow;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga step recorder (docs/06 section 4): durable RUNNING row before the
 * body, DONE on success, FAILED + compensation name on exception. P0 treats
 * the table as the audit trail for ops-driven compensation; the dispatching
 * saga engine is the first P1 business feature to build on it.
 */
@Aspect
@Order(3)
@Component
public class CompensableAspect {

    private static final Logger log = LoggerFactory.getLogger(CompensableAspect.class);

    private final SagaInstanceRepository sagas;

    public CompensableAspect(SagaInstanceRepository sagas) {
        this.sagas = sagas;
    }

    @Around("@annotation(comp)")
    public Object around(ProceedingJoinPoint pjp, Compensable comp) throws Throwable {
        String correlation = comp.correlation().isEmpty()
                ? UUID.randomUUID().toString()
                : SpelKeys.eval(comp.correlation(), pjp);
        SagaInstanceRow row = sagas.saveAndFlush(new SagaInstanceRow(
                comp.saga(), comp.step(), correlation, comp.compensation()));
        try {
            Object result = pjp.proceed();
            row.done();
            sagas.save(row);
            return result;
        } catch (Throwable t) {
            row.failed();
            sagas.save(row);
            log.warn("saga step failed: saga={} step={} correlation={} compensation={}",
                    comp.saga(), comp.step(), correlation, comp.compensation(), t);
            throw t;
        }
    }
}
