package dev.brp.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryRepository extends JpaRepository<IngestedEventEntity, String> {}
