package dev.brp.repository;

import dev.brp.entity.ExchangeClearEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ExchangeClearRepository extends JpaRepository<ExchangeClearEntity, ExchangeClearEntity.Pk> {

    List<ExchangeClearEntity> findByItemIdOrderByBatchSeqDesc(String itemId, Pageable pageable);

    /** isNew()=true entities delete through the guard, never through derived deletes. */
    @Modifying
    @Transactional
    @Query("delete from ExchangeClearEntity c")
    void deleteGuard();
}
