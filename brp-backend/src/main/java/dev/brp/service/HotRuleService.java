package dev.brp.service;

import dev.brp.dto.AnomalyDtos.AnomalyHitResponse;
import dev.brp.dto.AnomalyDtos.AnomalyRuleResponse;
import dev.brp.dto.AnomalyDtos.SetRuleRequest;
import dev.brp.entity.AnomalyRuleEntity;

import java.util.List;
import java.util.Map;

/**
 * L1 hot rules (docs/05 L1, docs/10 R-2): numeric thresholds on telemetry
 * payloads, evaluated as events arrive on the fanOut hook. Rules are data,
 * not code — the anomaly desk does not redeploy the server.
 */
public interface HotRuleService {

    List<AnomalyRuleResponse> rules();

    AnomalyRuleResponse create(SetRuleRequest req);

    /** Evaluate the active rules against one payload; hits persist + notify. */
    void evaluate(String eventType, Map<?, ?> payload);

    List<AnomalyHitResponse> hits();

    /** exposed for telemetry batch scope: fetch once, evaluate per event */
    List<AnomalyRuleEntity> activeRules();
}
