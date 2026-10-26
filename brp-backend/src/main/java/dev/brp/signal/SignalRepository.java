package dev.brp.signal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalRepository extends JpaRepository<SignalCommandEntity, Long> {
    List<SignalCommandEntity> findTop50ByTenantIdAndStatusOrderByIdAsc(String tenantId, String status);
    List<SignalCommandEntity> findByTenantIdAndIdIn(String tenantId, List<Long> ids);
}
