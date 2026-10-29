package dev.brp.repository;

import dev.brp.entity.DowntimeEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface DowntimeEventRepository extends JpaRepository<DowntimeEventEntity, Long> {

    List<DowntimeEventEntity> findByTenantIdAndSinceGreaterThanEqualAndSinceLessThan(
            String tenantId, LocalDateTime from, LocalDateTime to);

    List<DowntimeEventEntity> findByTenantIdOrderBySinceDesc(String tenantId);
}
