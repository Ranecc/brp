package dev.brp.repository;

import dev.brp.entity.BondAccrualEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BondAccrualRepository extends JpaRepository<BondAccrualEntity, BondAccrualEntity.Pk> {

    List<BondAccrualEntity> findBySeriesIdAndTenantId(Long seriesId, String tenantId);
}
