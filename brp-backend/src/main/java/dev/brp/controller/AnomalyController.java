package dev.brp.controller;

import dev.brp.dto.AnomalyDtos.AnomalyHitResponse;
import dev.brp.dto.AnomalyDtos.AnomalyRuleResponse;
import dev.brp.dto.AnomalyDtos.SetRuleRequest;
import dev.brp.service.HotRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** L1 hot-rule desk (docs/10 R-2): rules are data, hits are paperwork. */
@RestController
@RequestMapping("/api/v1/anomaly")
public class AnomalyController {

    private final HotRuleService hotRules;

    public AnomalyController(HotRuleService hotRules) {
        this.hotRules = hotRules;
    }

    @GetMapping("/rules")
    public List<AnomalyRuleResponse> rules() {
        return hotRules.rules();
    }

    @PostMapping("/rules")
    public AnomalyRuleResponse create(@Valid @RequestBody SetRuleRequest req) {
        return hotRules.create(req);
    }

    @GetMapping("/hits")
    public List<AnomalyHitResponse> hits() {
        return hotRules.hits();
    }
}
