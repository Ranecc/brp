package dev.brp.repository;

import dev.brp.entity.ExchangeClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeClaimRepository extends JpaRepository<ExchangeClaimEntity, Long> {

    List<ExchangeClaimEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    List<ExchangeClaimEntity> findByStatus(String status);
}
