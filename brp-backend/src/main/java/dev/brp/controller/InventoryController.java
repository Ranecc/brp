package dev.brp.controller;
import dev.brp.service.*;

import dev.brp.contract.InventoryDtos.InventoryAck;
import dev.brp.contract.InventoryDtos.InventoryBatch;
import dev.brp.dto.StockLevelDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Thin transport layer for the WMS domain. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/snapshots")
    public InventoryAck upsert(@PathVariable String tenantId,
                               @Valid @RequestBody InventoryBatch batch) {
        return inventoryService.upsert(tenantId, batch);
    }

    @GetMapping
    public List<StockLevelDto> list(@PathVariable String tenantId) {
        return inventoryService.list(tenantId);
    }
}
