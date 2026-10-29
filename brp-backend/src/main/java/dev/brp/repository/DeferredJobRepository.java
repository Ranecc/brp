package dev.brp.repository;

import dev.brp.entity.DeferredJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DeferredJobRepository extends JpaRepository<DeferredJobEntity, Long> {

    List<DeferredJobEntity> findByTenantIdOrderByCreatedAtAsc(String tenantId);

    List<DeferredJobEntity> findByStatusOrderByCreatedAtAsc(String status);

    List<DeferredJobEntity> findByTenantIdAndIdemKey(String tenantId, String idemKey);

    List<DeferredJobEntity> findByStatusAndEarliestLessThanEqual(String status, LocalDateTime now);
}
