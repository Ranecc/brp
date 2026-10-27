package dev.brp.repository;

import dev.brp.entity.ReconciliationFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationFindingRepository extends JpaRepository<ReconciliationFindingEntity, Long> {
    List<ReconciliationFindingEntity> findTop50ByOrderByIdDesc();
}
