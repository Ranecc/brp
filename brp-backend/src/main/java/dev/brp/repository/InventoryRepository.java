package dev.brp.repository;
import dev.brp.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventorySnapshotEntity, Long> {
    Optional<InventorySnapshotEntity> findByTenantIdAndContainerIdAndItemId(
            String tenantId, String containerId, String itemId);
    List<InventorySnapshotEntity> findByTenantIdOrderByContainerId(String tenantId);
}
