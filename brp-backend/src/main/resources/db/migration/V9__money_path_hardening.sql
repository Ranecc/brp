-- V9: money-path hardening (code review round, 2026-10).
-- 1) idempotency keys become tenant-scoped: composite PK (tenant_id, idem_key).
--    A global PK let tenant B replay tenant A's key and read A's books.
-- Note: journal_entries.idem_key below is an index on the ledger rows only.
-- Note for future readers: teams.id IS the tenant_id (tenant = guild, one
-- ledger scope per team); team_members carries no extra tenant column on
-- purpose.

CREATE TABLE idempotency_keys_v2 (
    idem_key      VARCHAR(64) NOT NULL,
    tenant_id     VARCHAR(64) NOT NULL,
    response_json TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_idempotency_tenant PRIMARY KEY (tenant_id, idem_key)
);

INSERT INTO idempotency_keys_v2 (idem_key, tenant_id, response_json, created_at)
    SELECT idem_key, tenant_id, response_json, created_at FROM idempotency_keys;

DROP TABLE idempotency_keys;
ALTER TABLE idempotency_keys_v2 RENAME TO idempotency_keys;
