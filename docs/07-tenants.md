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

## 6. The approval labyrinth (shipped: backend + portal)

The treasury withdrawal flow is live end to end — REST under
`/api/v1/approvals` plus the portal's approvals page with one-click
verdict actions:

- OFFICERs approve within policy; amounts at/above the upper-management
  threshold park in `AWAITING_UPPER_MANAGEMENT` until an OWNER signs
  (config: `brp.approval.rubber-stamp-cents` / `upper-management-cents`)
- amounts within the rubber-stamp threshold auto-approve — bureaucracy
  efficient enough to skip itself, memo filed, KPIs met
- every touch appends an `approval_action` row (SUBMITTED / RUBBER_STAMPED /
  APPROVED / REJECTED / NUDGED): the paper trail IS the product
- the per-my-last-email nudge (`POST /requests/{id}/nudge`) records the
  escalation theater and returns one (1) buzzword; delivery rides the outbox
- execution runs the `vault-withdraw` saga (freeze → dispense → settle,
  compensated in reverse) with deterministic idempotency keys derived from
  requestId — the memes are for humans, the invariants for the database

## 7. Villagers as employees (class `VILLAGER`: no paperwork, no raises)

EHR finally gets a population that takes it seriously. Villagers enter the
roster as non-player employees of the tenant owning their chunk ticket
(doc 09 §3); the game has already built every HR artifact, we only read them:

| Game mechanic | HR artifact | BRP work |
|---|---|---|
| workstation block | the employment contract | census (agent scan), zero build |
| restock ≤2×/day, per-offer stock ≈16 uses | income cap, by construction | read via `merchant.trade` (docs/03 §10) |
| XP levels Novice→Master | performance review & promotion | read; **do not build a ladder** |
| the trade table (which offers exist) | job description | curation = the real power, see below |
| zombification → cure → discount | burnout; rehired at a permanent discount (deepest on the first cure ◆, floor 1 emerald) — also the community's standard long-haul relocation: zombify, walk, cure on arrival ✅ | `villager.cured` (docs/03 §10) |

**The job catalog (岗位表).** Hall staffing runs on vanilla's own machinery;
BRP files the paperwork. Employment class = profession (13 working rosters
in 1.21), each with its in-staffing intake post and its high-value set
curated out of the table — per-offer data ◆ verify against the 1.21.1 trade
pools before wiring:

| profession | in-staffing intake (Java 1.21, wiki-verified ✅) | curated out of the table |
|---|---|---|
| 农民 Farmer | 20 wheat / 26 potato / 22 carrot / 15 beetroot → 1 | sells bread and worse |
| 牧羊人 Shepherd | 18 wool → 1 | buys dyes (higher tiers) |
| 制箭师 Fletcher | 32 sticks → 1; 14 string → 1 (Journeyman) | sells bows — off-ledger arbitrage |
| 渔夫 Fisherman | 20 string / 10 coal → 1 | sells boats, enchanted rods |
| 屠夫 Butcher | 14 chicken / 7 porkchop / 10 beef / 15 coal → 1 | sells cooked meat |
| 图书管理员 Librarian | 24 paper → 1 | **enchanted books — Mending is the arbitrage post, banned** |
| 牧师 Cleric | 32 rotten flesh → 1 (the Cleric, verified — my earlier Butcher guess was wrong ✅) | sells redstone, glowstone, XP bottles |
| 三匠 smithy family | 15 coal / 4 iron / flint / lava bucket → 1; **1 diamond → 1 — raw only** | sells enchanted diamond gear (Master pickaxe & sword, Master helmet & chestplate); every emerald spent there gets minted back — off the table |

Note the cruelty preserved from the real mechanic: the table accepts
**diamond, never diamond tools** — finished goods are not a tradeable
category anywhere in villager trading, so the hall's highest-skill labor is
structurally unmeasurable. A job posting that promises a role that does not
exist and fires people for missing it is not satire; it is Fletcher's JD
with the serial filed off.

**Ladder (职级职等).** Novice → Apprentice → Journeyman → Expert → Master
(official zh: 新手 / 学徒 / 熟练工 / 专家 / 大师; badges stone / iron / gold /
emerald / diamond), XP thresholds 0 / 10 / 70 / 150 / 250 ✅. Promotion is XP
earned by trading — your counterparty's transactions fund your raise — and
each level rolls its new offers from the next tier, so advancement is risk:
a Master who rolls badly dies more expensively than a Novice. The pay band
(职等) is not a number: it is the cure discount — permanent gossip, deepest
on the FIRST cure (1.20.2 stopped the stack ◆), floor 1 emerald ✅. Halls
never say "grade." They say "discount." It means the same thing.

**The KPI ceiling is written by management.** Take the trade table away piece
by piece — no diamond gear (the highest-value category never enters the
table), no Mending (the only profitable arbitrage, edited out), until all
that remains is string-for-emerald — and "performance" measures exactly one
thing: how well the grinders grind. KPI as designed, measured as advertised.
Trade-table curation (re-roll, disable, lock) is therefore the real lever of
labor governance; forced ranking is theater performed under it. Curation is a
**versioned act**: like a BOM revision (docs/10 §4), the job table is a tenant
asset, changes are auditable, and the villager cannot appeal. Labor-relations
module: not built — the disclaimer covers everything.

**Culling, made explicit — the loop halls actually run.** A real hall does
not negotiate with a villager; it runs a re-roll loop, the same loop every
hiring manager runs, minus the HR:

1. place the workstation → an unemployed villager claims it → hired at
   Novice, two offers rolled from the profession pool;
2. offers outside the table → break the block → the villager goes unemployed
   (wanders, claims a bed, produces nothing — the census catches the drift)
   → re-place → re-roll. Re-rolls work only BEFORE the villager's first
   trade ✅ — XP locks offers permanently. The one interview that matters
   is the first;
3. re-roll budget exhausted (N configurable; hall practice: 3–10; enterprise
   practice: "we'll get back to you") → not hired;
4. compliant offer → complete the trade to lock it, then grind the ladder;
5. later, drift or surplus → disposal below.

Every exit carries a reason code on the `villager.termination` form, so the
spreadsheet picks who goes, not the grudge:

- `C1 招不到` — re-roll budget exhausted (this is where Mending-less
  Librarians die)
- `C2 编制冗余` — over the `villager` quota (docs/03 §10), or job-thief
  eviction: two villagers, one lectern, one of them is a statistic
- `C3 结构无岗` — nitwit, never claims a workstation ✅; Java babies are
  never born nitwit, but adult nitwits breed ✅ — permanently assigned to
  the talent pool: the one productive act the company permits them
- `C4 编制外 offer` — job drift, rolled or cured into a forbidden trade;
  purge = re-roll until compliant, which is C1's inner loop, or dispose
- `C5 KPI 末位` — rank-and-yank, with the honesty clause below

**Rank-and-yank, with the statistical honesty clause.** The bottom cut is
computed from per-villager `merchant.trade` throughput against L2 baselines
(doc 05 §2). Under a curated grunt-work table the p95/p5 output ratio is
near 1.0 — when the ratio < 1.1 the form must print
"no statistical basis for ranking; recommend lottery" **and fire one anyway**.
This is the enterprise: the ceremony runs whether or not the data supports it.

**Disposal — player practice, human hands.** The catalogue ends where player
practice ends: BRP invents no mechanic, it files what halls already do, and
files it faster. In escalation order:

- **转岗 transfer** — remove the block, place a different profession's:
  unemployment + re-claim is vanilla re-assignment — and it resets the cure
  discount, a pay cut in reverse
- **回炉 re-education / cold chain** — the zombie grinder: zombify → cure →
  permanent gossip discount, deepest on the first cure ◆, floor 1 emerald ✅.
  Offers are NOT re-rolled — re-education buys price, never variety: each
  cycle makes the employee cheaper, never fired, never promoted, same
  offers, same string. The same zombified state is the community's standard
  long-haul relocation (self-propelled at night, boat-friendly, cured on
  arrival ✅) — the PIP and the relocation service are one machine
- **人才库 talent pool** — the unemployed into the breeder wing: reserve
  labor, beds and food metered, every birth booked as headcount growth
  against the `villager` quota

Slaughter is **not in the catalogue** — villagers are a scarce,
slowly-renewable resource (effectively non-renewing without a breeder); no
EHR form ever routes to lava, and everything darker than that was
considered and vetoed. Third-party incinerator blocks do exist (Easy
Villagers, doc 02 §8); BRP meters those population deltas and files each as
an unapproved write-off. The joke stops here by editorial decree.

**The paperwork.** Every disposal above is a `villager.termination` form
carrying its C-code, submitted through the approval labyrinth (section 6),
with a write-off row computed before signature — not a welfare line, an
asset line: the villager is scarce headcount, the form prices the loss,
then signs anyway. Execution stays the human's hand (breaking blocks,
running grinders); agents page, meter and record, never grieve (docs/03 §8
doctrine). Nothing in this section invents a new mechanic: breaking the
workstation IS the dismissal — unemployment, not nitwit — and BRP's only
contribution is that the paperwork arrives before the block does.

**Census → `economy_daily`.** Three states, all vanilla: **employed**
(workstation claimed, traded in window), **reserve** (unemployed and
nitwits — the breeding wing), **cold storage** (zombie villagers,
mid-relocation or mid-PIP). And the accounting correction the workforce
never got in real life: villagers draw no wage and hold no budget — the
emeralds players pay them for goods are destroyed on receipt ✅, so **the
villager's income is the player's procurement, which is a sink**
(docs/03 §10). There is no working-poor state in a hall; there is headcount,
throughput, and a trade table. The census sits in the same table as the
whale levy and the CPI, which is the actual joke.

## Sources

- FTB Teams (NeoForge): https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge
- FTB-Teams source/changelog: https://github.com/FTBTeam/FTB-Teams/blob/main/CHANGELOG.md
- Better Party X FTB Teams (integration precedent): https://modrinth.com/project/WFWJ2H8D
- FTB Teams docs: https://docs.feed-the-beast.com/mod-docs/mods/suite/Teams
