package dev.brp.common.aspect.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaInstanceRepository extends JpaRepository<SagaInstanceRow, Long> {
    List<SagaInstanceRow> findBySagaAndCorrelationAndStepAndState(
            String saga, String correlation, String step, String state);

    List<SagaInstanceRow> findByCorrelationAndStateIn(String correlation, List<String> states);
}
