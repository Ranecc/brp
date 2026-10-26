package dev.brp.telemetry;

import dev.brp.common.BadRequestException;
import dev.brp.contract.TelemetryDtos.TelemetryAck;
import dev.brp.contract.TelemetryDtos.TelemetryBatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Telemetry plane: batched, at-least-once, deduped by eventId. Deliberately
 * NOT one big transaction — a unique-violation on one event must not poison
 * the batch (rollback-only pitfall), so each event commits independently.
 */
@Service
public class TelemetryServiceImpl implements TelemetryService {

    private final TelemetryRepository events;
    private final int maxBatchSize;

    public TelemetryServiceImpl(TelemetryRepository events,
                                @Value("${brp.telemetry.max-batch-size:1000}") int maxBatchSize) {
        this.events = events;
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
            } catch (DataIntegrityViolationException race) {
                duplicates++;
            }
        }
        return new TelemetryAck(accepted, duplicates);
    }
}
