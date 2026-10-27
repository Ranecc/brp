package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.common.annotation.Atomic;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.ChunkDtos.ChunkAdminRow;
import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.GovernorStatusResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.ChunkDtos.PurchaseCreditsRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.entity.ChunkPurchaseKeyEntity;
import dev.brp.entity.ManagedChunkEntity;
import dev.brp.entity.ManagedChunkEntity.ChunkStatus;
import dev.brp.mapper.ChunkMapper;
import dev.brp.repository.ChunkPurchaseKeyRepository;
import dev.brp.repository.ManagedChunkRepository;
import dev.brp.service.ChunkService;
import dev.brp.service.LedgerService;
import dev.brp.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Chunk slots + the governor's eyes + the prepaid meter. The decision
 * table lives here once and is served read-only to the agent (docs/09
 * section 4). Money moves ONLY in purchaseCredits, through the ledger's
 * same-transaction idempotency — the one door money is allowed through.
 */
@Service
public class ChunkServiceImpl implements ChunkService {

    private final ManagedChunkRepository chunks;
    private final ChunkPurchaseKeyRepository purchaseKeys;
    private final NotificationService notifications;
    private final LedgerService ledger;
    private final ChunkMapper mapper;
    private final TransactionTemplate tx;
    private final long graceMinutes;
    private final long starterMinutes;
    private final long centsPerMinute;
    private final int peakStartHour;
    private final int peakEndHour;
    private final int peakSurchargePercent;
    private final java.time.ZoneId billingZone;

    public ChunkServiceImpl(ManagedChunkRepository chunks,
                            ChunkPurchaseKeyRepository purchaseKeys,
                            NotificationService notifications,
                            LedgerService ledger,
                            ChunkMapper mapper,
                            TransactionTemplate tx,
                            @Value("${brp.chunk.governor.grace-minutes:60}") long graceMinutes,
                            @Value("${brp.chunk.governor.starter-minutes:1440}") long starterMinutes,
                            @Value("${brp.chunk.billing.cents-per-minute:0}") long centsPerMinute,
                            @Value("${brp.chunk.billing.peak-start-hour:19}") int peakStartHour,
                            @Value("${brp.chunk.billing.peak-end-hour:23}") int peakEndHour,
                            @Value("${brp.chunk.billing.peak-surcharge-percent:0}") int peakSurchargePercent,
                            @Value("${brp.reports.zone:UTC}") String billingZoneId) {
        this.chunks = chunks;
        this.purchaseKeys = purchaseKeys;
        this.notifications = notifications;
        this.ledger = ledger;
        this.mapper = mapper;
        this.tx = tx;
        this.graceMinutes = graceMinutes;
        this.starterMinutes = starterMinutes;
        this.centsPerMinute = centsPerMinute;
        this.peakStartHour = peakStartHour;
        this.peakEndHour = peakEndHour;
        this.peakSurchargePercent = peakSurchargePercent;
        this.billingZone = java.time.ZoneId.of(billingZoneId);
    }

    @Override
    @Atomic
    public ChunkSlotResponse register(String tenantId, RegisterChunkRequest req) {
        var existing = chunks.findByTenantIdAndDimensionAndChunkKey(
                tenantId, req.dimension(), req.chunkKey());
        if (existing.isPresent()) {
            return mapper.toDto(existing.get());   // re-registration is a no-op, as paperwork should be
        }
        var slot = new ManagedChunkEntity(tenantId, req.dimension(), req.chunkKey(), req.registeredBy());
        // Bootstrap (docs/09 section 3): the FIRST slot ships with starter
        // CREDITS, not currency — the ledger never mints onboarding.
        if (starterMinutes > 0 && chunks.findByTenantId(tenantId).isEmpty()) {
            slot.setCreditMinutes(starterMinutes);
            notifications.enqueue("chunk.starter.granted", "chunk.starter.granted",
                    tenantId, starterMinutes);
        }
        return mapper.toDto(chunks.save(slot));
    }

    @Override
    @Atomic
    public ChunkSlotResponse grantMinutes(String tenantId, String dimension,
                                          String chunkKey, GrantMinutesRequest req) {
        var slot = require(tenantId, dimension, chunkKey);
        if (req.minutes() <= 0) {
            throw new BadRequestException("chunk.grant.invalid");
        }
        try {
            slot.setCreditMinutes(Math.addExact(slot.getCreditMinutes(), req.minutes()));
        } catch (ArithmeticException overflow) {
            throw new BadRequestException("chunk.grant.invalid");   // overflow -> HALT is not a business model
        }
        return mapper.toDto(chunks.save(slot));
    }

    /**
     * Money path, ledger-style (docs/06 section 5): TransactionTemplate, not
     * the @Atomic aspect, so the insert-first idem key and the journal and
     * the credit grant commit as ONE unit and a DVE replay is caught outside
     * the transaction. The ledger guards the money; V12 guards the minutes.
     */
    @Override
    public ChunkSlotResponse purchaseCredits(String tenantId, String dimension,
                                             String chunkKey, PurchaseCreditsRequest req) {
        long amountCents;
        try {
            amountCents = Math.addExact(
                    Math.multiplyExact(req.minutes(), centsPerMinute),
                    surchargeCents(Math.multiplyExact(req.minutes(), centsPerMinute)));
        } catch (ArithmeticException overflow) {
            throw new BadRequestException("chunk.grant.invalid");   // money math, no jokes, no overflow
        }
        // Fast path first (sequential replay), DVE for the concurrent race —
        // same two-step discipline as the ledger's money idempotency.
        var key = new ChunkPurchaseKeyEntity.Pk(tenantId, req.idemKey());
        if (purchaseKeys.existsById(key)) {
            return chunks.findByTenantIdAndDimensionAndChunkKey(tenantId, dimension, chunkKey)
                    .map(mapper::toDto)
                    .orElseThrow(() -> new BadRequestException("chunk.unknown"));
        }
        try {
            return tx.execute(status -> {
                purchaseKeys.saveAndFlush(new ChunkPurchaseKeyEntity(tenantId, req.idemKey()));
                var slot = chunks.findForUpdate(tenantId, dimension, chunkKey)
                        .orElseThrow(() -> new BadRequestException("chunk.unknown"));
                if (amountCents > 0) {
                    ensureChunkSink(tenantId);
                    // DEBIT is +, CREDIT is - (this ledger's sign convention):
                    // the meter takes, the sink keeps.
                    ledger.post(tenantId, new PostJournalRequest(req.idemKey(), "BRC", List.of(
                            new JournalLine("chunk-sink", "DEBIT", amountCents, null),
                            new JournalLine("treasury", "CREDIT", amountCents, null))));
                }
                slot.setCreditMinutes(Math.addExact(slot.getCreditMinutes(), req.minutes()));
                return mapper.toDto(chunks.save(slot));
            });
        } catch (DataIntegrityViolationException e) {
            if (purchaseKeys.existsById(key)) {
                // same key: money charged once (the ledger agrees), minutes granted once
                return chunks.findByTenantIdAndDimensionAndChunkKey(tenantId, dimension, chunkKey)
                        .map(mapper::toDto)
                        .orElseThrow(() -> new BadRequestException("chunk.unknown"));
            }
            throw e;   // not our key race: propagate the real cause
        }
    }

    @Override
    @Atomic
    public void accrueMinutes(String tenantId, String dimension, String chunkKey, long minutes) {
        if (minutes <= 0) {
            return;
        }
        // One event reports at most a week of load-minutes; anything larger
        // is a malformed feed, not a debt (audit #9: overflow -> negative
        // credit -> surprise HALT).
        long burn = Math.min(minutes, 10_080L);
        chunks.findForUpdate(tenantId, dimension, chunkKey)
                .ifPresent(slot -> {
                    long before = slot.getCreditMinutes();
                    slot.setCreditMinutes(before - burn);
                    chunks.save(slot);
                    if (before > 0 && before - burn <= 0) {
                        notifications.enqueue("chunk.credit.exhausted", "chunk.credit.exhausted",
                                chunkKey, dimension, tenantId);
                    }
                });
    }

    @Override
    @dev.brp.common.annotation.Atomic
    public ChunkSlotResponse setPower(String tenantId, String dimension,
                                      String chunkKey, PowerRequest req) {
        var slot = require(tenantId, dimension, chunkKey);
        String want = req.enabled() ? ChunkStatus.ACTIVE : ChunkStatus.DISABLED;
        if (!want.equals(slot.getStatus())) {
            slot.setStatus(want);
            slot = chunks.save(slot);
            notifications.enqueue(req.enabled() ? "chunk.power.on" : "chunk.power.off",
                    req.enabled() ? "chunk.power.on" : "chunk.power.off",
                    chunkKey, dimension, tenantId);
        }
        return mapper.toDto(slot);
    }

    @Override
    public List<ChunkSlotResponse> slots(String tenantId) {
        return mapper.toDtoList(chunks.findByTenantId(tenantId));
    }

    @Override
    public List<ChunkAdminRow> adminRows() {
        return chunks.findAll().stream()
                .map(slot -> new ChunkAdminRow(mapper.toDto(slot),
                        verdictFor(slot), reasonFor(slot)))
                .toList();
    }

    @Override
    public List<GovernorStatusResponse> governor(String tenantId) {
        return chunks.findByTenantId(tenantId).stream()
                .map(slot -> new GovernorStatusResponse(
                        slot.getDimension(), slot.getChunkKey(),
                        verdictFor(slot), reasonFor(slot)))
                .toList();
    }

    // --- the decision table (docs/09 section 4), in the open ---

    private String verdictFor(ManagedChunkEntity slot) {
        if (ChunkStatus.DISABLED.equals(slot.getStatus()) || slot.getCreditMinutes() <= 0) {
            return VERDICT_HALT;
        }
        if (slot.getCreditMinutes() <= graceMinutes) {
            return VERDICT_THROTTLE;
        }
        return VERDICT_RUN;
    }

    private String reasonFor(ManagedChunkEntity slot) {
        if (ChunkStatus.DISABLED.equals(slot.getStatus())) {
            return "DISABLED_BY_ADMIN";
        }
        if (slot.getCreditMinutes() <= 0) {
            return "NO_CREDIT";
        }
        if (slot.getCreditMinutes() <= graceMinutes) {
            return "LOW_CREDIT";
        }
        return "OK";
    }

    private ManagedChunkEntity require(String tenantId, String dimension, String chunkKey) {
        return chunks.findByTenantIdAndDimensionAndChunkKey(tenantId, dimension, chunkKey)
                .orElseThrow(() -> new BadRequestException("chunk.unknown"));
    }

    /**
     * Time-of-use pricing (docs/10 R-3b): peak hours cost more, integral
     * cents only — the power company's behavioral art, in one config key.
     */
    long surchargeCents(long baseCents) {
        if (peakSurchargePercent == 0 || baseCents <= 0) {
            return 0;
        }
        int hour = java.time.LocalTime.now(billingZone).getHour();
        boolean peak = peakStartHour <= peakEndHour
                ? hour >= peakStartHour && hour < peakEndHour
                : hour >= peakStartHour || hour < peakEndHour;   // overnight windows wrap
        return peak ? baseCents * peakSurchargePercent / 100 : 0;
    }

    /** The chunk meter's SYSTEM_SINK: per-tenant, provisioned idempotently. */
    private void ensureChunkSink(String tenantId) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest("chunk-sink", "SYSTEM_SINK", "BRC"));
        } catch (ConflictException alreadyThere) {
            // sinks are patient; they can wait
        }
    }
}
