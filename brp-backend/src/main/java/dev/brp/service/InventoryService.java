package dev.brp.service;

import dev.brp.contract.InventoryDtos.InventoryAck;
import dev.brp.contract.InventoryDtos.InventoryBatch;
import dev.brp.dto.StockLevelDto;

import java.util.List;

/** WMS snapshot domain contract (impl: {@link InventoryServiceImpl}). */
public interface InventoryService {

    InventoryAck upsert(String tenantId, InventoryBatch batch);

    List<StockLevelDto> list(String tenantId);
}
