package dev.brp.repository;

import dev.brp.entity.EconomyDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface EconomyDailyRepository extends JpaRepository<EconomyDailyEntity, Long> {
    Optional<EconomyDailyEntity> findByReportDateAndCurrency(LocalDate date, String currency);
}
