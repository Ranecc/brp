package dev.brp.repository;

import dev.brp.entity.OutboxMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessageEntity, Long> {
    List<OutboxMessageEntity> findTop20ByStatusOrderByIdAsc(String status);
}
