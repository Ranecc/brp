package dev.brp.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.brp.common.annotation.Idempotent;
import dev.brp.repository.IdemStoreRepository;
import dev.brp.entity.IdemStoreRow;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;

/**
 * Post-hoc effect idempotency: dedup key -> stored first response replay
 * (docs/06 section 7). Sits OUTSIDE @Atomic so a replay short-circuits before
 * any transaction or saga row is touched.
 *
 * Crash window honesty: the response is stored after the method returns. A
 * crash between effect and store can re-execute on retry — which is exactly
 * why financial mutations use the ledger's same-transaction idempotency row
 * instead of this aspect. Use for syncs, enqueues, notifications, read-model
 * writes.
 */
@Aspect
@Order(1)
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdemStoreRepository store;
    private final ObjectMapper mapper;

    public IdempotencyAspect(IdemStoreRepository store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        Method method = SpelKeys.method(pjp);
        if (method.getReturnType() == void.class) {
            // Nothing replayable; caller still benefits from @Atomic etc.
            return pjp.proceed();
        }
        String key = method.getDeclaringClass().getName() + "#" + method.getName()
                + ":" + SpelKeys.eval(idempotent.key(), pjp);

        var hit = store.findById(key);
        if (hit.isPresent()) {
            if (Duration.between(hit.get().getCreatedAt(), Instant.now())
                    .compareTo(Duration.ofSeconds(idempotent.ttlSeconds())) > 0) {
                store.delete(hit.get()); // TTL expired: treat as new
            } else {
                log.debug("idempotent replay: {}", key);
                return mapper.readValue(hit.get().getResponseJson(), method.getReturnType());
            }
        }

        Object result = pjp.proceed();
        try {
            store.saveAndFlush(new IdemStoreRow(key, mapper.writeValueAsString(result)));
        } catch (DataIntegrityViolationException race) {
            // Concurrent duplicate: the first writer's stored response stands.
            log.debug("idempotent race lost (expected under retry): {}", key);
        }
        return result;
    }
}
