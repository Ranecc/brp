package dev.brp.controller;

import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.GovernorStatusResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.ChunkDtos.PurchaseCreditsRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.service.ChunkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Chunk governance console: slots, credits, the remote switch, verdicts. */
@RestController
@RequestMapping("/api/v1/chunks")
public class ChunkController {

    private final ChunkService chunkService;

    public ChunkController(ChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @PostMapping("/{tenantId}")
    public ChunkSlotResponse register(@PathVariable String tenantId,
                                      @Valid @RequestBody RegisterChunkRequest req) {
        return chunkService.register(tenantId, req);
    }

    @GetMapping("/{tenantId}")
    public List<ChunkSlotResponse> slots(@PathVariable String tenantId) {
        return chunkService.slots(tenantId);
    }

    /** Agent-facing: the governor's read model (RUN/THROTTLE/HALT per chunk). */
    @GetMapping("/{tenantId}/governor")
    public List<GovernorStatusResponse> governor(@PathVariable String tenantId) {
        return chunkService.governor(tenantId);
    }

    @PutMapping("/{tenantId}/{dimension}/{chunkKey}/power")
    public ChunkSlotResponse power(@PathVariable String tenantId,
                                   @PathVariable String dimension,
                                   @PathVariable String chunkKey,
                                   @Valid @RequestBody PowerRequest req) {
        return chunkService.setPower(tenantId, dimension, chunkKey, req);
    }

    @PutMapping("/{tenantId}/{dimension}/{chunkKey}/grant")
    public ChunkSlotResponse grant(@PathVariable String tenantId,
                                   @PathVariable String dimension,
                                   @PathVariable String chunkKey,
                                   @Valid @RequestBody GrantMinutesRequest req) {
        return chunkService.grantMinutes(tenantId, dimension, chunkKey, req);
    }

    /** Money path: prepaid credits. Ledger idempotency key rides the request. */
    @PostMapping("/{tenantId}/{dimension}/{chunkKey}/purchase")
    public ChunkSlotResponse purchase(@PathVariable String tenantId,
                                      @PathVariable String dimension,
                                      @PathVariable String chunkKey,
                                      @Valid @RequestBody PurchaseCreditsRequest req) {
        return chunkService.purchaseCredits(tenantId, dimension, chunkKey, req);
    }
}
