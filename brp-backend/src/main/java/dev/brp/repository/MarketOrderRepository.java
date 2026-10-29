package dev.brp.repository;

import dev.brp.entity.MarketOrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketOrderRepository extends JpaRepository<MarketOrderEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrderEntity o where o.id = :id")
    MarketOrderEntity findForUpdate(@Param("id") Long id);

    /**
     * The book, price-time priority: ASKs cheapest first, BIDs richest first,
     * age breaking ties — the ordering every exchange inherited from paper.
     */
    @Query("""
            select o from MarketOrderEntity o where o.side = 'ASK' and o.status in ('NEW', 'PARTIAL')
            and o.priceCents <= :maxPrice and o.tenantId <> :excludeTenant
            order by o.priceCents asc, o.createdAt asc, o.id asc
            """)
    List<MarketOrderEntity> asksFor(@Param("maxPrice") long maxPrice,
                                    @Param("excludeTenant") String excludeTenant,
                                    Pageable pageable);

    @Query("""
            select o from MarketOrderEntity o where o.side = 'BID' and o.status in ('NEW', 'PARTIAL')
            and o.priceCents >= :minPrice and o.tenantId <> :excludeTenant
            order by o.priceCents desc, o.createdAt asc, o.id asc
            """)
    List<MarketOrderEntity> bidsFor(@Param("minPrice") long minPrice,
                                    @Param("excludeTenant") String excludeTenant,
                                    Pageable pageable);

    List<MarketOrderEntity> findByExpiresAtBeforeAndStatusIn(OffsetDateTime cutoff, List<String> statuses);

    List<MarketOrderEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<MarketOrderEntity> findByTenantIdAndIdemKey(String tenantId, String idemKey);
}
