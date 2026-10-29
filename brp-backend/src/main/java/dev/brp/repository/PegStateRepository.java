package dev.brp.repository;

import dev.brp.entity.PegStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PegStateRepository extends JpaRepository<PegStateEntity, String> {
}
