package dev.brp.common.aspect.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaInstanceRepository extends JpaRepository<SagaInstanceRow, Long> {}
