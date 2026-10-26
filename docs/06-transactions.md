# 06 — Idempotency, Outbox, Saga, Ledger Concurrency ⭐

> [中文版](06-transactions.zh.md)
>
> There is no exactly-once in distributed systems — only at-least-once
> delivery with exactly-once effects. This document is the contract both the
> agent and the backend implement.

## 1. Event envelope (see brp-contract/events.md)

```json
{
  "eventId": "uuid", "idempotencyKey": "uuid", "schemaVersion": 1,
  "agentId": "ag-x", "tenantId": "t-1",
  "type": "inventory.snapshot", "occurredAt": "...", "payload": {}
}
```

## 2. Idempotency (Idempotency-Key contract)

- every write: client-generated UUID in the body/`Idempotency-Key` header
- server: `idempotency_keys` row holds the response snapshot; the row is
  inserted **in the same transaction as the work**, so row-exists ⇔ response
  replayable (no in-doubt state)
- duplicate request → replay the stored response (client sees a
  semantically identical answer, `replayed=true`)
- concurrent duplicates: the unique PK decides at commit; the loser rolls
  back completely (including balance changes) and replays the winner's
  snapshot — portable across PostgreSQL and H2
- timeouts: client retries with the SAME key, exponential backoff + jitter

## 3. Outbox (persisted before it counts as sent)

**Agent side (file WAL)** — implemented in
`brp-mod/src/main/java/dev/brp/mod/queue/DiskBackedEventQueue.java`:
```
event → append line to WAL file → sender thread batches POST →
backend acks all eventIds → advance watermark → compact segment
```
Disk-watermark guard: under pressure drop oldest **TELEMETRY** events only;
CRITICAL (monetary/command) events are never dropped (queue grows, alert fires).

**Backend side (DB outbox)**: business transaction writes `outbox(event_id,
payload, status)` atomically; a dispatcher delivers (SSE / Discord / webhooks)
at-least-once; downstream dedups by event_id.

## 4. Saga (cross-module long transactions, compensations not rollbacks)

Team-vault withdrawal after approval:
```
1. ledger: escrow-freeze      ─ fail → nothing happened, stop
2. inventory: ME-bridge out (key k) ─ fail → compensate: unfreeze
3. ledger: freeze → final debit      ─ fail → compensate: revert inventory (k'), unfreeze
4. notify: Discord/mailbox           ─ fail → retry only (non-critical step)
```
Implementation: a `saga_instances` state machine; every step idempotent and
re-entrant; compensations carry their own idempotency keys. Rule of thumb:
**escrow-freeze is the universal compensation point** — freeze first, act
second, any failure lands safely.

## 5. Ledger concurrency & consistency

- `journal_entries` append-only; revoke UPDATE/DELETE at the DB role level
- atomicity: one transaction inserts all journal lines + the idempotency row;
  constraints enforce direction enums and positive amounts, the service
  asserts debit==credit>0 before writing
- hot accounts (system shop): shard balances across N sub-accounts to spread
  row-lock contention; normal accounts are fine with optimistic locking
  (retry with the same idempotency key on conflict — always safe)
- reconciliation job daily: the three invariants of doc 05 §3 + agent↔backend
  flow-meter count checksums over sampled windows

## 6. Event versioning

- `schemaVersion` additive-only within a major; backend supports N and N-1
- breaking change = new event type name + two-version freeze on the old one;
  agents that refuse upgrade lose new features, never the old pipeline

## 7. Annotation kit (brp-common, shared by backend and agent)

| Annotation | Backend semantics | Agent semantics |
|---|---|---|
| `@Idempotent(key, ttl)` | SpEL key; `idem_store` row stores the response; replays return it; races resolved by PK | WAL dedup by eventId / key before enqueue |
| `@Cached(key, ttl, maxSize)` | Caffeine, TTL from write, nulls not cached | CacheFront (Caffeine wrapper, relocation-safe) |
| `@Atomic(timeoutMs)` | one DB transaction, all-or-nothing | one SQLite transaction via LocalStore |
| `@Compensable(saga, step, compensation)` | durable RUNNING→DONE/FAILED rows; SagaEngine dispatches reverse-order compensations + crash recovery | recorded into the local log, uploaded with telemetry |

Aspect order (outer→inner): `@Cached` → `@Idempotent` → `@Atomic` → `@Compensable`
— cache hits short-circuit everything, replays skip transactions, saga steps
stay atomic.

Honesty clause: `@Idempotent` stores the response AFTER success — a crash
between effect and store can re-execute. Financial mutations therefore use the
ledger's same-transaction idempotency row (section 2/5), not this aspect.

## 9. Hardened invariants (code review round, 2026-10)

- Idempotency keys are TENANT-SCOPED (composite PK (tenant_id, idem_key), V9):
  replay never crosses tenants; the same key in another tenant is a different
  operation.
- Integrity violations replay ONLY when the tenant's own key row exists;
  everything else propagates with its real cause.
- Saga rows are written REQUIRES_NEW (SagaRecorder): the audit trail survives
  the caller's rollback, so SagaRecovery recovers facts, not fiction.
- Recovery scopes (saga, correlation) — business correlations collide — and
  compensates in REVERSE definition order.
- Journals validate currency match, positive amounts (service-side), and
  treat TOCTOU account disappearance as 400, not 500.
