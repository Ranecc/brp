package dev.brp.repository;

import dev.brp.entity.ManagedChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManagedChunkRepository
        extends JpaRepository<ManagedChunkEntity, ManagedChunkEntity.Pk> {

    List<ManagedChunkEntity> findByTenantId(String tenantId);

    Optional<ManagedChunkEntity> findByTenantIdAndDimensionAndChunkKey(
            String tenantId, String dimension, String chunkKey);
}
