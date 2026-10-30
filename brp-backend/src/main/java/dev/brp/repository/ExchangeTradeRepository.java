package dev.brp.repository;

import dev.brp.entity.ExchangeTradeEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeTradeRepository extends JpaRepository<ExchangeTradeEntity, Long> {

    List<ExchangeTradeEntity> findByItemIdOrderByIdDesc(String itemId, Pageable pageable);

    List<ExchangeTradeEntity> findByBatchSeq(long batchSeq);

    List<ExchangeTradeEntity> findByBidOrderId(Long bidOrderId);

    List<ExchangeTradeEntity> findByAskOrderId(Long askOrderId);
}
