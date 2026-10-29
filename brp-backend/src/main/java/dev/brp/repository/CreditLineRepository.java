package dev.brp.repository;

import dev.brp.entity.CreditLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditLineRepository extends JpaRepository<CreditLineEntity, String> {
}
