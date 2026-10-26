package dev.brp.service;

import dev.brp.contract.TelemetryDtos.TelemetryAck;
import dev.brp.contract.TelemetryDtos.TelemetryBatch;

/** Telemetry ingest contract (impl: {@link TelemetryServiceImpl}). */
public interface TelemetryService {

    TelemetryAck ingest(TelemetryBatch batch);
}
