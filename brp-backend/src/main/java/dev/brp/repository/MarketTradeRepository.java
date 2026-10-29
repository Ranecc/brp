package dev.brp.repository;

import dev.brp.entity.MarketTradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketTradeRepository extends JpaRepository<MarketTradeEntity, Long> {
}
