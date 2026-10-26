# 01 — Architecture

> [中文版](01-architecture.zh.md)

## 1. Version & loader decision (researched 2026-10)

**Verdict: Minecraft 1.21.1 + NeoForge as the primary target.**

| Candidate | Evidence | Verdict |
|---|---|---|
| **1.21.1 / NeoForge** | Create 6 official (wiki ships a "Depend on Create / NeoForge 1.21.1" guide); AE2 ships official 1.21.1 NeoForge builds; big packs updated Sept 2026 still baseline on 1.21.1 | ✅ primary |
| 26.1 / 26.2 (year-based) | MC switched to year versioning in 2026; NeoForge tooling supports to 26.3; tech mods (Create especially) not confirmed there yet | 🔭 watch; port when Create lands |
| 1.20.1 / Forge | big legacy ecosystem, but the past | ❌ |
| Fabric | AE2 has Fabric builds, but **Create is NeoForge-only** and the arm/SU/logistics integration is core | ❌ locked to NeoForge |

Port strategy: agent core imports **zero Minecraft classes** (see the SPI),
Minecraft interaction stays a thin shell — keeps the 26.x port cheap.

## 2. Components & deployment

ONE universal mod jar (single-file install for servers and players); what runs
depends on distribution:

```
┌──────────────────────────── host / internal network ───────────────────────────┐
│                                                                                │
│  MC dedicated server                BRP Backend (separate JVM)                 │
│  ┌────────────────────────┐         ┌────────────────────────────────┐         │
│  │ brp-mod jar = AGENT    │  HTTPS  │ Spring Boot 3.5                 │         │
│  │  event collection      │ ──────► │  REST    command plane (low)    │         │
│  │  inventory/machine     │ ◄────── │  batch   telemetry plane (high) │         │
│  │  probes                │ poll/ack│  push    signal commands        │         │
│  │  signal execution      │         │  PostgreSQL / Redis             │         │
│  │  disk WAL (degraded)   │         └───────┬────────────────────────┘         │
│  └────────────────────────┘                 │ web                               │
│  (same jar on players' clients = UI runtime)┴► portal (OAuth2) + Grafana        │
└────────────────────────────────────────────────────────────────────────────────┘
```

Why Spring never lives inside the game JVM (project rule #1, restated):
- bundled Netty/Guava/log4j in the MC server vs Spring Boot's managed deps =
  guaranteed conflicts; ModLauncher/SecureJarHandler isolation is misery
- crash isolation, both directions
- backend deploys without restarting the game server

## 3. Three communication planes

| Plane | Direction | Protocol | Rate | Semantics |
|---|---|---|---|---|
| command | agent → backend | REST + idempotency key | low (human-paced) | strongly consistent, sync result |
| telemetry | agent → backend | `POST /telemetry/batches` | high (seconds-batched) | at-least-once + dedup |
| push | backend → agent | poll+ack v0, SSE in P1 | low | signal/permission commands |

On the Feign aspiration: the agent defines a **declarative interface**
(`BrpServerApi`, feign-shaped) implemented over JDK HttpClient + Gson — both
already on the MC classpath — instead of shading feign-core into a mod jar.
Backend outbound calls (Discord, Mojang API) may use Spring Cloud OpenFeign in P1.

## 4. Data authority & consistency

- Ledger authority: **backend only**; in-game balances are cached views.
- Stock snapshots: periodic full + deltas, materialized read models with
  `capturedAt` staleness labels.
- Every event carries `eventId`, `idempotencyKey`, `schemaVersion`,
  `occurredAt`, `agentId` → safe redelivery. Details in doc 06.

## 5. Degradation (the game server wins)

| Failure | Behavior |
|---|---|
| backend down | agent appends to local disk WAL, replays in order on recovery; cached views marked `stale` |
| agent down | backend stale but consistent; push commands queue in outbox |
| MC server crash | WAL persists on disk; nothing booked is lost |
| clock skew | booking time = backend receive time, always; agent clocks advisory |

**Hard constraint: the agent never performs network IO on a tick thread.**
Game threads only take lock-free snapshots; serialization and HTTP live on a
dedicated thread pool.

## 6. Security model

- enrollment: one-time provisioning token → instance_id + secret
- requests: HMAC-SHA256(instance_id, timestamp, nonce, bodyHash), ±30s window,
  nonce replay cache (v0: static bearer token on a trusted network; HMAC in P1)
- transport: plain HTTP on a private network is fine; TLS/wireguard otherwise
- player portal: OAuth2/OIDC, in-game code binding to MC account; RBAC synced
  from LuckPerms
- every write lands in an append-only audit log

## 7. Backend stack

Spring Boot 3.5 / Java 21 · Flyway (single DDL truth) · PostgreSQL 16 ·
Redis 7 (cache + pub/sub, P1) · Micrometer + Prometheus (self-monitoring,
dogfooding the dashboards) · Testcontainers + H2 (tests) · OpenAPI-first
contracts in brp-contract.
