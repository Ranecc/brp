package dev.brp.repository;

import dev.brp.entity.ExchangeOrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ExchangeOrderRepository extends JpaRepository<ExchangeOrderEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ExchangeOrderEntity o where o.id = :id")
    ExchangeOrderEntity findForUpdate(@Param("id") Long id);

    /** The item's resting book, price-time priority (docs/14 §6). */
    @Query("""
            select o from ExchangeOrderEntity o where o.itemId = :itemId
            and o.status in ('NEW', 'PARTIAL')
            order by o.createdAt asc, o.id asc
            """)
    List<ExchangeOrderEntity> restingForItem(@Param("itemId") String itemId);

    List<ExchangeOrderEntity> findByExpiresAtBeforeAndStatusIn(OffsetDateTime cutoff, List<String> statuses);

    @Query("select distinct o.itemId from ExchangeOrderEntity o where o.status in ('NEW', 'PARTIAL')")
    List<String> itemIdsWithResting();

    List<ExchangeOrderEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ExchangeOrderEntity> findByTenantIdAndItemIdOrderByCreatedAtDesc(String tenantId, String itemId);

    Optional<ExchangeOrderEntity> findByTenantIdAndIdemKey(String tenantId, String idemKey);
}
