package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Idempotent;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ApprovalDtos.Decision;
import dev.brp.dto.ApprovalDtos.NudgeAck;
import dev.brp.dto.ApprovalDtos.SubmitWithdrawalRequest;
import dev.brp.dto.ApprovalDtos.WithdrawalRequestResponse;
import dev.brp.entity.ApprovalActionRow;
import dev.brp.entity.TeamMemberRow;
import dev.brp.entity.WithdrawalRequestEntity;
import dev.brp.mapper.WithdrawalRequestMapper;
import dev.brp.repository.ApprovalActionRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.repository.WithdrawalRequestRepository;
import dev.brp.saga.SagaEngine;
import dev.brp.service.ApprovalService;
import dev.brp.service.NotificationService;
import dev.brp.service.LedgerService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The approval labyrinth. Small requests get the rubber stamp (autocatalytic
 * bureaucracy); large ones must be escalated to UPPER MANAGEMENT; everything
 * executes through the vault-withdraw saga so a failure at any desk refunds
 * everyone's patience. Money movement itself stays boring and idempotent
 * (deterministic keys derived from requestId) — the memes are for humans,
 * the invariants are for the database.
 */
@Service
public class ApprovalServiceImpl implements ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalServiceImpl.class);

    private final WithdrawalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final TeamMemberRepository members;
    private final LedgerService ledger;
    private final SagaEngine saga;
    private final NotificationService notifications;
    private final WithdrawalRequestMapper mapper;
    private final long rubberStampCents;
    private final long upperManagementCents;

    public ApprovalServiceImpl(WithdrawalRequestRepository requests,
                               ApprovalActionRepository actions,
                               TeamMemberRepository members,
                               LedgerService ledger,
                               SagaEngine saga,
                               NotificationService notifications,
                               WithdrawalRequestMapper mapper,
                               @Value("${brp.approval.rubber-stamp-cents:1000}") long rubberStampCents,
                               @Value("${brp.approval.upper-management-cents:10000}") long upperManagementCents) {
        this.requests = requests;
        this.actions = actions;
        this.members = members;
        this.ledger = ledger;
        this.saga = saga;
        this.notifications = notifications;
        this.mapper = mapper;
        this.rubberStampCents = rubberStampCents;
        this.upperManagementCents = upperManagementCents;
    }

    @PostConstruct
    void registerSagas() {
        SagaEngine.Builder<Map<String, Object>> vaultWithdraw = SagaEngine.saga("vault-withdraw");
        // money-path steps: boring names, deterministic idempotency
        saga.register(vaultWithdraw
                .step("freeze", p -> postJournal(p, freezeJournal(p, false)),
                        "unfreeze", p -> postJournal(p, freezeJournal(p, true)))
                .step("dispense", p -> {
                    log.debug("dispense: P2 wires the ME bridge here (request {})",
                            p.get("requestId"));
                    return null;
                }, "restock", p -> {
                    log.debug("restock stub (request {})", p.get("requestId"));
                    return null;
                })
                .step("settle", p -> postJournal(p, settleJournal(p, false)),
                        "refund", p -> postJournal(p, settleJournal(p, true)))
                .build());
    }

    @Override
    @Idempotent(key = "#req.idempotencyKey()")
    public WithdrawalRequestResponse submitWithdrawalRequest(SubmitWithdrawalRequest req) {
        requireMember(req.teamId(), req.requesterUuid());
        var entity = new WithdrawalRequestEntity(UUID.randomUUID().toString(),
                req.teamId(), req.requesterUuid(), req.amountCents(), req.memo());
        requests.save(entity);
        actions.save(new ApprovalActionRow(entity.getId(), req.requesterUuid(),
                "SUBMITTED", req.memo()));

        if (req.amountCents() <= rubberStampCents) {
            // The rubber stamp: bureaucracy so proud of its efficiency it
            // skips itself. Small amounts are auto-approved, memo filed, KPIs met.
            actions.save(new ApprovalActionRow(entity.getId(), "system", "RUBBER_STAMPED",
                    "amount within rubber-stamp threshold"));
            return executeApproved(entity);
        }
        if (req.amountCents() >= upperManagementCents) {
            entity.transitionTo("AWAITING_UPPER_MANAGEMENT");
            requests.save(entity);
        }
        return mapper.toDto(entity);
    }

    @Override
    @Atomic
    public WithdrawalRequestResponse approve(String requestId, Decision decision) {
        var entity = requests.findById(requestId)
                .orElseThrow(() -> BadRequestException.of("approval.unknown"));
        String status = entity.getStatus();
        if (!"PENDING".equals(status) && !"AWAITING_UPPER_MANAGEMENT".equals(status)) {
            throw ConflictException.of("approval.request.closed", status);
        }
        if ("AWAITING_UPPER_MANAGEMENT".equals(status)) {
            requireRole(entity.getTeamId(), decision.approverUuid(), "OWNER",
                    "approval.upper.management.required");
        } else {
            requireRole(entity.getTeamId(), decision.approverUuid(), "OFFICER",
                    "approval.officer.required");
        }
        actions.save(new ApprovalActionRow(requestId, decision.approverUuid(),
                "APPROVED", decision.note()));
        return executeApproved(entity);
    }

    @Override
    @Atomic
    public WithdrawalRequestResponse reject(String requestId, Decision decision) {
        var entity = requests.findById(requestId)
                .orElseThrow(() -> BadRequestException.of("approval.unknown"));
        // Same guard approve already had (audit #5): settled requests stay
        // settled — an EXECUTED withdrawal cannot be un-executed by a verdict.
        if (!"PENDING".equals(entity.getStatus())
                && !"AWAITING_UPPER_MANAGEMENT".equals(entity.getStatus())) {
            throw ConflictException.of("approval.request.closed", entity.getStatus());
        }
        requireRole(entity.getTeamId(), decision.approverUuid(), "OFFICER",
                "approval.officer.required");
        entity.transitionTo("REJECTED");
        requests.save(entity);
        actions.save(new ApprovalActionRow(requestId, decision.approverUuid(),
                "REJECTED", decision.note()));
        return mapper.toDto(entity);
    }

    @Override
    public NudgeAck perMyLastEmail(String requestId) {
        var entity = requests.findById(requestId)
                .orElseThrow(() -> BadRequestException.of("approval.unknown"));
        if (!"PENDING".equals(entity.getStatus())
                && !"AWAITING_UPPER_MANAGEMENT".equals(entity.getStatus())) {
            throw ConflictException.of("approval.nudge.settled");
        }
        // P1 wires Discord/webhook here; today the reminder echoes into the void
        log.info("per my last email: request {} awaits your timely attention", requestId);
        notifications.enqueue("approval.nudged", "approval.nudge.ok");
        actions.save(new ApprovalActionRow(requestId, entity.getRequesterUuid(),
                "NUDGED", "per my last email"));
        String[] buzzwords = {"synergy", "circling back", "quick sync", "bandwidth",
                "taking this offline", "let's parking-lot it"};
        return new NudgeAck(requestId, 1,
                buzzwords[ThreadLocalRandom.current().nextInt(buzzwords.length)]);
    }

    @Override
    public List<WithdrawalRequestResponse> inbox(String teamId, String status) {
        String s = status != null ? status : "PENDING";
        return mapper.toDtoList(requests.findByTeamIdAndStatusOrderById(teamId, s));
    }

    @Override
    public List<WithdrawalRequestResponse> latestPending() {
        return mapper.toDtoList(requests.findTop20ByStatusOrderByIdDesc("PENDING"));
    }

    // --- internals ---

    private WithdrawalRequestResponse executeApproved(WithdrawalRequestEntity entity) {
        ensureAccounts(entity.getTeamId());
        var outcome = saga.run("vault-withdraw", entity.getId(),
                Map.of("teamId", entity.getTeamId(),
                       "amountCents", entity.getAmountCents(),
                       "requestId", entity.getId()));
        entity.transitionTo(outcome.success() ? "EXECUTED" : "FAILED");
        requests.save(entity);
        if (!outcome.success()) {
            log.warn("vault-withdraw failed for request {} (compensated: {}) — the org chart apologizes",
                    entity.getId(), outcome.compensated());
        }
        return mapper.toDto(entity);
    }

    private void ensureAccounts(String teamId) {
        try {
            ledger.createAccount(teamId, new CreateAccountRequest("escrow", "ESCROW", "BRC"));
        } catch (ConflictException alreadyThere) {
            // idempotent provisioning
        }
        try {
            ledger.createAccount(teamId, new CreateAccountRequest("expense-sink", "SYSTEM_SINK", "BRC"));
        } catch (ConflictException alreadyThere) {
            // ditto
        }
    }

    private Object postJournal(Map<String, Object> p, PostJournalRequest journal) {
        ledger.post((String) p.get("teamId"), journal);
        return null;
    }    private PostJournalRequest freezeJournal(Map<String, Object> p, boolean reverse) {
        long amount = ((Number) p.get("amountCents")).longValue();
        String requestId = (String) p.get("requestId");
        List<JournalLine> lines = reverse
                ? List.of(new JournalLine("treasury", "DEBIT", amount, null),
                          new JournalLine("escrow", "CREDIT", amount, null))
                : List.of(new JournalLine("escrow", "DEBIT", amount, null),
                          new JournalLine("treasury", "CREDIT", amount, null));
        return new PostJournalRequest("wd:" + requestId + ":freeze", "BRC", lines);
    }

    private PostJournalRequest settleJournal(Map<String, Object> p, boolean reverse) {
        long amount = ((Number) p.get("amountCents")).longValue();
        String requestId = (String) p.get("requestId");
        List<JournalLine> lines = reverse
                ? List.of(new JournalLine("escrow", "DEBIT", amount, null),
                          new JournalLine("expense-sink", "CREDIT", amount, null))
                : List.of(new JournalLine("expense-sink", "DEBIT", amount, null),
                          new JournalLine("escrow", "CREDIT", amount, null));
        return new PostJournalRequest("wd:" + requestId + ":settle", "BRC", lines);
    }

    private void requireMember(String teamId, String uuid) {
        if (members.findByTeamIdAndPlayerUuid(teamId, uuid).isEmpty()) {
            throw BadRequestException.of("approval.hr.rejected");
        }
    }

    private void requireRole(String teamId, String uuid, String atLeast, String messageKey) {
        TeamMemberRow member = members.findByTeamIdAndPlayerUuid(teamId, uuid)
                .orElseThrow(() -> BadRequestException.of(messageKey));
        String role = member.getRole();
        boolean ok = switch (atLeast) {
            case "OWNER" -> "OWNER".equals(role);
            default -> "OWNER".equals(role) || "OFFICER".equals(role);
        };
        if (!ok) {
            throw ConflictException.of(messageKey);
        }
    }
}
