package dev.brp.service.impl;

import dev.brp.entity.ManagedChunkEntity;
import dev.brp.repository.ManagedChunkRepository;
import dev.brp.service.ApsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Water-fill, v1 (docs/09 section 5): claims are each tenant's chunk
 * credit-minutes; capacity is the global quota-minute budget. Over budget,
 * everyone takes the same haircut — the math is one division and it is
 * fair by construction. Smarter water-filling earns its complexity later.
 */
@Service
public class ApsServiceImpl implements ApsService {

    private final ManagedChunkRepository chunks;
    private final long capacityQuotaMinutes;

    public ApsServiceImpl(ManagedChunkRepository chunks,
                          @Value("${brp.aps.capacity-quota-minutes:0}") long capacityQuotaMinutes) {
        this.chunks = chunks;
        this.capacityQuotaMinutes = capacityQuotaMinutes;
    }

    @Override
    public Map<String, Double> allocations() {
        Map<String, Long> claims = chunks.findAll().stream()
                .collect(Collectors.groupingBy(ManagedChunkEntity::getTenantId,
                        Collectors.summingLong(c -> Math.max(0, c.getCreditMinutes()))));
        Map<String, Double> out = new TreeMap<>();
        long total = claims.values().stream().mapToLong(Long::longValue).sum();
        double factor = unconstrained(capacityQuotaMinutes) || total == 0
                ? 1.0
                : Math.min(1.0, (double) capacityQuotaMinutes / total);
        for (var entry : claims.entrySet()) {
            out.put(entry.getKey(), factor);
        }
        return out;
    }

    @Override
    public double factorFor(String tenantId) {
        return allocations().getOrDefault(tenantId, 1.0);
    }

    private boolean unconstrained(long capacity) {
        return capacity <= 0;   // 0 = the server has not decided to be scarce yet
    }
}
