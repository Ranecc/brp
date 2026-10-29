package dev.brp.repository;

import dev.brp.entity.CpiDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CpiDailyRepository extends JpaRepository<CpiDailyEntity, java.time.LocalDate> {
    List<CpiDailyEntity> findTop90ByOrderByDayDesc();
}
