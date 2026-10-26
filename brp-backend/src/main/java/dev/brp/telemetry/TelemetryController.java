package dev.brp.telemetry;

import dev.brp.common.BadRequestException;
import dev.brp.contract.TelemetryDtos.TelemetryAck;
import dev.brp.contract.TelemetryDtos.TelemetryBatch;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryRepository events;
    private final int maxBatchSize;

    public TelemetryController(TelemetryRepository events,
                               @Value("${brp.telemetry.max-batch-size:1000}") int maxBatchSize) {
        this.events = events;
        this.maxBatchSize = maxBatchSize;
    }

    /**
     * Telemetry plane: batched, at-least-once, deduped by eventId. The ack
     * reports duplicates so the agent can advance its WAL watermark.
     *
     * Deliberately NOT one big transaction: a unique-violation on one event must
     * not poison the rest of the batch (rollback-only pitfall), so each event
     * commits independently. Bulk insert optimization is a P1 concern.
     */
    @PostMapping("/batches")
    public TelemetryAck ingest(@Valid @RequestBody TelemetryBatch batch) {
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
                // Concurrent insert of the same eventId: unique constraint wins, count as duplicate.
                duplicates++;
            }
        }
        return new TelemetryAck(accepted, duplicates);
    }
}
