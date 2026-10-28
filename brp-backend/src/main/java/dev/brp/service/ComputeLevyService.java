package dev.brp.service;

import java.time.LocalDateTime;

/**
 * The marginal compute levy (docs/12 §5): usage lands as minute rows at
 * ingest (row-locked accumulation), the materializer prices each minute
 * with MARGINAL share bands and posts one journal per tenant-minute with
 * idem tax:{tenant}:{minute}. Emeralds are the invoice language; the
 * ledger speaks BRC at the emerald-leg rate.
 */
public interface ComputeLevyService {

    /** Telemetry ingest hook: accumulate usage into the minute row. */
    void recordUsage(String tenantId, double minutes, LocalDateTime at);

    /** Price + post one minute's rows (idempotent; posted rows are skipped). */
    int materialize(LocalDateTime minuteBucket);
}
