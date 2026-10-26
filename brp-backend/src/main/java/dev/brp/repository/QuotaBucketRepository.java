package dev.brp.repository;

import dev.brp.entity.QuotaBucketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotaBucketRepository
        extends JpaRepository<QuotaBucketEntity, QuotaBucketEntity.Pk> {
    List<QuotaBucketEntity> findByTenantId(String tenantId);
}
