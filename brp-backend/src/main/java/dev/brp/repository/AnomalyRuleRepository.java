package dev.brp.repository;

import dev.brp.entity.AnomalyRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnomalyRuleRepository extends JpaRepository<AnomalyRuleEntity, Long> {
    List<AnomalyRuleEntity> findByStatusOrderByIdAsc(String status);
}
