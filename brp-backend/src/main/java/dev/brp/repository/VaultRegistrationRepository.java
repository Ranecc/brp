package dev.brp.repository;

import dev.brp.entity.VaultRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VaultRegistrationRepository extends JpaRepository<VaultRegistrationEntity, VaultRegistrationEntity.Pk> {
    List<VaultRegistrationEntity> findByTenantId(String tenantId);
}
