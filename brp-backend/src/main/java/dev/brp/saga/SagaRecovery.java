package dev.brp.saga;

import dev.brp.common.aspect.store.SagaInstanceRepository;
import dev.brp.common.aspect.store.SagaInstanceRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crash recovery: on startup, find saga rows stuck RUNNING/FAILED and
 * compensate their already-DONE siblings (the safe default — re-running an
 * uncertain forward step risks double effects, compensating a DONE step is
 * idempotent by contract). Unregistered saga names are logged for ops.
 */
@Component
public class SagaRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SagaRecovery.class);

    private final SagaInstanceRepository rows;
    private final SagaEngine engine;

    public SagaRecovery(SagaInstanceRepository rows, SagaEngine engine) {
        this.rows = rows;
        this.engine = engine;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SagaInstanceRow> stuck = rows.findAll().stream()
                .filter(r -> "RUNNING".equals(r.getState()) || "FAILED".equals(r.getState()))
                .toList();
        if (stuck.isEmpty()) {
            return;
        }
        log.info("saga recovery: {} stuck row(s)", stuck.size());
        for (SagaInstanceRow row : stuck) {
            recover(row);
        }
    }

    private void recover(SagaInstanceRow stuck) {
        List<SagaInstanceRow> done = rows.findByCorrelationAndStateIn(
                stuck.getCorrelation(), List.of("DONE"));
        if (done.isEmpty()) {
            stuck.failed();  // nothing completed: mark terminal, no compensation needed
            rows.save(stuck);
            return;
        }
        for (SagaInstanceRow completed : done) {
            SagaEngine.Definition def = engine.registryView().get(completed.getSaga());
            if (def == null) {
                log.warn("saga recovery: saga {} not registered, skipping manual row {}",
                        completed.getSaga(), completed.getId());
                continue;
            }
            def.steps().stream()
                    .filter(s -> s.name().equals(completed.getStep()))
                    .findFirst()
                    .ifPresent(s -> {
                        engine.compensateRecovered(def, stuck.getCorrelation(), s);
                    });
        }
        stuck.failed();
        rows.save(stuck);
    }
}
