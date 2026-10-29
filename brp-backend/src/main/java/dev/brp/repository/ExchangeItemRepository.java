package dev.brp.repository;

import dev.brp.entity.ExchangeItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeItemRepository extends JpaRepository<ExchangeItemEntity, String> {

    List<ExchangeItemEntity> findByListedState(String listedState);

    List<ExchangeItemEntity> findByListedStateNot(String listedState);
}
