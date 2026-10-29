package dev.brp.service.impl;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.*;

import dev.brp.common.BadRequestException;
import dev.brp.common.event.BrpEventTypes;
import dev.brp.service.AgentMonitorService;
import dev.brp.service.QuotaService;
import dev.brp.contract.TelemetryDtos.TelemetryAck;
import dev.brp.contract.TelemetryDtos.TelemetryBatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Telemetry plane: batched, at-least-once, deduped by eventId. Deliberately
 * NOT one big transaction — a unique-violation on one event must not poison
 * the batch (rollback-only pitfall), so each event commits independently.
 */
@Service
public class TelemetryServiceImpl implements TelemetryService {

    private final TelemetryRepository events;
    private final AgentMonitorService monitor;
    private final QuotaService quotas;
    private final dev.brp.service.ChunkService chunks;
    private final dev.brp.service.HotRuleService hotRules;
    private final dev.brp.service.ComputeLevyService computeLevy;
    private final dev.brp.service.MarketPriceService marketPrices;
    private final dev.brp.service.LoadSampleService loadSamples;
    private final int maxBatchSize;

    public TelemetryServiceImpl(TelemetryRepository events, AgentMonitorService monitor,
                                QuotaService quotas, dev.brp.service.ChunkService chunks,
                                dev.brp.service.HotRuleService hotRules,
                                dev.brp.service.ComputeLevyService computeLevy,
                                dev.brp.service.MarketPriceService marketPrices,
                                dev.brp.service.LoadSampleService loadSamples,
                                @Value("${brp.telemetry.max-batch-size:1000}") int maxBatchSize) {
        this.events = events;
        this.monitor = monitor;
        this.quotas = quotas;
        this.chunks = chunks;
        this.hotRules = hotRules;
        this.computeLevy = computeLevy;
        this.marketPrices = marketPrices;
        this.loadSamples = loadSamples;
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public TelemetryAck ingest(TelemetryBatch batch) {
        if (batch.events().size() > maxBatchSize) {
            throw new BadRequestException("batch too large: " + batch.events().size());
        }
        int accepted = 0;
        int duplicates = 0;
        for (var e : batch.events()) {
            if (events.existsById(e.eventId())) {
                duplicates++;
                continue;
            }
            try {
                events.saveAndFlush(new IngestedEventEntity(e.eventId(), e.agentId(), e.type()));
                accepted++;
                fanOut(e);
                if (e.payload() instanceof Map<?, ?> p) {
                    hotRules.evaluate(e.type() == null ? "" : e.type(), p);   // docs/10 R-2
                }
            } catch (DataIntegrityViolationException race) {
                duplicates++;
            }
        }
        return new TelemetryAck(accepted, duplicates);
    }

    /** Heartbeats feed the fleet monitor; a malformed payload never kills the batch. */
    private void fanOut(dev.brp.contract.TelemetryDtos.EventEnvelope e) {
        if (!(e.payload() instanceof Map<?, ?> p)) {
            return;
        }
        try {
            switch (e.type() == null ? "" : e.type()) {
                case BrpEventTypes.AGENT_HEARTBEAT -> {
                    monitor.recordHeartbeat(e.agentId(),
                            num(p.get("mspt")), num(p.get("tps")), (int) num(p.get("backlog")));
                    // docs/13 §2: the load plane's raw material rides the same heartbeat
                    loadSamples.record(num(p.get("mspt")), (int) num(p.get("players")),
                            (int) num(p.get("entities")), (int) num(p.get("chunks")),
                            (int) num(p.get("forced")));
                }
                case BrpEventTypes.MACHINE_PLACED -> {
                    if (e.tenantId() != null) {
                        quotas.recordMachinePlaced(e.tenantId(), str(p.get("machineId")));
                    }
                }
                case BrpEventTypes.CHUNK_PERFORMANCE -> {
                    if (e.tenantId() != null) {
                        quotas.recordChunkPerformance(e.tenantId(), str(p.get("dimension")),
                                str(p.get("chunk")), num(p.get("costScore")));
                    }
                }
                case BrpEventTypes.MARKET_PRICE_OBSERVED -> {
                    marketPrices.record(str(p.get("item")), (long) num(p.get("price")),
                            str(p.get("currency")), (long) num(p.get("qty")),
                            str(p.get("venue")), "telemetry");   // docs/12 §12: CPI's food chain
                }
                case BrpEventTypes.CHUNK_LOAD_MINUTES -> {
                    if (e.tenantId() != null) {
                        long minutes = (long) num(p.get("minutes"));
                        chunks.accrueMinutes(e.tenantId(), str(p.get("dimension")),
                                str(p.get("chunkKey")), minutes);
                        computeLevy.recordUsage(e.tenantId(), minutes,
                                java.time.LocalDateTime.now());   // docs/12 §5: the same minutes feed the levy
                    }
                }
                default -> { /* plain telemetry, no fan-out */ }
            }
        } catch (RuntimeException bad) {
            org.slf4j.LoggerFactory.getLogger(TelemetryServiceImpl.class)
                    .warn("malformed heartbeat from {}: {}", e.agentId(), bad.getMessage());
        }
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
