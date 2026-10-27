package dev.brp.repository;

import dev.brp.entity.AnomalyHitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnomalyHitRepository extends JpaRepository<AnomalyHitEntity, Long> {
    List<AnomalyHitEntity> findTop50ByOrderByIdDesc();
}
