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
| machine.placed | TELEMETRY | `{machineId}` (machine licensing, docs/03 §8 — already shipped by the backend fan-out) |
| chunk.performance | TELEMETRY | `{dimension, chunk, costScore}` (chunk incidents, docs/03 §8 — already shipped) |
| agent.heartbeat | TELEMETRY | `{tps, mspt, backlogSize}` (feeds TPS-commodity metrics, docs/03 §6) |
| chunk.load.minutes | TELEMETRY | `{dimension, chunkKey, minutes}` (chunk governance metering, docs/09 §3; burns prepaid credits; purchases idempotent via caller idem keys + V12) |

### v1 proposals (NOT yet in force — rows land in this table in the same
commit as the agent-side mixin, per contract-before-code; design: docs/03 §10)

| type | reliability | payload (sketch) |
|---|---|---|
| merchant.trade | TELEMETRY | `{villager, profession, offer, itemIdIn, itemIdOut, count, dimension, chunkKey}` (one per completed villager trade; the licensed-mint water gauge — mint fee aggregates `tax:tenant:mint:day`) |
| villager.cured | TELEMETRY | `{villager, player}` (cure resets trade pairs = the mint's speed governor) |

Rules:
1. CRITICAL events are never dropped by the agent WAL, regardless of pressure.
2. Booking time is backend receive time; agent clocks are advisory only.
3. Replays of TELEMETRY are fine (backend dedups); replays of CRITICAL are
   required to be idempotent on the backend (idempotency key = eventId for
   event-carried transactions).
