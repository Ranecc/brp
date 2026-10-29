package dev.brp.repository;

import dev.brp.entity.PegHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PegHistoryRepository extends JpaRepository<PegHistoryEntity, Long> {
    List<PegHistoryEntity> findByLegOrderByIdDesc(String leg);
}
