-- V6: agent heartbeat materialization. Latest-known state per agent;
-- the TPS report reads real numbers from here (docs/04, docs/03 section 6).

CREATE TABLE agent_state (
    agent_id    VARCHAR(64) PRIMARY KEY,
    mspt        DOUBLE      NOT NULL DEFAULT 0,
    tps         DOUBLE      NOT NULL DEFAULT 0,
    backlog     INT         NOT NULL DEFAULT 0,
    reported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
