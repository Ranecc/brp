package dev.brp.inventory;

import dev.brp.common.BadRequestException;
import dev.brp.contract.InventoryDtos.InventoryAck;
import dev.brp.contract.InventoryDtos.InventoryBatch;
import dev.brp.contract.InventoryDtos.InventoryLine;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** WMS read model: staleness is allowed and visible via capturedAt (docs/01 section 4). */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inventory")
public class InventoryController {

    private final InventoryRepository snapshots;

    public InventoryController(InventoryRepository snapshots) {
        this.snapshots = snapshots;
    }

    @PostMapping("/snapshots")
    @Transactional
    public InventoryAck upsert(@PathVariable String tenantId,
                               @Valid @RequestBody InventoryBatch batch) {
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

    @GetMapping
    public List<InventorySnapshotEntity> list(@PathVariable String tenantId) {
        return snapshots.findByTenantIdOrderByContainerId(tenantId);
    }
}
