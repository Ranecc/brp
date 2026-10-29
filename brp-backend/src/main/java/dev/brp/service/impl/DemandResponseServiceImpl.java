package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.SignalDtos.SignalSetCommand;
import dev.brp.dto.LoadDtos.DeferredJobResponse;
import dev.brp.dto.LoadDtos.FileDeferredJobRequest;
import dev.brp.entity.DeferredJobEntity;
import dev.brp.repository.DeferredJobRepository;
import dev.brp.service.DemandResponseService;
import dev.brp.service.LedgerService;
import dev.brp.service.ShopFloorService;
import dev.brp.service.LoadSampleService;
import dev.brp.service.SignalService;
import dev.brp.service.TariffService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The DR desk (docs/13 §5). Billing note: the reservation is a DISPATCH FEE
 * for the guaranteed window — priority scheduling, shed protection — not a
 * prepayment of the compute levy, which keeps billing actual minutes
 * independently. One price of time, two line items, like every utility bill.
 *
 * Shed order of the grid: DR windows go first (interruptible by definition),
 * unpaid-chunk enforcement is firmer, credit cutoff firmer still.
 */
@Service
public class DemandResponseServiceImpl implements DemandResponseService {

    static final String SIGNAL_CHANNEL_PREFIX = "@dr/";
    static final int LEVEL_OPEN = 15;
    static final int LEVEL_CLOSE = 0;
    private static final Duration SHED_EXTENSION = Duration.ofHours(1);

    private final DeferredJobRepository jobs;
    private final dev.brp.repository.CreditLineRepository creditLines;
    private final dev.brp.service.ShopFloorService shopFloor;
    private final LoadSampleService loadSamples;
    private final TariffService tariff;
    private final LedgerService ledger;
    private final SignalService signals;
    private final Clock clock;
    private final long baseCentsPerMinute;
    private final int msptBudget;
    private final int maxSheds;

    /** Replay cache: idem key -> the job it already created. */
    private final Map<String, Long> filedKeys = new ConcurrentHashMap<>();

    public DemandResponseServiceImpl(DeferredJobRepository jobs,
                                     dev.brp.repository.CreditLineRepository creditLines,
                                     dev.brp.service.ShopFloorService shopFloor,
                                     LoadSampleService loadSamples,
                                     TariffService tariff,
                                     LedgerService ledger,
                                     SignalService signals,
                                     Clock clock,
                                     @Value("${brp.levy.compute.base-emeralds-per-minute:0}") long baseCentsPerMinute,
                                     @Value("${brp.dr.mspt-budget:40}") int msptBudget,
                                     @Value("${brp.dr.max-sheds:2}") int maxSheds) {
        this.jobs = jobs;
        this.creditLines = creditLines;
        this.shopFloor = shopFloor;
        this.loadSamples = loadSamples;
        this.tariff = tariff;
        this.ledger = ledger;
        this.signals = signals;
        this.clock = clock;
        this.baseCentsPerMinute = baseCentsPerMinute;
        this.msptBudget = msptBudget;
        this.maxSheds = maxSheds;
    }

    @Override
    public DeferredJobResponse file(String tenantId, FileDeferredJobRequest req) {
        // docs/13 section 6: the credit desk blocks new flexible commitments
        // (repo read directly — DemandResponse -> repo, CreditService -> DemandResponse stays one-way)
        creditLines.findById(tenantId).ifPresent(line -> {
            String st = line.getStatus();
            if (dev.brp.entity.CreditLineEntity.RESTRICTED.equals(st)
                    || dev.brp.entity.CreditLineEntity.DEFAULTED.equals(st)) {
                throw new BadRequestException("credit.restricted");
            }
        });
        if (req.idemKey() == null || req.idemKey().isBlank()) {
            throw new BadRequestException("dr.idem.required");
        }
        if (req.estMinutes() <= 0 || req.estMinutes() > 10_080) {
            throw new BadRequestException("dr.est.invalid");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime earliest = req.earliest() == null ? now : LocalDateTime.parse(req.earliest());
        LocalDateTime deadline = req.deadline() == null
                ? now.plusDays(7) : LocalDateTime.parse(req.deadline());
        if (!deadline.isAfter(earliest)) {
            throw new BadRequestException("dr.window.invalid");
        }
        String idem = "dr:" + tenantId + ":" + req.idemKey();
        Long existing = filedKeys.get(idem);
        if (existing != null) {
            return jobs.findById(existing).map(DemandResponseServiceImpl::toDto)
                    .orElseThrow(() -> new BadRequestException("dr.state.lost"));
        }
        try {
            DeferredJobEntity job = jobs.saveAndFlush(new DeferredJobEntity(tenantId, req.idemKey(),
                    req.resource() == null ? "compute" : req.resource(),
                    req.quantity(), req.estMinutes(), earliest, deadline));
            filedKeys.put(idem, job.getId());
            return toDto(job);
        } catch (DataIntegrityViolationException race) {
            // concurrent duplicate: unique idem_key — replay whatever landed
            return jobs.findByTenantIdAndIdemKey(tenantId, req.idemKey()).stream()
                    .findFirst().map(DemandResponseServiceImpl::toDto)
                    .orElseThrow(() -> new BadRequestException("dr.state.lost"));
        }
    }

    @Override
    public List<DeferredJobResponse> list(String tenantId) {
        return jobs.findByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .map(DemandResponseServiceImpl::toDto).toList();
    }

    @Override
    public DeferredJobResponse cancel(String tenantId, long jobId) {
        DeferredJobEntity job = require(tenantId, jobId);
        switch (job.getStatus()) {
            case DeferredJobEntity.FILED -> job.close(DeferredJobEntity.CANCELLED);
            case DeferredJobEntity.AWARDED, DeferredJobEntity.RUNNING -> {
                enqueue(job, LEVEL_CLOSE);
                refund(job);
                job.close(DeferredJobEntity.CANCELLED);
            }
            default -> throw new BadRequestException("dr.status.invalid");
        }
        return toDto(jobs.save(job));
    }

    @Override
    @Scheduled(fixedDelayString = "${brp.dr.award-ms:60000}")
    public void awardDueJobs() {
        LocalDateTime now = LocalDateTime.now(clock);
        expirePastDeadline(now);
        List<DemandResponseMath.Hour> hours = forecastHours();
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.FILED)) {
            if (job.getEarliest().isAfter(now)) {
                continue;
            }
            DemandResponseMath.Plan plan = DemandResponseMath.plan(hours, job.getEstMinutes(),
                    job.getEarliest(), job.getDeadline());
            if (plan == null) {
                continue;   // no feasible window yet; stays FILED until the deadline expires it
            }
            job.award(plan.start(), plan.hours() * 60L);
            jobs.save(job);
            reserve(job);
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${brp.dr.award-ms:60000}")
    public void dispatchDueSignals() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.AWARDED)) {
            if (job.getAwardedStart() != null && !job.getAwardedStart().isAfter(now)) {
                enqueue(job, LEVEL_OPEN);
                job.markRunning();
                jobs.save(job);
            }
        }
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.RUNNING)) {
            if (job.getAwardedStart() != null
                    && job.getAwardedStart().plusMinutes(job.getAwardedMinutes()).isBefore(now)) {
                enqueue(job, LEVEL_CLOSE);
                job.close(DeferredJobEntity.DONE);
                jobs.save(job);
            }
        }
    }

    @Scheduled(fixedDelayString = "${brp.dr.shed-ms:30000}")
    public void shedCheck() {
        shedIfOverloaded(loadSamples.liveMspt());
    }

    @Override
    public void shedIfOverloaded(double liveMspt) {
        if (liveMspt <= msptBudget) {
            return;
        }
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.RUNNING)) {
            enqueue(job, LEVEL_CLOSE);
            refund(job);
            shedDowntime(job);
            if (job.getShedCount() + 1 > maxSheds) {
                job.close(DeferredJobEntity.CANCELLED);   // the grid gave it chances
            } else {
                job.shed(SHED_EXTENSION);
            }
            jobs.save(job);
        }
    }

    /** The shed lands in the downtime ledger — OEE sees the grid operator's hand. */
    private void shedDowntime(DeferredJobEntity job) {
        LocalDateTime start = job.getAwardedStart() == null ? LocalDateTime.now(clock) : job.getAwardedStart();
        shopFloor.recordDowntime(job.getTenantId(), "*", dev.brp.entity.DowntimeEventEntity.DR_SHED,
                "dr-shed:" + job.getId() + ":" + (job.getShedCount() + 1), start, LocalDateTime.now(clock));
    }

    @Override
    public void suspendTenant(String tenantId) {
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.RUNNING)) {
            if (!job.getTenantId().equals(tenantId)) {
                continue;
            }
            enqueue(job, LEVEL_CLOSE);
            refund(job);
            job.shed(SHED_EXTENSION);
            jobs.save(job);
        }
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.AWARDED)) {
            if (!job.getTenantId().equals(tenantId) || job.getAwardedStart() != null) {
                continue;
            }
            refund(job);
            job.close(DeferredJobEntity.EXPIRED);
            jobs.save(job);
        }
    }

    // --- internals ---

    private void expirePastDeadline(LocalDateTime now) {
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.FILED)) {
            if (job.getDeadline().isBefore(now)) {
                job.close(DeferredJobEntity.EXPIRED);
                jobs.save(job);
            }
        }
        for (DeferredJobEntity job : jobs.findByStatusOrderByCreatedAtAsc(DeferredJobEntity.AWARDED)) {
            if (job.getAwardedStart() == null && job.getDeadline().isBefore(now)) {
                refund(job);
                job.close(DeferredJobEntity.EXPIRED);
                jobs.save(job);
            }
        }
    }

    private List<DemandResponseMath.Hour> forecastHours() {
        return loadSamples.forecast(48).hours().stream()
                .map(h -> new DemandResponseMath.Hour(LocalDateTime.parse(h.hourStart()),
                        h.multiplierPct(), h.predictedMspt(), h.schedulable()))
                .toList();
    }

    /** The dispatch fee: est minutes × base rate × the awarded window's tariff. */
    private void reserve(DeferredJobEntity job) {
        long amount = feeOf(job);
        if (amount <= 0) {
            return;
        }
        ensureSink(job.getTenantId());
        ledger.post(job.getTenantId(), new PostJournalRequest(
                "dr-reserve:" + job.getId() + ":" + job.getWindowSeq(), "BRC", List.of(
                new JournalLine("dr-sink", "DEBIT", amount, null),
                new JournalLine("treasury", "CREDIT", amount, null))));
    }

    private void refund(DeferredJobEntity job) {
        long amount = feeOf(job);
        if (amount <= 0) {
            return;
        }
        ensureSink(job.getTenantId());
        ledger.post(job.getTenantId(), new PostJournalRequest(
                "dr-refund:" + job.getId() + ":" + job.getWindowSeq(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", amount, null),
                new JournalLine("dr-sink", "CREDIT", amount, null))));
    }

    /** Same formula for reserve and refund — tariff drift is a footnote, not a bug. */
    private long feeOf(DeferredJobEntity job) {
        LocalDateTime anchor = job.getAwardedStart() == null
                ? LocalDateTime.now(clock) : job.getAwardedStart();
        int pct = tariff.multiplierPctAt(anchor.toInstant(java.time.ZoneOffset.UTC));
        return Math.multiplyExact(job.getEstMinutes(), baseCentsPerMinute) * pct / 100;
    }

    private void enqueue(DeferredJobEntity job, int level) {
        signals.enqueue(job.getTenantId(), new SignalSetCommand(
                SIGNAL_CHANNEL_PREFIX + job.getId(), level));
    }

    private void ensureSink(String tenantId) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest("dr-sink", "SYSTEM_SINK", "BRC"));
        } catch (dev.brp.common.ConflictException alreadyThere) {
            // sinks are patient
        }
    }

    private DeferredJobEntity require(String tenantId, long jobId) {
        DeferredJobEntity job = jobs.findById(jobId).orElseThrow(() -> new BadRequestException("dr.job.unknown"));
        if (!job.getTenantId().equals(tenantId)) {
            throw new BadRequestException("dr.job.unknown");
        }
        return job;
    }

    static DeferredJobResponse toDto(DeferredJobEntity j) {
        return new DeferredJobResponse(j.getId(), j.getTenantId(), j.getResource(), j.getQuantity(),
                j.getEstMinutes(), j.getEarliest().toString(), j.getDeadline().toString(),
                j.getStatus(), j.getAwardedStart() == null ? null : j.getAwardedStart().toString(),
                j.getAwardedMinutes(), j.getWindowSeq(), j.getShedCount());
    }
}
