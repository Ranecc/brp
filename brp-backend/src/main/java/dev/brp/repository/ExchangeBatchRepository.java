package dev.brp.repository;

import dev.brp.entity.ExchangeBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeBatchRepository extends JpaRepository<ExchangeBatchEntity, Long> {

    Optional<ExchangeBatchEntity> findTopByOrderByBatchSeqDesc();
}
