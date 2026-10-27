package dev.brp.repository;

import dev.brp.entity.ManagedChunkEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ManagedChunkRepository
        extends JpaRepository<ManagedChunkEntity, ManagedChunkEntity.Pk> {

    List<ManagedChunkEntity> findByTenantId(String tenantId);

    Optional<ManagedChunkEntity> findByTenantIdAndDimensionAndChunkKey(
            String tenantId, String dimension, String chunkKey);

    /** SELECT ... FOR UPDATE: metering (accrue) and purchases must not lose updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ManagedChunkEntity c where c.tenantId = :tenant" +
           " and c.dimension = :dim and c.chunkKey = :key")
    Optional<ManagedChunkEntity> findForUpdate(@Param("tenant") String tenantId,
                                               @Param("dim") String dimension,
                                               @Param("key") String chunkKey);
}
