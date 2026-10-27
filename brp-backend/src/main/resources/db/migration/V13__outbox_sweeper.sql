-- V13: outbox sweeper support (audit #2). updated_at backs the FAILED ->
-- PENDING requeue backoff; attempts caps the retry count before a row is
-- left FAILED for the humans (DEAD in spirit, FAILED in ink).
ALTER TABLE outbox_messages ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX idx_outbox_failed ON outbox_messages (status, attempts);
