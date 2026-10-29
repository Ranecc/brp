package dev.brp.repository;

import dev.brp.entity.WorkorderCostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkorderCostRepository extends JpaRepository<WorkorderCostEntity, WorkorderCostEntity.Pk> {
}
