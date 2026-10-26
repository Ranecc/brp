package dev.brp.controller;

import dev.brp.dto.QuotaDtos.ChunkIncidentResponse;
import dev.brp.dto.QuotaDtos.QuotaBucketResponse;
import dev.brp.dto.QuotaDtos.SetQuotaRequest;
import dev.brp.service.QuotaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Compute-quota console: license machines, review chunk gluttony. */
@RestController
@RequestMapping("/api/v1/quotas")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @PutMapping("/{tenantId}/{resource}")
    public QuotaBucketResponse setLimit(@PathVariable String tenantId,
                                        @PathVariable String resource,
                                        @Valid @RequestBody SetQuotaRequest req) {
        return quotaService.setLimit(tenantId, resource, req.limit());
    }

    @GetMapping("/{tenantId}")
    public List<QuotaBucketResponse> usage(@PathVariable String tenantId) {
        return quotaService.usage(tenantId);
    }

    @GetMapping("/incidents")
    public List<ChunkIncidentResponse> incidents(@RequestParam(required = false) String tenantId) {
        return quotaService.incidents(tenantId);
    }
}
