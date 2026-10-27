package dev.brp.repository;

import dev.brp.entity.ChunkPurchaseKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChunkPurchaseKeyRepository
        extends JpaRepository<ChunkPurchaseKeyEntity, ChunkPurchaseKeyEntity.Pk> {
}
