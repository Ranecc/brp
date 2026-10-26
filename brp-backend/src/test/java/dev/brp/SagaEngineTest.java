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
}
