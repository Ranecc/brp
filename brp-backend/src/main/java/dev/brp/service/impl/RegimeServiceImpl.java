package dev.brp.service.impl;

import dev.brp.dto.MonetaryDtos.RegimeResponse;
import dev.brp.entity.AnomalyHitEntity;
import dev.brp.entity.PolicyRegimeEntity;
import dev.brp.repository.AnomalyHitRepository;
import dev.brp.repository.AnomalyRuleRepository;
import dev.brp.repository.PolicyRegimeRepository;
import dev.brp.service.NotificationService;
import dev.brp.service.RegimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Hysteresis, half-open breaker heritage (docs/03 §9, docs/12 §4): the
 * band between the thresholds holds state. Transitions file their own
 * alert; the red line (sustained >50% share) is a separate, louder thing.
 */
@Service
public class RegimeServiceImpl implements RegimeService {

    private final PolicyRegimeRepository regimes;
    private final AnomalyHitRepository hits;
    private final AnomalyRuleRepository rules;
    private final NotificationService notifications;
    private final double on;
    private final double off;
    private final double redlineShare;

    public RegimeServiceImpl(PolicyRegimeRepository regimes,
                             AnomalyHitRepository hits,
                             AnomalyRuleRepository rules,
                             NotificationService notifications,
                             @Value("${brp.regime.gini.on:0.8}") double on,
                             @Value("${brp.regime.gini.off:0.7}") double off,
                             @Value("${brp.regime.redline-share:0.5}") double redlineShare) {
        this.regimes = regimes;
        this.hits = hits;
        this.rules = rules;
        this.notifications = notifications;
        this.on = on;
        this.off = off;
        this.redlineShare = redlineShare;
    }

    @Override
    public String afterSnapshot(double gini, double topShare) {
        PolicyRegimeEntity regime = regimes.findById(PolicyRegimeEntity.ANTITRUST)
                .orElseGet(() -> regimes.save(new PolicyRegimeEntity(
                        PolicyRegimeEntity.ANTITRUST, PolicyRegimeEntity.INACTIVE, 0)));
        String before = regime.getState();
        if (gini >= on) {
            if (!PolicyRegimeEntity.ACTIVE.equals(before)) {
                regime.transitionTo(PolicyRegimeEntity.ACTIVE, gini);
                file(901L, "gini", gini, "monetary.regime.active");
            } else {
                regime.reading(gini);
            }
        } else if (gini < off) {
            if (!PolicyRegimeEntity.INACTIVE.equals(before)) {
                regime.transitionTo(PolicyRegimeEntity.INACTIVE, gini);
                file(902L, "gini", gini, "monetary.regime.inactive");
            } else {
                regime.reading(gini);
            }
        } else {
            regime.reading(gini);   // the band: hold your state, keep your nerve
        }
        regimes.save(regime);

        // The red line is not a tax rate (docs/03 §9): it pages humans.
        if (topShare > redlineShare) {
            file(950L, "top_share", topShare, "monetary.regime.redline");
        }
        return regime.getState();
    }

    @Override
    public boolean active() {
        return regimes.findById(PolicyRegimeEntity.ANTITRUST)
                .map(r -> PolicyRegimeEntity.ACTIVE.equals(r.getState()))
                .orElse(false);
    }

    @Override
    public RegimeResponse regime() {
        var r = regimes.findById(PolicyRegimeEntity.ANTITRUST).orElse(null);
        return r == null ? new RegimeResponse("antitrust", "INACTIVE", 0.0, null)
                : new RegimeResponse(r.getName(), r.getState(), r.getLastReading(),
                        String.valueOf(r.getEnteredAt()));
    }

    private void file(long code, String field, double observed, String copyKey) {
        var rule = rules.findById(code).orElse(null);
        hits.save(new AnomalyHitEntity(code,
                rule != null ? rule.getName() : "BRP-" + code,
                "policy", observed, rule != null ? rule.getSeverity() : "WARN"));
        notifications.enqueue(copyKey, copyKey, observed);
    }
}
