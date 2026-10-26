# 07 — Tenant Model: Guilds, Native & Compat

> [中文版](07-tenants.zh.md)
>
> Researched 2026-10. Tenant = team/guild; the model is specialized for
> player guilds (treasury, quotas, shared vaults) rather than generic orgs.

## 1. Roles & shape

- Roles: `OWNER` (1, treasury control, dissolve) · `OFFICER` (invite, approve
  withdrawals per policy) · `MEMBER`
- A native team creation provisions: team row + OWNER member + **treasury
  ledger account** (`TEAM` type, tenant = teamId) in one atomic idempotent op
- Player can hold membership in N teams; each team maps to: treasury account,
  quota bucket (doc 03 §6), vault storage scope (AE2 cell binding, doc 02 §2)

## 2. Native vs compat mode (exactly one active)

| | NATIVE | COMPAT (e.g. FTBTEAMS) |
|---|---|---|
| Authority | BRP backend | game-side mod |
| Create/invite/kick | API (web portal + terminal) | in the other mod, as usual |
| Roster flow | backend → game (P1 UI) | agent pulls → `POST /teams/sync` (read-only mirror) |
| Native endpoints | enabled | **refuse to touch compat-sourced rows** (tested) |
| Team id | server-chosen | the source mod's stable UUID |

Sync protocol: wholesale batch, idempotent on `batchId`, atomic per batch;
roster replace keeps `joined_at` for continuing members, records leaves.

## 3. FTB Teams compat (researched)

- FTB Teams is the dominant team mod (224M+ downloads; CurseForge), active
  1.21.1 NeoForge builds; API consumed via `maven.ftb.dev.org`
  (`dev.ftb:ftb-teams-neoforge`)
- Entry points (◆ verify at wiring): `FTBTeamsAPI.api().getManager()` →
  `getTeamByID/getTeamByPlayer` → `PartyTeam.getMembers()` → member uuid/role;
  party-creation API changed recently (changelog) — read source, not old
  tutorials
- Existence proof of third-party API integration: "Better Party X FTB Teams"
  (Modrinth) syncs members/names/leaders exactly like our adapter will
- Mapping: FTB party UUID = backend team id; owner/officer→OFFICER; solo
  player-teams skipped; agent never mutates FTB state in compat mode

## 4. Why not also LuckPerms groups as teams?

LuckPerms = permission model (per-server ops concern), FTB/BRP = social unit
(persistent, cross-feature). We sync LP groups into RBAC (doc 01 §6), NOT into
tenants — mixing them is how "enterprise" systems earn their reputation.

## 5. Auth boundary (user decision: delegate to the MC server)

BRP runs **no player authentication of its own**: in-game identity = the MC
server's session (online-mode verification; the agent only ever acts for
authenticated `ServerPlayer`s). The web portal binds via an in-game issued
code (short-lived, player must be online) — the game server is the only
identity authority; BRP is a relying party. Agent↔backend uses instance
provisioning (HMAC in P1), which is machine auth, not player auth.

## Sources

- FTB Teams (NeoForge): https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge
- FTB-Teams source/changelog: https://github.com/FTBTeam/FTB-Teams/blob/main/CHANGELOG.md
- Better Party X FTB Teams (integration precedent): https://modrinth.com/project/WFWJ2H8D
- FTB Teams docs: https://docs.feed-the-beast.com/mod-docs/mods/suite/Teams
