# AGENTS.md

House rules for our robot colleagues (GLM Fox, Deepseek Fishs/Whales/Orcas,
Qwen Rabbit, GPT Dragon, Creeper (?), crabs — all welcome). Humans are allowed
to read this too, but the meme budget is earmarked.

## The vibe

- **Be relaxing.** This is a Minecraft mod that cosplays as an Ent ERP system.
  The absurdity is load-bearing: do not "fix" the joke, ship it.
- Puns and memes are welcome in docs, comments and commit messages (English
  ones — see red line 8). They are **not** welcome in ledger invariants,
  migrations or security code — those stay aggressively boring.
- Memes are earned, not scheduled: if the joke is right there, take it; if
  it isn't, ship it straight. No forced bits, no dead memes. A joke that
  needs a footnote has already failed its code review.
- We are already over-engineered *on purpose*. Adding more ceremony is only
  funny if it compiles and the tests stay green. Which brings us to:
- **The meme boundary**: user-facing copy (messages[_xx].properties, error
  text, docs, method names) may judge you freely; business BEHAVIOR stays
  compliant — no jokes in state machines, thresholds, or money math. The
  i18n bundle is canonical dark humor; every new endpoint ships keyed copy
  in `messages.properties` + `messages_en/_zh.properties`.

## Red lines (hard, no exceptions)

1. **Spring never runs inside the game JVM.** Backend is a standalone process
   (sidecar child process or compose). No classloader experiments.
2. **Never trust the game client.** Clients read and submit; only the
   server-side agent writes; backend still range-checks agent reports.
3. **Never do network IO on a game tick thread.** Game threads take lock-free
   snapshots; HTTP/serialization live on the agent IO pool.
4. **Money paths use the ledger's same-transaction idempotency row**
   (`LedgerServiceImpl`), never `@Idempotent` (post-hoc, has a crash window).
   `@Idempotent` is for syncs/enqueues/read-model writes only. Money paths
   are no-joke zones: if an emerald test turns red, everything stops.
5. **`journal_entries` is append-only.** No UPDATE/DELETE in code; revoke at
   the DB role level in production.
6. **Player auth belongs to the Minecraft server.** BRP does no player
   authentication; portal binding = in-game issued code. Agent↔backend is
   machine auth (HMAC in P1).
7. **No real-money features. Ever.** (P5 derivatives are play-money satire.)
8. **English only** in code, comments, commits and canonical docs; Chinese
   only as `.zh.md` translations. An English doc edit without its `.zh.md`
   mirror is only half a PR.
9. **Tests green before every commit**: `mvn -pl brp-backend -am test`
   (172/172 backend + 28 mod units today — update when it lies; the CI workflow remains the
   count's single source of truth). New domain = tests for its idempotency +
   failure paths.
10. **Deploy posture:** host ports bind 127.0.0.1; credentials via env
    (`BRP_DB_PASS` required — compose refuses to start without it); weak
    defaults never reach compose files.
11. The loader is locked to **NeoForge 1.21.1**. Don't propose Fabric. We've
    been through this (docs/02); the answer is still NeoForge.
12. **Private infrastructure identifiers never enter tracked text** —
    domains, IPs, ports, network topology, account names on private
    forges: none of it in files, comments, docs, commit messages or
    branch names. They are not flavor text. Remote addresses live in
    local git config; a repo heading for the public internet says "the
    private mirror" and means it. If one slips in anyway, the history
    gets rewritten and the remotes get force-pushed, not apologized to.
    (`git grep` before committing costs seconds; an unredacted history
    costs the force-push.)

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

## Workflow

1. Research → decision → **docs first** (both languages, confidence marks:
   ✅ verified / ◆ verify-against-javadoc / ❓ open). Docs are part of the
   change, not its aftermath: behavior moves → the matching `docs/NN-*` pair
   and README indexes move in the SAME commit (see CONTRIBUTING rule 0).
2. Contract next: `brp-contract/` changes land in the same commit as the code.
3. Code → tests → one logical commit. Verify before committing.
4. Uncertain about a third-party API? Mark it `◆` + TODO. Never guess silently.

## Satire-doc doctrine (learned the hard way on docs/11, v1→v6)

Satire docs go through full rewrites, each absorbing an external review.
The rules that survived every round:

- **the mask never comes off.** One voice, end to end. Corrections land
  in-voice, never as audit notes, version history or authorial "we"
  (the diff is the changelog). If the frame breaks, absorb the critique
  as content, not commentary.
- **density discipline.** Most items read like boring corporate filler;
  three or four load-bearing jokes stay loud. A punchline per line is
  wisecrack inflation.
- **cite the source concept the way the company would cite a vendor** —
  naming the manual's term upgrades euphemism into verdict, in-register.
- **parallel editors are live.** Re-read any file before editing it;
  someone (human or another agent) has probably touched it since your
  last read.

```bash
cp .env.example .env            # set a real BRP_DB_PASS (compose enforces it)
docker compose up -d --build    # postgres + backend, ports bound to 127.0.0.1
mvn -pl brp-backend -am test    # the sacred ritual
```

## Backend package layout (global layer-first — the ceremony IS the spec)

```
dev.brp
├── controller/   transport only: @Valid in → service call → DTO out
├── service/      domain interfaces
├── service/impl/ business logic + cross-cutting annotations live HERE
├── mapper/       MapStruct (componentModel=spring), Entity → DTO only
├── dto/          request/response records (container records ok, see PlayerDtos)
├── entity/       JPA entities; never returned from controllers
├── repository/   Spring Data interfaces
├── aspect/       the four cross-cutting aspects + SpelKeys
├── saga/         SagaEngine + SagaRecovery (orchestration, not a domain)
├── common/       TenantContext, exceptions, ApiExceptionHandler
└── contract/     shared wire DTOs mirrored in brp-contract
```

Ceremony discipline (per domain: Controller + Service + ServiceImpl + Mapper +
DTOs + Entities + Repositories):
- Controllers are ~5-line delegations; business logic in a controller is a bug.
- Entities never cross the service boundary; MapStruct does all Entity→DTO
  conversion (no hand-rolled `toDto` statics, no `Map.of` assembly upstairs).
- Annotation order (outer→inner): `@Cached` → `@Idempotent` → `@Atomic` →
  `@Compensable`, placed on ServiceImpl methods.
- Constructor injection only; records for DTOs; no Lombok, no field injection.
- DDL only via Flyway, portable SQL (H2 PG-mode + PostgreSQL), `ddl-auto: none`.
- Multi-table writes default to `@Atomic`; cross-system flows register a
  `SagaEngine` definition with named compensations (see SagaEngineTest).

## Mod (brp-mod) architecture

- `acl/`, `spi/`, `queue/`, `store/`, `client/`, `supervisor/` are **pure
  Java: zero Minecraft/mod imports**. Game access lives only in
  `platform/neoforge/` and `adapters/` (soft deps, `ModList`-guarded).
- Loader/version migrations replace adapters, never core. SPI is additive-only.
- Agent local persistence = embedded SQLite (`LocalStore` port); Caffeine
  behind `CacheFront` so a package relocation touches one file.
- Mod consumes `brp-common` from mavenLocal (`mvn -pl brp-common install`).
- Single-file distribution: the backend fat jar embeds at
  `resources/backend/brp-backend.jar`; the supervisor extracts it
  (sha256-checked) — distribution artifact, not runtime topology.

## Commit style

Short, present tense, lightly unhinged, English. Example:
`ledger: refuse to lose a single emerald (idempotency hardening)`.

## Remotes

- **gogs** — the private home mirror; always push here. Its address lives
  in local git config (`git remote -v`) and nowhere in tracked text.
- **origin** (GitHub, `Ranecc/brp`) — the **server** is private, the code
  is not. Push freely, no per-push ceremony. Force-push only after a
  rebase, only with `--force-with-lease`, and only after verifying the
  old tip's tree matches the local rebased base (`git diff` empty).
- CI lives on origin. It is the second opinion; the local build is the
  verdict of record (see brp-mod/README.md, mirror notes).

## When in doubt

Read [docs/00-overview.md](docs/00-overview.md), touch grass, then ask a human.
