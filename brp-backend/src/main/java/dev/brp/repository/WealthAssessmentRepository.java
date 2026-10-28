package dev.brp.repository;

import dev.brp.entity.WealthAssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WealthAssessmentRepository extends JpaRepository<WealthAssessmentEntity, WealthAssessmentEntity.Pk> {
    List<WealthAssessmentEntity> findByWeekOf(String weekOf);
}
