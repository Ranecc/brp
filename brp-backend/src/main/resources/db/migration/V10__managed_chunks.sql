-- V10: managed chunk slots — chunk governance (docs/09 section 3).
-- Chunk loading is metered, billable, remotely switchable utility.
-- Governor verdicts (RUN/THROTTLE/HALT) are computed from these columns;
-- HALT governs BRP machinery only — never player movement or simulation.

CREATE TABLE managed_chunks (
    tenant_id      VARCHAR(64) NOT NULL,
    dimension      VARCHAR(64) NOT NULL,
    chunk_key      VARCHAR(64) NOT NULL,   -- "x,z" of the chunk center
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | DISABLED (admin remote switch)
    credit_minutes BIGINT      NOT NULL DEFAULT 0,          -- prepaid + granted chunk-load minutes
    registered_by  VARCHAR(64),
    registered_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_managed_chunk PRIMARY KEY (tenant_id, dimension, chunk_key)
);
