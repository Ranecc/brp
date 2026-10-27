# BRP — Better Resource Plan

![enterprise readiness](https://img.shields.io/badge/enterprise%20readiness-TBD-important)
![seriousness](https://img.shields.io/badge/seriousness-0%25-red)
![emeralds lost](https://img.shields.io/badge/emeralds%20lost-0-brightgreen)
![compatibility](https://img.shields.io/badge/compatibility-best%20effort-orange)
![CI](https://github.com/Ranecc/brp/actions/workflows/ci.yml/badge.svg) <!-- the only real one -->

![build](https://img.shields.io/badge/build-maybe%20(somehow)-success)
![coverage](https://img.shields.io/badge/coverage-0%25%20(trust%20me%20bro)-yellow)
![code%20style](https://img.shields.io/badge/code%20style-AI%20hallucinated-blueviolet)
![dependencies](https://img.shields.io/badge/dependencies-Eventually%20Consistent-orange)
![PRs](https://img.shields.io/badge/PRs-welcome%20(but%20will%20break%20everything)-brightgreen)

> Delivering an *unadulterated*, E-n-t-e-r-p-r-i-s-e-grade Spring Boot experience in Minecraft XD
>
> It is my great honour to introduce: SaaS multi-tenant player guilds,
> guild SCM & EHR, order-to-cash & escrowed agreements,
> vanilla-redstone / mod-powered MES/APS, material WMS,
> server-wide financial markets & consolidated finance,
> and a server TPS trade market (๑•̀ㅂ•́)و✧
>
> [中文版](README.zh.md) · fellow coding agents: read [AGENTS.md](AGENTS.md) first, sit down and be relaxing.

## What it is

| Component | Form | Stack | Responsibility |
|---|---|---|---|
| **brp-backend** | Standalone Java service — **not a mod**, never touches the game JVM | Spring Boot 3.5 / Java 21 / PostgreSQL | Ledger, tenants/RBAC, WMS/MRP/APS/EHR/SCM, monetary policy, anomaly detection, web API |
| **brp-mod** | ONE universal jar (NeoForge 1.21.1), installed on both sides | Java + Gson (feign-style declarative client over JDK HttpClient) | On the dedicated server: the **Agent** (collect/probe/execute/disk-queued). On the player client: the **UI runtime** (read-only, proxied writes) |

> **DEPLOYMENT ADVISORY (for new tenants):** `brp-mod` is the only artifact
> cleared for the `mods/` directory. `brp-backend` is a standalone Java
> service, not a Minecraft mod; placing it in `mods/` will provision nothing
> except a "not a valid mod" finding from NeoForge. No refunds.

The backend deploys as a managed sidecar of the game server, or on an
independent server without modification. Deployment is flexible; the
bankruptcy risk is not.

One jar to rule them all — server operators and players install the identical
artifact; runtime behavior is resolved by the deployment environment
(docs/00-overview.md).

## Repository layout

```
brp/
├── docs/          # design docs (canonical English, .zh.md translations)
├── brp-contract/  # OpenAPI + event contract (single truth for both sides)
├── brp-common/    # zero-dependency shared kit: @Idempotent/@Cached/@Atomic/@Compensable
├── brp-backend/   # Spring Boot service (compiles; 56/56 tests green (+15 mod-side units), CI-enforced)
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
- [07-tenants](docs/07-tenants.md) — guild-specialized tenants, native + FTB Teams compat
- [08-bridge-api](docs/08-bridge-api.md) — stable surface for third-party bridge mods
- [09-shop-floor](docs/09-shop-floor.md) — P2: shop-floor terminals (dispatch/line/form), chunk governance, APS balancing, task board ⭐
- [10-backlog](docs/10-backlog.md) — reviewed backlog: 8 outside proposals graded, 7.5 accepted, stale premises corrected
- [10-business-map](docs/10-business-map.md) — the missing half of the ERP: order-to-cash spine, agreements, projects; what ships as a field, what is vetoed ⭐
- [11-kapital-checklist](docs/11-kapital-checklist.md) — Das Kapital, reviewed quarterly: execution coverage across all three volumes ⭐

## Quick start (backend)

```bash
cp .env.example .env            # set a real BRP_DB_PASS (compose enforces it)
docker compose up -d --build    # postgres + backend, ports bound to 127.0.0.1
mvn -pl brp-backend -am test    # 56/56 green or no commit: ledger, saga, approvals, quotas, chunk billing, currency edits, outbox, portal — go touch grass while it runs
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| P0 | repo + contracts + ledger core (idempotent, double-entry) + agent skeleton | ✅ this repo |
| P1 | teams, player sync, saga engine, approval labyrinth, TPS reports (real mspt), heartbeats, economy observability (M/V), currency registry, machine-slot quotas + chunk incidents, i18n copy, outbox + Discord wiring, portal console (overview/approvals/economy/quotas pages + verdict actions) done; portal auth/RBAC + agent runtime wiring pending | 🚧 |
| P2 | WMS adapters (vanilla → AE2 → Create) + inbound discrepancy & transfer orders + villager-mint metering events (docs/03 §10) + dashboards + alerts | ⬜ |
| P3 | MRP (sales-order spine → BOM explosion + work orders, completion costing) + agreements/escrow + mint-fee engine + villager EHR & ALICE census (docs/07 §7, docs/03 §2) + work-order turnaround report (docs/11 Vol. II) | ⬜ |
| P4 | APS + machine monitoring + downtime ledger + signal hub + projects & asset registry + Dept I/II balance alerts (the realization condition, engineered, docs/11 Vol. II) | ⬜ |
| P5 | exchange/futures, k8s + helm chart (the performance-art finale) + cross-dimension market entry (Nether/End, zero acquisition cost) + counter-cyclical consolidation playbooks (docs/11 Vol. III) | ⬜ |
| ∞ | the expropriators are expropriated (long-tail risk — external, uncontrollable, no budget allocated) | 🔮 unschedulable (docs/11) |

## Licensing

Committed to building you a clean, *"enterprise-grade"* environment:

| Module | License | Rationale |
|---|---|---|
| `brp-mod` | MIT | players and pack authors deserve zero friction |
| `brp-common` | MIT | shared by both sides — keeps the mod jar clean |
| `brp-backend` | Apache-2.0 | the *enterprise* ritual: explicit patent grant, NOTICE file, the full ceremony |
| `brp-contract` | Apache-2.0 | Please find the Legal Department's stamp |

### Legal pecking order

1. **The Mojang EULA** — supreme law of this world, non-negotiable,
   self-illuminating. Our licenses neither override nor interfere with it;
   frankly, we are not convinced it knows this repository exists.
2. Your server's rules — local ordinances, vary by jurisdiction (i.e. server).
3. **Our MIT / Apache-2.0** — a desk lamp. Governs *our* original code and
   nothing else. Minecraft itself remains the proprietary property of Mojang;
   we claim no ownership over its codebase, nor any right to license it
   (see item 1, the sun).

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
