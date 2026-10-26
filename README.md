# BRP — Better Resource Plan

> An "enterprise-grade" resource planning suite for Minecraft servers (ERP for Minecraft).
> Guilds are tenants, redstone farms are capacity, management blocks are probes,
> and the ledger is the single source of truth.
>
> [中文版](README.zh.md)

## What it is

| Component | Form | Stack | Responsibility |
|---|---|---|---|
| **brp-backend** | Standalone process, never inside the game JVM | Spring Boot 3.5 / Java 21 / PostgreSQL | Ledger, tenants/RBAC, WMS/MRP/APS, monetary policy, anomaly detection, web API |
| **brp-mod** | ONE universal jar (NeoForge 1.21.1), installed on both sides | Java + Gson (feign-style declarative client over JDK HttpClient) | On the dedicated server: the **Agent** (collect/probe/execute/disk-queued). On the player client: the **UI runtime** (read-only, proxied writes) |

One mod file for everyone — server admins and players install the same jar;
what runs depends on the distribution (docs/00-overview.md).

## Version decision (researched 2026-10)

- **Primary target: Minecraft 1.21.1 + NeoForge.** Create 6 and AE2 both ship
  official 1.21.1 NeoForge builds, and major packs (e.g. "All Of NeoForge",
  updated Sept 2026) still baseline on it — the deepest tech-mod ecosystem.
- Minecraft moved to year-based versioning in 2026 (26.1 ≈ the old 1.22, 26.2 the
  second drop; NeoForge tooling supports up to 26.3). 26.x is the forward target
  once Create lands there.
- Create is NeoForge-only → **loader locked to NeoForge** (rationale in docs/02).

## Repository layout

```
brp/
├── docs/          # design docs (canonical English, .zh.md translations)
├── brp-contract/  # OpenAPI + event contract (single truth for both sides)
├── brp-backend/   # Spring Boot service (compiles, tested)
└── brp-mod/       # universal single-jar mod skeleton + adapter SPI
```

## Design docs

- [00-overview](docs/00-overview.md) — vision, glossary, trust boundaries, non-goals
- [01-architecture](docs/01-architecture.md) — components, version decision, planes, degradation, security
- [02-integrations](docs/02-integrations.md) — AE2 / Create 6 / CC:T / wireless redstone research & adapter design ⭐
- [03-economy](docs/03-economy.md) — double-entry ledger, CPI, monetary policy, crisis detection, TPS-as-commodity
- [04-technical-control](docs/04-technical-control.md) — farm metering & control (flow meters / signals / quotas)
- [05-anomaly-anticheat](docs/05-anomaly-anticheat.md) — behavior-driven anomaly detection / anti-cheat
- [06-transactions](docs/06-transactions.md) — idempotency, outbox, saga, ledger concurrency ⭐

## Quick start (backend)

```bash
cd brp-backend
docker compose -f ../docker-compose.yml up -d postgres redis
mvn spring-boot:run   # or: mvn test (ledger idempotency & telemetry dedup tests)
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| P0 | repo + contracts + ledger core (idempotent, double-entry) + agent skeleton | ✅ this repo |
| P1 | tenants/RBAC, player profiles, web portal, agent upload pipeline | ⬜ |
| P2 | WMS adapters (vanilla → AE2 → Create) + dashboards + alerts | ⬜ |
| P3 | MRP (BOM explosion + work orders) | ⬜ |
| P4 | APS + machine monitoring + signal hub | ⬜ |
| P5 | exchange/futures, k8s + helm chart (the performance-art finale) | ⬜ |
