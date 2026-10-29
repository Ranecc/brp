package dev.brp.repository;

import dev.brp.entity.BondSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BondSubscriptionRepository extends JpaRepository<BondSubscriptionEntity, Long> {

    List<BondSubscriptionEntity> findBySeriesIdOrderByIdAsc(Long seriesId);

    Optional<BondSubscriptionEntity> findBySeriesIdAndTenantId(Long seriesId, String tenantId);
}
