package dev.brp.repository;

import dev.brp.entity.SnapshotTenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnapshotTenantRepository extends JpaRepository<SnapshotTenantEntity, SnapshotTenantEntity.Pk> {
    List<SnapshotTenantEntity> findByWeekOf(String weekOf);
}
