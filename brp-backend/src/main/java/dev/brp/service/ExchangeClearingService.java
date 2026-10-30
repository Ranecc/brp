package dev.brp.service;

import dev.brp.dto.ExchangeDtos.ClearResponse;
import dev.brp.dto.ExchangeDtos.ClearRunResponse;
import dev.brp.dto.ExchangeDtos.TradeResponse;

import java.util.List;

/**
 * The batch auction (docs/14 §6, R-8): clear every N minutes in one
 * transaction — guard, gather, kink, settle, crawl, observe. Open at 9:00,
 * closed at 9:05, like all great thin markets.
 */
public interface ExchangeClearingService {

    ClearRunResponse runBatch();

    List<ClearResponse> clears(String itemId, int limit);

    List<TradeResponse> trades(String itemId, int limit);
}
