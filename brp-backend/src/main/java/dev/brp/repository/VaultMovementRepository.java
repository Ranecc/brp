package dev.brp.repository;

import dev.brp.entity.VaultMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VaultMovementRepository extends JpaRepository<VaultMovementEntity, Long> {
    List<VaultMovementEntity> findByTenantIdAndContainerRef(String tenantId, String containerRef);

    List<VaultMovementEntity> findByTenantIdAndContainerRefAndItemId(
            String tenantId, String containerRef, String itemId);
}
