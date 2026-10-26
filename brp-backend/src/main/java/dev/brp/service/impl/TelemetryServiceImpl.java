package dev.brp.service.impl;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.*;

import dev.brp.common.BadRequestException;
import dev.brp.common.event.BrpEventTypes;
import dev.brp.service.AgentMonitorService;
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
    private final int maxBatchSize;

    public TelemetryServiceImpl(TelemetryRepository events, AgentMonitorService monitor,
                                @Value("${brp.telemetry.max-batch-size:1000}") int maxBatchSize) {
        this.events = events;
        this.monitor = monitor;
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
            } catch (DataIntegrityViolationException race) {
                duplicates++;
            }
        }
        return new TelemetryAck(accepted, duplicates);
    }

    /** Heartbeats feed the fleet monitor; a malformed payload never kills the batch. */
    private void fanOut(dev.brp.contract.TelemetryDtos.EventEnvelope e) {
        if (!BrpEventTypes.AGENT_HEARTBEAT.equals(e.type()) || !(e.payload() instanceof Map<?, ?> p)) {
            return;
        }
        try {
            monitor.recordHeartbeat(e.agentId(),
                    num(p.get("mspt")), num(p.get("tps")), (int) num(p.get("backlog")));
        } catch (RuntimeException bad) {
            org.slf4j.LoggerFactory.getLogger(TelemetryServiceImpl.class)
                    .warn("malformed heartbeat from {}: {}", e.agentId(), bad.getMessage());
        }
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
