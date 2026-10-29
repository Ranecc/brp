package dev.brp;

import dev.brp.repository.SagaInstanceRepository;
import dev.brp.saga.SagaEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Full-stack saga: failure at step N auto-compensates completed steps in reverse. */
@SpringBootTest
class SagaEngineTest {

    @Autowired SagaEngine engine;
    @Autowired SagaInstanceRepository rows;

    @Test
    void failureTriggersReverseOrderAutoCompensation() {
        List<String> journal = new ArrayList<>();
        engine.register(SagaEngine.saga("gift-flow")
                .step("freeze", p -> {
                    journal.add("s:freeze");
                    return null;
                }, "unfreeze", p -> {
                    journal.add("c:unfreeze");
                    return null;
                })
                .step("deliver", p -> {
                    journal.add("s:deliver");
                    throw new IllegalStateException("delivery exploded");
                }, "recall", p -> journal.add("c:recall"))
                .build());

        String correlation = UUID.randomUUID().toString();
        SagaEngine.Outcome outcome = engine.run("gift-flow", correlation, Map.of());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.failedStep()).isEqualTo("deliver");
        assertThat(outcome.compensated()).containsExactly("freeze");
        assertThat(journal).containsExactly("s:freeze", "s:deliver", "c:unfreeze");

        // durable audit: freeze DONE->COMPENSATED, deliver FAILED, comp row DONE
        var freezeRows = rows.findBySagaAndCorrelationAndStepAndState(
                "gift-flow", correlation, "freeze", "COMPENSATED");
        var deliverRows = rows.findBySagaAndCorrelationAndStepAndState(
                "gift-flow", correlation, "deliver", "FAILED");
        assertThat(freezeRows).hasSize(1);
        assertThat(deliverRows).hasSize(1);
    }

    @Test
    void successPathRecordsDoneStepsOnly() {
        List<String> journal = new ArrayList<>();
        engine.register(SagaEngine.saga("happy-path")
                .step("a", p -> journal.add("a"), "undoA", p -> journal.add("undoA"))
                .step("b", p -> journal.add("b"), "undoB", p -> journal.add("undoB"))
                .build());

        SagaEngine.Outcome outcome =
                engine.run("happy-path", UUID.randomUUID().toString(), Map.of());

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.compensated()).isEmpty();
        assertThat(journal).containsExactly("a", "b");
    }

    @Test
    void recoveryCompensationReceivesTheRealPayloadNotNull() {
        // audit #1: compensateRecovered used to pass null; any compensation
        // reading its payload NPE'd three times and the funds stayed frozen.
        List<Object> received = new ArrayList<>();
        var def = SagaEngine.saga("vault-flow")
                .step("freeze", p -> "frozen", "unfreeze", p -> {
                    received.add(p);
                    return null;
                })
                .build();
        engine.register(def);

        String correlation = UUID.randomUUID().toString();
        assertThat(engine.run("vault-flow", correlation,
                Map.of("amountCents", 42_000L, "requestId", "r-1")).success()).isTrue();

        // simulate the crash leftover: the DONE freeze row, recovered solo
        engine.compensateRecovered(def, correlation, def.steps().get(0));

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).isInstanceOf(Map.class);
        // JSON round-trip numbers arrive as Integer/Long depending on size —
        // the payload's CONTENT is the contract, not its boxed type.
        assertThat(((Number) ((Map<?, ?>) received.get(0)).get("amountCents")).longValue())
                .isEqualTo(42_000L);
    }

    @Test
    void exhaustedCompensationsEscalateAs0905(
            @Autowired dev.brp.saga.SagaRecovery recovery,
            @Autowired dev.brp.repository.AnomalyHitRepository hits) {
        engine.register(SagaEngine.saga("doomed-flow")
                .step("freeze", p -> "ok", "unfreeze", p -> {
                    throw new IllegalStateException("compensation desk is closed forever");
                })
                .build());
        String correlation = UUID.randomUUID().toString();
        assertThat(engine.run("doomed-flow", correlation, Map.of()).success()).isTrue();

        // the crash leftover: a RUNNING row the recovery will try to settle
        rows.save(new dev.brp.entity.SagaInstanceRow("doomed-flow", "freeze", correlation, "unfreeze"));

        int before905 = (int) hits.findAll().stream()
                .filter(h -> h.getRuleId() == 905L).count();
        recovery.run(null);

        assertThat(rows.findBySagaAndCorrelationAndStepAndState(
                "doomed-flow", correlation, "comp:freeze:recovery", "FAILED")).hasSize(1);
        long after904 = hits.findAll().stream().filter(h -> h.getRuleId() == 905L).count();
        org.assertj.core.api.Assertions.assertThat(after904).isGreaterThan(before905);
    }
}
