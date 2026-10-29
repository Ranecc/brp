package dev.brp.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.entity.AnomalyHitEntity;
import dev.brp.entity.PegHistoryEntity;
import dev.brp.entity.PegStateEntity;
import dev.brp.entity.PolicyRequestEntity;
import dev.brp.repository.AnomalyHitRepository;
import dev.brp.repository.AnomalyRuleRepository;
import dev.brp.repository.PegHistoryRepository;
import dev.brp.repository.PegStateRepository;
import dev.brp.repository.PolicyRequestRepository;
import dev.brp.service.NotificationService;
import dev.brp.service.PegService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Re-peg without a third governance channel (docs/03 §9): the machine
 * files the vote, the OWNER ladder decides, the execution is atomic with
 * the request id stamped into history. v0 console note: the decide()
 * role check rides the portal RBAC once it lands — the standing
 * no-auth-internal-only posture covers it for now.
 */
@Service
public class PegServiceImpl implements PegService {

    private final PegStateRepository legs;
    private final PegHistoryRepository history;
    private final PolicyRequestRepository requests;
    private final AnomalyHitRepository hits;
    private final AnomalyRuleRepository rules;
    private final NotificationService notifications;
    private final ObjectMapper mapper;
    private final double bandPct;

    public PegServiceImpl(PegStateRepository legs, PegHistoryRepository history,
                          PolicyRequestRepository requests,
                          AnomalyHitRepository hits, AnomalyRuleRepository rules,
                          NotificationService notifications, ObjectMapper mapper,
                          @Value("${brp.peg.band:0.05}") double bandPct) {
        this.legs = legs;
        this.history = history;
        this.requests = requests;
        this.hits = hits;
        this.rules = rules;
        this.notifications = notifications;
        this.mapper = mapper;
        this.bandPct = bandPct;
    }

    @Override
    public List<PegStateEntity> legs() {
        return legs.findAll();
    }

    @Override
    public void observe(String leg, double observedRate) {
        PegStateEntity state = legs.findById(leg)
                .orElseThrow(() -> new BadRequestException("monetary.peg.leg.unknown"));
        if (!state.outOfBand(observedRate)) {
            state.streak(0);
            legs.save(state);
            return;
        }
        int streak = state.getOutOfBandStreak() + 1;
        state.streak(streak);
        legs.save(state);
        if (streak >= 2 && requests.findByStatusOrderByIdAsc(PolicyRequestEntity.PENDING).stream()
                .noneMatch(r -> r.getKind().equals(PolicyRequestEntity.REPEG_VOTE)
                        && r.getPayloadJson().contains("\"" + leg + "\""))) {
            fileVote(state, observedRate);
        }
    }

    private void fileVote(PegStateEntity state, double observed) {
        try {
            requests.save(new PolicyRequestEntity(PolicyRequestEntity.REPEG_VOTE,
                    mapper.writeValueAsString(Map.of(
                            "leg", state.getLeg(),
                            "currentRate", state.getRate(),
                            "proposedRate", observed)), "machine"));
            notifications.enqueue("monetary.peg.repeg.filed", "monetary.peg.repeg.filed",
                    state.getLeg(), state.getRate(), observed);
        } catch (com.fasterxml.jackson.core.JsonProcessingException neverHappens) {
            throw new IllegalStateException(neverHappens);
        }
    }

    @Override
    public void executeRepeg(long requestId, String decidedBy) {
        PolicyRequestEntity request = requests.findById(requestId)
                .orElseThrow(() -> new BadRequestException("monetary.request.unknown"));
        if (!PolicyRequestEntity.APPROVED.equals(request.getStatus())) {
            throw new ConflictException("monetary.request.not.approved");
        }
        try {
            var payload = mapper.readValue(request.getPayloadJson(), Map.class);
            String legName = String.valueOf(payload.get("leg"));
            double proposed = ((Number) payload.get("proposedRate")).doubleValue();
            PegStateEntity state = legs.findById(legName)
                    .orElseThrow(() -> new BadRequestException("monetary.peg.leg.unknown"));
            double old = state.getRate();
            state.repeg(proposed, bandPct);
            legs.save(state);
            history.save(new PegHistoryEntity(legName, old, proposed, requestId));
        } catch (java.io.IOException bad) {
            throw new BadRequestException("monetary.request.payload.bad");
        }
    }

    @Override
    public void decide(long requestId, boolean approved, String decidedBy) {
        PolicyRequestEntity request = requests.findById(requestId)
                .orElseThrow(() -> new BadRequestException("monetary.request.unknown"));
        if (!PolicyRequestEntity.PENDING.equals(request.getStatus())) {
            throw new ConflictException("monetary.request.closed");
        }
        request.decide(approved ? PolicyRequestEntity.APPROVED : PolicyRequestEntity.REJECTED,
                decidedBy);
        requests.save(request);
        if (approved && PolicyRequestEntity.REPEG_VOTE.equals(request.getKind())) {
            executeRepeg(requestId, decidedBy);
        }
    }

    /** Shared by drift-type alerts (vault 0904 etc.). */
    void file(long code, double observed) {
        var rule = rules.findById(code).orElse(null);
        hits.save(new AnomalyHitEntity(code, rule != null ? rule.getName() : "BRP-" + code,
                "policy", observed, rule != null ? rule.getSeverity() : "CRIT"));
    }
}
