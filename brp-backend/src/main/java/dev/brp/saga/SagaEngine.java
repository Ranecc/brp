package dev.brp.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.brp.repository.SagaInstanceRepository;
import dev.brp.entity.SagaInstanceRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Saga orchestration, full stack (docs/06 section 4): definitions register an
 * ordered step list; run() executes steps, durably recording each; on failure
 * it dispatches compensations for COMPLETED steps in reverse order (each with
 * retries). Crash leftovers (rows stuck RUNNING/FAILED without a terminal
 * saga outcome) are recovered at startup by {@link SagaRecovery}.
 *
 * Every step and compensation is itself expected to be idempotent — retries
 * and replays are always safe by contract, never by accident.
 */
@Component
public class SagaEngine {

    private static final Logger log = LoggerFactory.getLogger(SagaEngine.class);

    public record StepSpec(
            String name,
            Function<Object, Object> body,
            String compensationName,
            Function<Object, Object> compensation) {}

    public static final class Definition {
        final String name;
        final List<StepSpec> steps;
        private Definition(String name, List<StepSpec> steps) {
            this.name = name;
            this.steps = List.copyOf(steps);
        }
        public String name() { return name; }
        public List<StepSpec> steps() { return steps; }
    }

    public static final class Builder<T> {
        private final String name;
        private final List<StepSpec> steps = new ArrayList<>();
        private Builder(String name) { this.name = name; }

        public Builder<T> step(String name, Function<T, Object> body,
                               String compensationName, Function<T, Object> compensation) {
            steps.add(new StepSpec(name, cast(body), compensationName, cast(compensation)));
            return this;
        }

        public Definition build() { return new Definition(name, steps); }

        @SuppressWarnings("unchecked")
        private static <U> Function<Object, Object> cast(Function<U, Object> fn) {
            return (Function<Object, Object>) fn;
        }
    }

    public static <T> Builder<T> saga(String name) { return new Builder<>(name); }

    public record Outcome(boolean success, String failedStep, List<String> compensated) {}

    private final Map<String, Definition> registry = new ConcurrentHashMap<>();
    private final SagaInstanceRepository rows;
    private final SagaRecorder recorder;
    private final ObjectMapper mapper;

    public SagaEngine(SagaInstanceRepository rows, SagaRecorder recorder, ObjectMapper mapper) {
        this.rows = rows;
        this.recorder = recorder;
        this.mapper = mapper;
    }

    public void register(Definition definition) {
        registry.put(definition.name, definition);
    }

    public Outcome run(String sagaName, String correlation, Object payload) {
        Definition def = registry.get(sagaName);
        if (def == null) {
            throw new IllegalArgumentException("unknown saga: " + sagaName);
        }
        List<StepSpec> completed = new ArrayList<>();
        for (StepSpec step : def.steps) {
            SagaInstanceRow row = record(sagaName, step.name(), correlation,
                    step.compensationName(), payload);
            try {
                step.body().apply(payload);
                recorder.done(row);
                completed.add(step);
            } catch (Throwable t) {
                recorder.failed(row);
                List<String> compensated = compensate(def, correlation, completed, payload);
                log.warn("saga {} failed at step {}: compensated {}",
                        sagaName, step.name(), compensated, t);
                return new Outcome(false, step.name(), compensated);
            }
        }
        return new Outcome(true, null, List.of());
    }

    /** Reverse-order compensation with retries; each dispatch is durable. */
    private List<String> compensate(Definition def, String correlation,
                                    List<StepSpec> completed, Object payload) {
        List<String> done = new ArrayList<>();
        for (int i = completed.size() - 1; i >= 0; i--) {
            StepSpec step = completed.get(i);
            SagaInstanceRow row = record(def.name(), "comp:" + step.name(), correlation,
                    null, payload);
            dispatchWithRetry(step, payload, row);
            done.add(step.name());
            // mark the original step row compensated
            rows.findBySagaAndCorrelationAndStepAndState(
                    def.name(), correlation, step.name(), "DONE").forEach(r -> {
                r.compensated();
                rows.save(r);
            });
        }
        return done;
    }

    private void dispatchWithRetry(StepSpec step, Object payload, SagaInstanceRow row) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (step.compensation() != null) {
                    step.compensation().apply(payload);
                }
                row.done();
                rows.save(row);
                return;
            } catch (Throwable t) {
                row.failed();
                rows.save(row);
                log.warn("compensation {} attempt {}/3 failed", step.compensationName(), attempt, t);
            }
        }
        // Compensation exhausted: stays FAILED — SagaRecovery picks it up.
    }

    private SagaInstanceRow record(String saga, String step, String correlation,
                                   String compensation, Object payload) {
        SagaInstanceRow row = new SagaInstanceRow(saga, step, correlation, compensation);
        row.setPayload(safeJson(payload));
        // REQUIRES_NEW: survives a caller's rollback (docs/06 section 4)
        return recorder.record(row);
    }

    /** Recovery payload: the row's JSON, or null — loudly, never guessed. */
    private Object deserializePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("recovery payload undecodable: {}", e.getMessage());
            return null;
        }
    }

    private String safeJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Single-step compensation entrypoint for SagaRecovery. The forward row
     * kept the payload JSON all along — recovery deserializes it and feeds
     * the closure the SAME payload it ran with (audit #1: null payload NPE'd
     * every compensation and froze the funds permanently).
     */
    public void compensateRecovered(Definition def, String correlation, StepSpec step) {
        String json = rows.findBySagaAndCorrelationAndStepAndState(
                        def.name(), correlation, step.name(), "DONE").stream()
                .findFirst()
                .map(SagaInstanceRow::getPayload)
                .orElse(null);
        Object payload = deserializePayload(json);
        SagaInstanceRow row = record(def.name(), "comp:" + step.name() + ":recovery",
                correlation, null, payload);
        dispatchWithRetry(step, payload, row);
        rows.findBySagaAndCorrelationAndStepAndState(
                def.name(), correlation, step.name(), "DONE").forEach(r -> {
            r.compensated();
            rows.save(r);
        });
    }

    Map<String, Definition> registryView() { return registry; }
}
