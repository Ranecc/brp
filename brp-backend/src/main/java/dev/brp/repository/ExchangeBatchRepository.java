package dev.brp.repository;

import dev.brp.entity.ExchangeBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ExchangeBatchRepository extends JpaRepository<ExchangeBatchEntity, Long> {

    Optional<ExchangeBatchEntity> findTopByOrderByBatchSeqDesc();

    /**
     * isNew()=true makes every Spring Data delete a silent no-op (the P4
     * lesson) — test cleanup and admin purges go through this instead.
     */
    @Modifying
    @Transactional
    @Query("delete from ExchangeBatchEntity b")
    void deleteGuard();
}
