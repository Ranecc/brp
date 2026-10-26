package dev.brp.repository;
import dev.brp.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKeyEntity, IdempotencyKeyEntity.Pk> {
}
