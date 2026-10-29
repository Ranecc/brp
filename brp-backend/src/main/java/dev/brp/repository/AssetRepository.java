package dev.brp.repository;

import dev.brp.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<AssetEntity, Long> {

    List<AssetEntity> findByTenantIdOrderByIdAsc(String tenantId);
}
