package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.GovernorStatusResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.entity.ManagedChunkEntity;
import dev.brp.entity.ManagedChunkEntity.ChunkStatus;
import dev.brp.mapper.ChunkMapper;
import dev.brp.repository.ManagedChunkRepository;
import dev.brp.service.ChunkService;
import dev.brp.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Chunk slots + the governor's eyes. The decision table lives here once
 * and is served read-only to the agent (docs/09 section 4). Money never
 * moves in this class — billing is P2-04's hour job.
 */
@Service
public class ChunkServiceImpl implements ChunkService {

    private final ManagedChunkRepository chunks;
    private final NotificationService notifications;
    private final ChunkMapper mapper;
    private final long graceMinutes;

    public ChunkServiceImpl(ManagedChunkRepository chunks,
                            NotificationService notifications,
                            ChunkMapper mapper,
                            @Value("${brp.chunk.governor.grace-minutes:60}") long graceMinutes) {
        this.chunks = chunks;
        this.notifications = notifications;
        this.mapper = mapper;
        this.graceMinutes = graceMinutes;
    }

    @Override
    @dev.brp.common.annotation.Atomic
    public ChunkSlotResponse register(String tenantId, RegisterChunkRequest req) {
        var existing = chunks.findByTenantIdAndDimensionAndChunkKey(
                tenantId, req.dimension(), req.chunkKey());
        if (existing.isPresent()) {
            return mapper.toDto(existing.get());   // re-registration is a no-op, as paperwork should be
        }
        return mapper.toDto(chunks.save(
                new ManagedChunkEntity(tenantId, req.dimension(), req.chunkKey(), req.registeredBy())));
    }

    @Override
    @dev.brp.common.annotation.Atomic
    public ChunkSlotResponse grantMinutes(String tenantId, String dimension,
                                          String chunkKey, GrantMinutesRequest req) {
        var slot = require(tenantId, dimension, chunkKey);
        if (req.minutes() <= 0) {
            throw new BadRequestException("chunk.grant.invalid");
        }
        slot.setCreditMinutes(slot.getCreditMinutes() + req.minutes());
        return mapper.toDto(chunks.save(slot));
    }

    @Override
    @dev.brp.common.annotation.Atomic
    public ChunkSlotResponse setPower(String tenantId, String dimension,
                                      String chunkKey, PowerRequest req) {
        var slot = require(tenantId, dimension, chunkKey);
        String want = req.enabled() ? ChunkStatus.ACTIVE : ChunkStatus.DISABLED;
        if (!want.equals(slot.getStatus())) {
            slot.setStatus(want);
            slot = chunks.save(slot);
            notifications.enqueue(req.enabled() ? "chunk.power.on" : "chunk.power.off",
                    req.enabled() ? "chunk.power.on" : "chunk.power.off",
                    chunkKey, dimension, tenantId);
        }
        return mapper.toDto(slot);
    }

    @Override
    public List<ChunkSlotResponse> slots(String tenantId) {
        return mapper.toDtoList(chunks.findByTenantId(tenantId));
    }

    @Override
    public List<GovernorStatusResponse> governor(String tenantId) {
        return chunks.findByTenantId(tenantId).stream()
                .map(slot -> new GovernorStatusResponse(
                        slot.getDimension(), slot.getChunkKey(),
                        verdictFor(slot), reasonFor(slot)))
                .toList();
    }

    // --- the decision table (docs/09 section 4), in the open ---

    private String verdictFor(ManagedChunkEntity slot) {
        if (ChunkStatus.DISABLED.equals(slot.getStatus()) || slot.getCreditMinutes() <= 0) {
            return VERDICT_HALT;
        }
        if (slot.getCreditMinutes() <= graceMinutes) {
            return VERDICT_THROTTLE;
        }
        return VERDICT_RUN;
    }

    private String reasonFor(ManagedChunkEntity slot) {
        if (ChunkStatus.DISABLED.equals(slot.getStatus())) {
            return "DISABLED_BY_ADMIN";
        }
        if (slot.getCreditMinutes() <= 0) {
            return "NO_CREDIT";
        }
        if (slot.getCreditMinutes() <= graceMinutes) {
            return "LOW_CREDIT";
        }
        return "OK";
    }

    private ManagedChunkEntity require(String tenantId, String dimension, String chunkKey) {
        return chunks.findByTenantIdAndDimensionAndChunkKey(tenantId, dimension, chunkKey)
                .orElseThrow(() -> new BadRequestException("chunk.unknown"));
    }
}
