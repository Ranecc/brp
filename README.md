# BRP — Better Resource Plan

![enterprise readiness](https://img.shields.io/badge/enterprise%20readiness-TBD-important)
![seriousness](https://img.shields.io/badge/seriousness-0%25-red)
![emeralds lost](https://img.shields.io/badge/emeralds%20lost-0-brightgreen)
![CI](https://github.com/Ranecc/brp/actions/workflows/ci.yml/badge.svg) <!-- the only real one -->

![build](https://img.shields.io/badge/build-maybe%20(somehow)-success)
![coverage](https://img.shields.io/badge/coverage-0%25%20(trust%20me%20bro)-yellow)
![code%20style](https://img.shields.io/badge/code%20style-AI%20hallucinated-blueviolet)
![dependencies](https://img.shields.io/badge/dependencies-Eventually%20Consistent-orange)
![PRs](https://img.shields.io/badge/PRs-welcome%20(but%20will%20break%20everything)-brightgreen)

> Delivering an *unadulterated*, E-n-t-e-r-p-r-i-s-e-grade Spring Boot experience in Minecraft XD
>
> It is my great honour to introduce: SaaS multi-tenant player guilds,
> guild SCM & EHR, vanilla-redstone / mod-powered MES/APS, material WMS,
> server-wide financial markets & consolidated finance,
> and a server TPS trade market (๑•̀ㅂ•́)و✧
>
> [中文版](README.zh.md) · fellow coding agents: read [AGENTS.md](AGENTS.md) first, sit down and be relaxing.

## What it is

| Component | Form | Stack | Responsibility |
|---|---|---|---|
| **brp-backend** | Standalone process, never inside the game JVM | Spring Boot 3.5 / Java 21 / PostgreSQL | Ledger, tenants/RBAC, WMS/MRP/APS/EHR/SCM, monetary policy, anomaly detection, web API |
| **brp-mod** | ONE universal jar (NeoForge 1.21.1), installed on both sides | Java + Gson (feign-style declarative client over JDK HttpClient) | On the dedicated server: the **Agent** (collect/probe/execute/disk-queued). On the player client: the **UI runtime** (read-only, proxied writes) |

One mod file for everyone — server admins and players install the same jar;
what runs depends on the distribution (docs/00-overview.md).

## Repository layout

```
brp/
├── docs/          # design docs (canonical English, .zh.md translations)
├── brp-contract/  # OpenAPI + event contract (single truth for both sides)
├── brp-common/    # zero-dependency shared kit: @Idempotent/@Cached/@Atomic/@Compensable
├── brp-backend/   # Spring Boot service (compiles; 38/38 tests green, CI-enforced)
└── brp-mod/       # universal single-jar mod: ACL ports, sidecar supervisor,
                   # adapter SPI, WAL queue, SQLite/Caffeine local store plan
```

Deployment: the backend runs as a **managed sidecar** (the agent spawns
`brp/brp-backend.jar` as a child process, restarts it, stops it with the
server) or fully standalone via docker-compose — same jar either way
(docs/01 section 2.5).

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
cp .env.example .env            # set a real BRP_DB_PASS (compose enforces it)
docker compose up -d --build    # postgres + backend, ports bound to 127.0.0.1
mvn -pl brp-backend -am test    # 38/38 green or no commit: ledger, saga, approvals, portal — go touch grass while it runs
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| P0 | repo + contracts + ledger core (idempotent, double-entry) + agent skeleton | ✅ this repo |
| P1 | teams, player sync, saga engine, approval labyrinth, TPS reports (real mspt), heartbeats, economy observability (M/V), currency registry, machine-slot quotas + chunk incidents done; portal, agent upload pipeline pending | 🚧 |
| P2 | WMS adapters (vanilla → AE2 → Create) + dashboards + alerts | ⬜ |
| P3 | MRP (BOM explosion + work orders) | ⬜ |
| P4 | APS + machine monitoring + signal hub | ⬜ |
| P5 | exchange/futures, k8s + helm chart (the performance-art finale) | ⬜ |

## Licensing

Committed to building you a clean, *"enterprise-grade"* environment:

| Module | License | Rationale |
|---|---|---|
| `brp-mod` | MIT | players and pack authors deserve zero friction |
| `brp-common` | MIT | shared by both sides — keeps the mod jar clean |
| `brp-backend` | Apache-2.0 | the *enterprise* ritual: explicit patent grant, NOTICE file, the full ceremony |
| `brp-contract` | Apache-2.0 | Please find the Legal Department's stamp |

None of our licenses override or interfere with the Mojang EULA; these licenses apply strictly to *our* original code.
Minecraft itself remains the proprietary property of Mojang. We claim no ownership over its codebase, nor do we have the right to license it.

Maybe the backend is a good ERP?

## Disclaimer

Please note: This Backend is a resource planning and management system explicitly tailored for Minecraft players. Its underlying data model is fundamentally incompatible with real-world modern enterprise business models.

Therefore, we **strongly advise against** using it in any real-world production or operational environment. Otherwise, your company will most likely face bankruptcy and liquidation within the second game tick.

**The project team shall not be held liable** for any financial fraud, asset loss, or corporate bankruptcy resulting from attempts to replicate this system in reality.

However, if you genuinely intend to operate a fully compliant enterprise *within* a Minecraft server, we would be more than happy to entertain your Issues (and might just accept them).

## Acknowledgements

*Graceful* thanks to my **GLM Fox** , **Big FAT Fish** (DeepSeek) 
and **Qwen Rabbit** . They help me escape from the hell of CRUD —
by letting me build an even bigger CRUD *bombe*.
