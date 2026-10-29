package dev.brp.repository;

import dev.brp.entity.WashFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WashFindingRepository extends JpaRepository<WashFindingEntity, Long> {
    List<WashFindingEntity> findTop100ByOrderByScoreDesc();

    boolean existsByTenantIdAndAccountsHashAndWeekOf(
            String tenantId, String accountsHash, java.time.LocalDate weekOf);
}
