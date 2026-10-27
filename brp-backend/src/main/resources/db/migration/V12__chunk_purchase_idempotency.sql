-- V12: purchase idempotency for chunk credits (docs/09 section 3).
-- The ledger's idem row guards the MONEY; this table guards the MINUTES,
-- in the same transaction — insert-first, race loser replays the winner.
CREATE TABLE chunk_purchase_keys (
    tenant_id  VARCHAR(64) NOT NULL,
    idem_key   VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_chunk_purchase PRIMARY KEY (tenant_id, idem_key)
);
