# BRP Event Contract

The event envelope is the shared truth between the agent WAL and the backend
ingest plane (docs/06-transactions.md). Schema versioning is additive within
a major; breaking changes spawn a new event type and a two-version freeze.

## Envelope

| Field | Type | Notes |
|---|---|---|
| eventId | UUID string | globally unique; dedup key on the backend |
| type | string | see registry below |
| schemaVersion | int | envelope/DTO version, starts at 1 |
| agentId | string | provisioning-issued instance identity |
| tenantId | string? | null for server-wide events |
| occurredAt | ISO-8601 | agent clock; NEVER used for booking time |
| reliability | CRITICAL / TELEMETRY | WAL drop policy under disk pressure |
| payload | object | per-type schema |

## Event registry (v1)

| type | reliability | payload (sketch) |
|---|---|---|
| player.joined | TELEMETRY | `{player, world}` |
| player.left | TELEMETRY | `{player, playedMinutes}` |
| inventory.snapshot | TELEMETRY | `{containerId, stock:[{itemId, amount}], full:bool}` |
| machine.stats | TELEMETRY | `{machineId, kind, rpm, su, loadFactor, stalled}` |
| flow.sample | TELEMETRY | `{meterId, windowSec, counts:[{itemId, n}]}` |
| signal.executed | TELEMETRY | `{channel, level, commandId}` |
| economy.tx.requested | CRITICAL | mirrors PostJournalRequest + human intent |
| quota.exceeded | TELEMETRY | `{tenant, resource, limit}` |
| agent.heartbeat | TELEMETRY | `{tps, mspt, backlogSize}` (feeds TPS-commodity metrics, docs/03 §6) |
| chunk.load.minutes | TELEMETRY | `{dimension, chunkKey, minutes}` (chunk governance metering, docs/09 §3; billed hourly via `chunk:{tenant}:{minute}` idem keys, P2-04) |

Rules:
1. CRITICAL events are never dropped by the agent WAL, regardless of pressure.
2. Booking time is backend receive time; agent clocks are advisory only.
3. Replays of TELEMETRY are fine (backend dedups); replays of CRITICAL are
   required to be idempotent on the backend (idempotency key = eventId for
   event-carried transactions).
