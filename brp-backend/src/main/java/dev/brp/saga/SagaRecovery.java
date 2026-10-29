package dev.brp.saga;

import dev.brp.entity.SagaInstanceRow;
import dev.brp.repository.SagaInstanceRepository;
import dev.brp.service.impl.PegServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crash recovery: on startup, find saga rows stuck RUNNING/FAILED and
 * compensate their already-DONE siblings (the safe default — re-running an
 * uncertain forward step risks double effects; compensating a DONE step is
 * idempotent by contract). Scoping is (saga, correlation) — business
 * correlations collide (batchIds, order numbers) and cross-saga compensation
 * is sabotage. Compensation runs in REVERSE definition order, not query
 * order. Unregistered sagas are left for ops, untouched.
 */
@Component
public class SagaRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SagaRecovery.class);

    private final SagaInstanceRepository rows;
    private final SagaEngine engine;
    private final PegServiceImpl peg;

    public SagaRecovery(SagaInstanceRepository rows, SagaEngine engine, PegServiceImpl peg) {
        this.rows = rows;
        this.engine = engine;
        this.peg = peg;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SagaInstanceRow> stuck = rows.findByStateIn(List.of("RUNNING", "FAILED"));
        if (stuck.isEmpty()) {
            return;
        }
        log.info("saga recovery: {} stuck row(s)", stuck.size());
        for (SagaInstanceRow row : stuck) {
            recover(row);
        }
    }

    private void recover(SagaInstanceRow stuck) {
        List<SagaInstanceRow> done = rows.findBySagaAndCorrelationAndStateIn(
                stuck.getSaga(), stuck.getCorrelation(), List.of("DONE"));
        var def = engine.registryView().get(stuck.getSaga());
        if (def == null) {
            log.warn("saga recovery: saga {} not registered, leaving row {} for ops",
                    stuck.getSaga(), stuck.getId());
            return;
        }
        // reverse definition order; compensating a DONE step is idempotent
        List<SagaEngine.StepSpec> steps = def.steps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            SagaEngine.StepSpec step = steps.get(i);
            boolean wasDone = done.stream().anyMatch(d -> step.name().equals(d.getStep()));
            if (wasDone) {
                engine.compensateRecovered(def, stuck.getCorrelation(), step);
                // BRP-0905: a compensation that exhausted its retries leaves
                // its recovery row FAILED — funds stay frozen until a human
                // reads this. The alert IS the escalation path.
                boolean exhausted = !rows.findBySagaAndCorrelationAndStepAndState(
                        stuck.getSaga(), stuck.getCorrelation(),
                        "comp:" + step.name() + ":recovery", "FAILED").isEmpty();
                if (exhausted) {
                    peg.file(905L, 1);
                }
            }
        }
        stuck.failed();
        rows.save(stuck);
    }
}
