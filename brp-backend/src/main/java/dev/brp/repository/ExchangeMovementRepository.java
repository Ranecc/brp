package dev.brp.repository;

import dev.brp.entity.ExchangeMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExchangeMovementRepository extends JpaRepository<ExchangeMovementEntity, Long> {

    Optional<ExchangeMovementEntity> findByIdemKey(String idemKey);
}
