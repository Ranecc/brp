package dev.brp.repository;

import dev.brp.entity.QuotaBucketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotaBucketRepository
        extends JpaRepository<QuotaBucketEntity, QuotaBucketEntity.Pk> {
    List<QuotaBucketEntity> findByTenantId(String tenantId);

    /** SELECT ... FOR UPDATE: placement verdicts serialize (audit #6). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select q from QuotaBucketEntity q"
            + " where q.tenantId = :tenant and q.resource = :resource")
    java.util.Optional<QuotaBucketEntity> findForUpdate(
            @org.springframework.data.repository.query.Param("tenant") String tenantId,
            @org.springframework.data.repository.query.Param("resource") String resource);
}
