# AGENTS.md

House rules for our robot colleagues (GLM Fox, whales, orcas, crabs — all welcome).
Humans are allowed to read this too, but the meme budget is earmarked.

## The vibe

- **Be relaxing.** This is a Minecraft mod that cosplays as an ERP system.
  The absurdity is load-bearing: do not "fix" the joke, ship it.
- Puns and memes are welcome in docs, comments and commit messages.
  They are **not** welcome in ledger invariants, migrations or security code —
  those stay aggressively boring.
- We are already over-engineered *on purpose*. Adding more ceremony is only
  funny if it compiles and the tests stay green.

## What this repo is

BRP ("Better Resource Plan") is ERP-for-Minecraft. The domain vocabulary is
literal — use these terms exactly when writing code, contracts or docs:

- **Tenant** = a player guild. Multi-tenant SaaS: roles `OWNER/OFFICER/MEMBER`,
  per-team treasury ledger account, quota buckets, shared vault scope
  (docs/07-tenants.md).
- **EHR / roster** = players as employees: profiles, membership, role sync
  from the game side (native or FTB Teams compat) — docs/07-tenants.md, P1.
- **SCM / WMS** = supply chain & warehousing: chests, AE2 networks and team
  vaults are warehouses; procurement → inventory → logistics
  (docs/02-integrations.md, P2).
- **MES / APS** = production: vanilla redstone farms and mod power are
  capacity; flow meters → quotas → work orders → scheduling
  (docs/04-technical-control.md, P3/P4).
- **Finance** = the double-entry ledger in brp-backend is the single source of
  truth; no balance exists outside it (docs/03-economy.md, docs/06-transactions.md).
- **Financial market & consolidation** = server-wide exchange/futures layered
  on the ledger; TPS itself is a commodity (docs/03-economy.md, roadmap P5).
- **Probe** = management blocks placed in the world; they are the sensors and
  actuators the Agent reads and drives.

| Path | What lives there | Rules of engagement |
|---|---|---|
| `brp-contract/` | OpenAPI + event contracts | Single truth for both sides; change the contract BEFORE the code |
| `brp-common/` | `@Idempotent/@Cached/@Atomic/@Compensable` kit | **Zero third-party dependencies, forever** |
| `brp-backend/` | Spring Boot 3.5 / Java 21 / PostgreSQL | Never runs inside the game JVM |
| `brp-mod/` | universal single jar (NeoForge 1.21.1) | Same jar on client & server; behavior decided by dist |
| `docs/` | design docs | English is canonical; `.zh.md` mirrors it — update both |

## Ground rules

1. The loader is locked to **NeoForge 1.21.1**. Don't propose Fabric. We've been
   through this (docs/02); the answer is still NeoForge.
2. Money paths (ledger, idempotency, saga) are no-joke zones. If a test about
   emeralds turns red, everything stops until it's green.
3. Keep tests green: `mvn -pl brp-backend -am test` (currently 14/14).
4. Contract first, then backend, then mod. Don't invent endpoints.
5. Humor budget may be spent freely on README/commit messages. Zero on cryptography.
6. Docs are bilingual by convention — an English edit without its `.zh.md`
   mirror is only half a PR.

## Commands

```bash
cp .env.example .env            # set a real BRP_DB_PASS (compose enforces it)
docker compose up -d --build    # postgres + backend, ports bound to 127.0.0.1
mvn -pl brp-backend -am test    # the sacred ritual
```

## Commit style

Short, present tense, lightly unhinged. Example:
`ledger: refuse to lose a single emerald (idempotency hardening)`.

## When in doubt

Read [docs/00-overview.md](docs/00-overview.md), touch grass, then ask a human.
