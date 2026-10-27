package dev.brp.controller;

import dev.brp.service.ApsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** APS fair-share console (docs/09 section 5): the haircut schedule. */
@RestController
@RequestMapping("/api/v1/aps")
public class ApsController {

    private final ApsService aps;

    public ApsController(ApsService aps) {
        this.aps = aps;
    }

    @GetMapping("/allocations")
    public Map<String, Double> allocations() {
        return aps.allocations();
    }

    @GetMapping("/allocations/{tenantId}")
    public Map<String, Double> factor(@PathVariable String tenantId) {
        return Map.of("factor", aps.factorFor(tenantId));
    }
}
