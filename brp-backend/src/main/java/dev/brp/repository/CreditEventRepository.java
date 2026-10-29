package dev.brp.repository;

import dev.brp.entity.CreditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditEventRepository extends JpaRepository<CreditEventEntity, Long> {

    List<CreditEventEntity> findByTenantIdOrderByIdDesc(String tenantId);
}
