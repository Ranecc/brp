package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.common.annotation.Cached;
import dev.brp.dto.AnomalyDtos.AnomalyHitResponse;
import dev.brp.dto.AnomalyDtos.AnomalyRuleResponse;
import dev.brp.dto.AnomalyDtos.SetRuleRequest;
import dev.brp.entity.AnomalyHitEntity;
import dev.brp.entity.AnomalyRuleEntity;
import dev.brp.repository.AnomalyHitRepository;
import dev.brp.repository.AnomalyRuleRepository;
import dev.brp.service.HotRuleService;
import dev.brp.service.NotificationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * L1 hot rules on the fanOut hook (docs/10 R-2). Cheap by design: one
 * numeric comparison per rule per event — models and sidecars are L3's
 * problem, not ours.
 */
@Service
public class HotRuleServiceImpl implements HotRuleService {

    private final AnomalyRuleRepository rules;
    private final AnomalyHitRepository hits;
    private final NotificationService notifications;

    public HotRuleServiceImpl(AnomalyRuleRepository rules, AnomalyHitRepository hits,
                              NotificationService notifications) {
        this.rules = rules;
        this.hits = hits;
        this.notifications = notifications;
    }

    @Override
    public List<AnomalyRuleResponse> rules() {
        return rules.findAll().stream().map(dev.brp.dto.AnomalyDtos::toRuleDto).toList();
    }

    @Override
    @dev.brp.common.annotation.Atomic
    public AnomalyRuleResponse create(SetRuleRequest req) {
        AnomalyRuleEntity.Op op;
        try {
            op = AnomalyRuleEntity.Op.valueOf(req.op().toUpperCase());
        } catch (IllegalArgumentException badOp) {
            throw new BadRequestException("anomaly.rule.op.invalid");
        }
        String severity = req.severity() == null || req.severity().isBlank() ? "WARN" : req.severity();
        return dev.brp.dto.AnomalyDtos.toRuleDto(rules.save(
                new AnomalyRuleEntity(req.name(), req.eventType(), req.field(), op,
                        req.threshold(), severity)));
    }

    @Override
    public void evaluate(String eventType, Map<?, ?> payload) {
        for (AnomalyRuleEntity rule : activeRules()) {
            if (!rule.getEventType().equals(eventType)) {
                continue;
            }
            Object raw = payload.get(rule.getField());
            if (!(raw instanceof Number observed)) {
                continue;   // a rule on a missing field waits politely, it does not guess
            }
            if (rule.trips(observed.doubleValue())) {
                hits.save(new AnomalyHitEntity(rule.getId(), rule.getName(),
                        eventType, observed.doubleValue(), rule.getSeverity()));
                notifications.enqueue("anomaly.rule.hit", "anomaly.rule.hit",
                        rule.getName(), eventType, observed, rule.getSeverity());
            }
        }
    }

    @Override
    public List<AnomalyHitResponse> hits() {
        return hits.findTop50ByOrderByIdDesc().stream()
                .map(h -> new AnomalyHitResponse(h.getId(), h.getRuleName(),
                        h.getEventType(), h.getObserved(), h.getSeverity(),
                        String.valueOf(h.getCreatedAt())))
                .toList();
    }

    @Override
    @Cached(key = "'anomaly:active'", ttlSeconds = 30)
    public List<AnomalyRuleEntity> activeRules() {
        return rules.findByStatusOrderByIdAsc("ACTIVE");
    }
}
