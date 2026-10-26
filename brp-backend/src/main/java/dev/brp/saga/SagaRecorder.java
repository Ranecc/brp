package dev.brp.saga;

import dev.brp.entity.SagaInstanceRow;
import dev.brp.repository.SagaInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes saga rows in an INDEPENDENT transaction (REQUIRES_NEW), so a step's
 * RUNNING/DONE/FAILED audit trail survives the caller's rollback. Without
 * this, @Atomic on a @Compensable method would roll the "durable" row away
 * and SagaRecovery would be recovering fiction.
 */
@Component
public class SagaRecorder {

    private final SagaInstanceRepository rows;
    private final TransactionTemplate requiresNew;

    public SagaRecorder(SagaInstanceRepository rows, PlatformTransactionManager txManager) {
        this.rows = rows;
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.requiresNew = t;
    }

    public SagaInstanceRow record(SagaInstanceRow row) {
        return requiresNew.execute(status -> rows.saveAndFlush(row));
    }

    public void done(SagaInstanceRow row) {
        requiresNew.executeWithoutResult(status -> {
            row.done();
            rows.save(row);
        });
    }

    public void failed(SagaInstanceRow row) {
        requiresNew.executeWithoutResult(status -> {
            row.failed();
            rows.save(row);
        });
    }
}
