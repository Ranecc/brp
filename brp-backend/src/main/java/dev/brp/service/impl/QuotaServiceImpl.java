package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.common.annotation.Atomic;
import dev.brp.dto.QuotaDtos.ChunkIncidentResponse;
import dev.brp.dto.QuotaDtos.PlacementVerdict;
import dev.brp.dto.QuotaDtos.QuotaBucketResponse;
import dev.brp.entity.ChunkIncidentEntity;
import dev.brp.entity.QuotaBucketEntity;
import dev.brp.mapper.QuotaMapper;
import dev.brp.repository.ChunkIncidentRepository;
import dev.brp.repository.QuotaBucketRepository;
import dev.brp.service.NotificationService;
import dev.brp.service.QuotaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Machine placement licensing + chunk gluttony detection. Policy posture
 * (owner's decision): violations NOTIFY admins — enforcement (kick, fine,
 * therapy) stays human. limit 0 = unlimited licensing.
 */
@Service
public class QuotaServiceImpl implements QuotaService {

    private final QuotaBucketRepository buckets;
    private final ChunkIncidentRepository incidents;
    private final NotificationService notifications;
    private final QuotaMapper mapper;
    private final double chunkCostThreshold;

    public QuotaServiceImpl(QuotaBucketRepository buckets,
                            ChunkIncidentRepository incidents,
                            NotificationService notifications,
                            QuotaMapper mapper,
                            @Value("${brp.quota.chunk-cost-threshold:100}") double chunkCostThreshold) {
        this.buckets = buckets;
        this.incidents = incidents;
        this.notifications = notifications;
        this.mapper = mapper;
        this.chunkCostThreshold = chunkCostThreshold;
    }

    @Override
    @Atomic
    public QuotaBucketResponse setLimit(String tenantId, String resource, long limit) {
        // audit #6: the API DTO has @PositiveOrZero, but the portal form
        // posts a raw long — a negative limit meant UNLIMITED. Refuse here
        // so every door is closed, not just the polite one.
        if (limit < 0) {
            throw new BadRequestException("quota.limit.negative");
        }
        var bucket = buckets.findById(new QuotaBucketEntity.Pk(tenantId, resource))
                .orElseGet(() -> new QuotaBucketEntity(tenantId, resource, limit));
        bucket.setLimitAmount(limit);
        return mapper.toDto(buckets.save(bucket));
    }

    @Override
    public List<QuotaBucketResponse> usage(String tenantId) {
        return mapper.toDtoList(buckets.findByTenantId(tenantId));
    }

    @Override
    public List<QuotaBucketResponse> all() {
        return mapper.toDtoList(buckets.findAll());
    }

    @Override
    @Atomic
    public PlacementVerdict recordMachinePlaced(String tenantId, String machineId) {
        // audit #6: read-check-write raced under concurrency; the row lock
        // serializes placement verdicts the same way the chunk meter does.
        var bucket = buckets.findForUpdate(tenantId, QuotaService.RESOURCE_MACHINE_SLOT)
                .orElseGet(() -> new QuotaBucketEntity(tenantId, QuotaService.RESOURCE_MACHINE_SLOT, 0));
        if (!bucket.tryConsumeOne()) {
            // Over-licensed placement: page management, do not punish. The
            // admin decides what "too many machines" means socially.
            notifications.enqueue("quota.machine.exceeded",
                    "quota.machine.exceeded", tenantId);
            return new PlacementVerdict(false, QuotaService.RESOURCE_MACHINE_SLOT);
        }
        buckets.save(bucket);
        return new PlacementVerdict(true, QuotaService.RESOURCE_MACHINE_SLOT);
    }

    @Override
    @Atomic
    public void recordChunkPerformance(String tenantId, String dimension,
                                       String chunk, double costScore) {
        if (costScore < chunkCostThreshold) {
            return;
        }
        incidents.save(new ChunkIncidentEntity(tenantId, dimension, chunk, costScore));
        notifications.enqueue("quota.chunk.incident", "quota.chunk.incident",
                chunk, dimension == null ? "?" : dimension, costScore);
    }

    @Override
    public List<ChunkIncidentResponse> incidents(String tenantId) {
        return mapper.toIncidentDtoList(incidents.findAll().stream()
                .filter(i -> tenantId == null || tenantId.equals(i.getTenantId()))
                .toList());
    }
}
