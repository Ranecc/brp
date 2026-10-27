# 10 — Reviewed backlog: ideas that grew on the map

Eight proposals arrived from outside the foxhole (review pass, 2026-10-27),
graded against the CURRENT tree — two arrived carrying stale premises,
corrected here so the record stays honest:

- **"The outbox has no SKIP LOCKED yet"** — stale by one day: the outbox has
  had a CAS claim (`PENDING -> SENDING` compare-and-set) plus a sweeper
  since audit wave 3.
- **"Chunk billing's idem pattern needs defining"** — already shipped as
  PREPAID (P2-04): caller-carried idem keys, ledger same-transaction
  idempotency for the money, V12 insert-only keys for the minutes. The
  proposed hourly `chunk:{tenant}:{hour}` post-paid pattern is rejected —
  prepaid replaced it.

The verdicts below are merged into the phase boards where noted.

| # | Idea | Anchors (where it grows) | Verdict | Phase |
|---|---|---|---|---|
| R-1 | **Reconciliation desk (对账科)** — a scheduled job + portal page that proves the three invariants (Σ balances ≡ 0, item conservation, faucet/sink conservation, docs/05 §3; implementation promised in docs/06 §5 and never shipped). Breaches become `chunk_incident`-style rows with the department's signature apology template. | docs/05 §3, docs/06 §5, ledger + `economy_daily` | **ACCEPT, staged.** v1: monetary Σ≡0 + faucet/sink vs `economy_daily` cross-check (all data exists today). v2: item conservation lands when flow meters do (P2-14). Zero new deps; anticheat L1 for free. | P2 tail |
| R-2 | **L1 hot-rule table on `fanOut`** — threshold rules (type/field/op/value -> incident + outbox notify), table-backed, TTL-reloaded, evaluated as telemetry arrives. | docs/05 L1, `TelemetryServiceImpl.fanOut` | **ACCEPT.** The hook is literally already there; rules as data, not code. | P2 tail |
| R-3 | **Chunk billing idem + time-of-use pricing** | docs/09 §3, §5 | **SPLIT.** The hourly-billing idem half is REJECTED (prepaid already shipped, see above). The **peak/off-peak surcharge** half is ACCEPTED: a time-of-use multiplier curve on `cents-per-minute` — an APS signal (docs/09 §5) and a power company's behavioral art in one config key. | P2 tail |
| R-4 | **Wash-cycle detection** — `journal_entries` is a weighted digraph; Tarjan SCC + amount/timing scoring per tenant, results feed R-1's desk. No models, no sidecar, pure JVM. | docs/05 L2/L3 (as "L2.5") | **ACCEPT with a design note:** cycles per se are legal commerce (A pays B pays A is a market, not a crime) — the scoring carries the weight: short cycles, round amounts, tight timing. | P3 |
| R-5 | **CC:T Lua forms API** — the Form Terminal prints the paper; CC:T players file it from Lua. Technical players automate the bureaucracy. | docs/02 §4, docs/09 §1-2 | **ACCEPT (parked).** Depends on P2-10 (live forms) + P2-14 (CC:T adapter). The project's most self-consistent joke; it must survive to production. | P2 tail/P3 |
| R-6 | **`market.price.observed` event** — a price observation contract so CPI (docs/03 §3) stops waiting for a WMS that only counts items. Feeders: manual entries + shop plugins later. | docs/03 §3, events.md | **ACCEPT — contract seeded in the same commit as this doc.** | P3 |
| R-7 | **TPS bonds** — the treasury issues bonds settled in REAL tick time; heartbeat mspt is the settlement price. An exchange-shaped MVP without an exchange. | docs/03 §6, P5 satire clause | **ACCEPT.** Futures are roadmap-sanctioned satire; options stay permanently sealed (docs/00 non-goals). Ledger + heartbeat data only. | P5 MVP |
| R-8 | **Batch-auction exchange** — clear every N ticks in one transaction, single-tx settlement, naturally idempotent. Real exchanges run thin markets exactly like this. | P5 | **ACCEPT as the P5 exchange design.** "Open at 9:00, closed at 9:05, like all great thin markets." The premise that inspired it (no row claiming anywhere) was already fixed — the conclusion survives the correction. | P5 |
