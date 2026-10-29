package dev.brp.repository;

import dev.brp.entity.BondSeriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondSeriesRepository extends JpaRepository<BondSeriesEntity, Long> {
}
