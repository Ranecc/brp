package dev.brp.controller;

import dev.brp.common.BadRequestException;
import dev.brp.repository.CreditLineRepository;
import dev.brp.service.impl.QuotaMarketServiceImpl;
import dev.brp.repository.DeferredJobRepository;
import dev.brp.repository.QuotaBucketRepository;
import dev.brp.service.CreditService;
import dev.brp.service.LoadSampleService;
import dev.brp.service.QuotaMarketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The CC:T desk (docs/13 §10): one read-only poll for a computer, no history,
 * no side effects. Scoped bearer token (brp.cc.token); empty token disables
 * the desk entirely — a computer on the loose reads nothing.
 */
@RestController
@RequestMapping("/api/v1/cc")
public class CcController {

    private final QuotaBucketRepository buckets;
    private final DeferredJobRepository jobs;
    private final CreditLineRepository creditLines;
    private final LoadSampleService loadSamples;
    private final Clock clock;
    private final String token;

    public CcController(QuotaBucketRepository buckets,
                        DeferredJobRepository jobs,
                        CreditLineRepository creditLines,
                        LoadSampleService loadSamples,
                        Clock clock,
                        @Value("${brp.cc.token:}") String token) {
        this.buckets = buckets;
        this.jobs = jobs;
        this.creditLines = creditLines;
        this.loadSamples = loadSamples;
        this.clock = clock;
        this.token = token;
    }

    @GetMapping("/{tenant}/state")
    public Map<String, Object> state(@PathVariable String tenant,
                                     @org.springframework.web.bind.annotation.RequestHeader(
                                             value = "Authorization", required = false) String auth) {
        requireToken(auth);
        long quotaLimit = buckets.findById(new dev.brp.entity.QuotaBucketEntity.Pk(
                        tenant, QuotaMarketServiceImpl.RESOURCE))
                .map(b -> b.getLimitAmount()).orElse(0L);
        List<Map<String, Object>> windows = jobs
                .findByTenantIdOrderByCreatedAtAsc(tenant).stream()
                .filter(j -> dev.brp.entity.DeferredJobEntity.AWARDED.equals(j.getStatus())
                        || dev.brp.entity.DeferredJobEntity.RUNNING.equals(j.getStatus()))
                .map(j -> Map.<String, Object>of(
                        "jobId", String.valueOf(j.getId()),
                        "status", j.getStatus(),
                        "start", String.valueOf(j.getAwardedStart()),
                        "minutes", j.getAwardedMinutes()))
                .toList();
        var forecast = loadSamples.forecast(1);
        var hour = forecast.hours().isEmpty() ? null : forecast.hours().get(0);
        // READ-ONLY desk: the credit line is read, never provisioned here
        String creditStatus = creditLines.findById(tenant)
                .map(dev.brp.entity.CreditLineEntity::getStatus).orElse("GOOD");
        return Map.of(
                "tenant", tenant,
                "quotaLimit", quotaLimit,
                "msptPredicted", hour == null ? 0.0 : hour.predictedMspt(),
                "schedulable", hour != null && hour.schedulable(),
                "tariffLabel", hour == null ? "SHOULDER" : hour.tariffLabel(),
                "multiplierPct", hour == null ? 100 : hour.multiplierPct(),
                "creditStatus", creditStatus,
                "windows", windows,
                "generatedAt", LocalDateTime.now(clock).toString());
    }

    private void requireToken(String auth) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("cc.disabled");
        }
        String expected = "Bearer " + token;
        if (auth == null || !constantTimeEquals(expected, auth)) {
            throw new BadRequestException("cc.unauthorized");
        }
    }

    /** Token comparison that does not leak its length through early exit. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes();
        byte[] y = b.getBytes();
        int diff = x.length ^ y.length;
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }
}
