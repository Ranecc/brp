package dev.brp.service.impl;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.*;

import dev.brp.common.BadRequestException;
import dev.brp.common.annotation.Atomic;
import dev.brp.contract.InventoryDtos.InventoryAck;
import dev.brp.contract.InventoryDtos.InventoryBatch;
import dev.brp.contract.InventoryDtos.InventoryLine;
import dev.brp.dto.StockLevelDto;
import dev.brp.mapper.InventoryMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** WMS read model: staleness allowed and labeled via capturedAt (docs/01 section 4). */
@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository snapshots;
    private final InventoryMapper mapper;

    public InventoryServiceImpl(InventoryRepository snapshots, InventoryMapper mapper) {
        this.snapshots = snapshots;
        this.mapper = mapper;
    }

    @Override
    @Atomic
    public InventoryAck upsert(String tenantId, InventoryBatch batch) {
        boolean delta = "DELTA".equalsIgnoreCase(batch.batchMode());
        if (!delta && !"SNAPSHOT".equalsIgnoreCase(batch.batchMode())) {
            throw new BadRequestException("batchMode must be SNAPSHOT or DELTA");
        }
        for (InventoryLine line : batch.lines()) {
            var row = snapshots.findByTenantIdAndContainerIdAndItemId(
                    tenantId, line.containerId(), line.itemId()).orElse(null);
            long value = delta
                    ? (row != null ? row.getAmount() : 0) + line.amount()
                    : line.amount();
            if (row == null) {
                snapshots.save(new InventorySnapshotEntity(
                        tenantId, line.containerId(), line.itemId(), value));
            } else {
                row.setAmount(value);
                row.setCapturedAt(Instant.parse(line.capturedAt()));
            }
        }
        return new InventoryAck(batch.lines().size());
    }

    @Override
    public List<StockLevelDto> list(String tenantId) {
        return mapper.toDtoList(snapshots.findByTenantIdOrderByContainerId(tenantId));
    }
}
