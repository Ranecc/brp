# 03 — Economy System (microeconomics, engineered)

> [中文版](03-economy.zh.md)
>
> Goal: stop running server economies on admin vibes. Money becomes an
> observable, tunable system. Principle: **observe first, tune second,
> intervene last**. Every policy tool ships disabled, rolls out greylisted.

## 1. Currency & ledger

Single authoritative ledger (double-entry, doc 06); balances are materialized
`SUM(journal)` views. Multi-currency capable, one base currency at launch.
Account types: PLAYER / TEAM / SYSTEM_SINK / SYSTEM_FAUCET / ESCROW.

## 2. Money supply observability (full faucet/sink accounting)

Every unit of currency is born and dies with a journal entry, or the system
is not observable:

| Faucets | Sinks |
|---|---|
| quest/daily rewards, system shop sales, events | transaction tax, system shop purchases, teleport fees, auction commissions, fines, lottery |

Daily metrics materialized into `economy_daily`:
- **M** (money mass) = Σ balances
- **net emission** = faucet − sink
- **velocity V** = daily traded volume / M (Fisher's MV=PQ; collapsing V
  precedes either inflation or a confidence shock)
- **Gini coefficient** (>0.8 means whale hoarding creates artificial deflation)

## 3. CPI (server price index)

- **basket**: 30-50 items stratified into necessities / industrial
  commodities / luxuries; weights = traded value share over the trailing
  window, chained, rebalanced monthly
- **price sources**: trimmed-median player-to-player trades > auction clears
  > system shop quotes (indicative only)
- formula: chained geometric index (Törnqvist approximation). Manipulation
  guards: per-item daily weight caps, exclusion of accounts flagged by doc 05
- published daily with an annualized 7-day "inflation rate"; visible on
  in-world Create displays (doc 02 §4 — the satire becomes infrastructure)

## 4. Policy toolbox (parameterized, audited, one knob at a time)

| Tool | Turns | Treats |
|---|---|---|
| transaction tax / auction commission | sink strength | inflation, wash trading |
| system shop bid-ask spread | market-maker band | price anchoring; narrowing injects liquidity in deflation |
| daily faucet cap | issuance | hyperinflation |
| distance×CPI teleport fees | elastic sink | gentle reclamation |
| progressive large-balance levy (SYSTEM layer only) | hoarding cost | liquidity traps |
| open-market operations (central-bank counter-orders) | supply/demand | acute crises, targeted |

Discipline: one knob per change, greylist 10% of trades, observe CPI/V for a
week, then roll out or revert — **monetary policy gets change management and
rollback** (peak enterprise).

## 5. Crisis detection (rules first, models later)

| Crisis | Signal | Default response (alert-only) |
|---|---|---|
| hyperinflation | CPI > +2%/day for 7 days, faucet-heavy | alert + faucet cap recommendation |
| deflationary spiral | CPI < −0.5%/day for 14 days, V falling, sink > faucet | alert + spread narrowing / voucher recommendation |
| bank run | ESCROW withdrawal rate > 3× historical P99 | circuit-break: throttle + human review queue |
| liquidity crunch | order book depth below floor, spreads widening | alert |
| wash trading / manipulation | doc 05 graph detection (self-dealing cycles, spoofing) | flag + freeze for review |

Circuit-breaker levels: L1 alert → L2 rate-limit → L3 market pause (manual
unlock). Never intervene silently — every action is announced; transparency is
part of the policy.

## 6. Server physical resources as commodities ("selling TPS")

The joke that turns out to be the most useful module:

- **metering**: per-tenant chunk tickets, simulation-distance cost, scheduled
  tick / hopper throughput share (agent-collected; Carpet as a data source)
- **quotas**: `quota(tenant, resource, limit, used)` — chunk tickets, per-chunk
  entity caps
- **billing**: overage metered per-minute against the ledger (this is just a
  cloud provider's billing model)
- **trading (P4+)**: tenant-to-tenant quota transfer market. In inflationary
  times the central bank issues compute vouchers, not currency — **CPU time
  is the hard anchor of this server's gold standard** (laugh, then note it
  genuinely constrains virtual inflation)
- red lines: never real money; free tier must cover basic play

## 7. Phasing

P1 ledger + observability (M/V/net/CPI dailies) → P2 the three knobs (tax,
commission, spread) → P3 circuit breakers → P4 quota market → P5
exchange/futures (performance-art ceiling; options stay sealed forever, thanks).

## 8. Currency registry & legacy-plugin compatibility

- The ledger accepts **registered currencies only** (V8; BRC pre-registered,
  ACTIVE/RETIRED status). Owners mint currencies from the web console —
  `POST /api/v1/currencies`. Finance cannot invent money; that's the faucet's job.
- Legacy currency plugins (Vault-style plugin servers, mod economies) integrate
  as **adapter-mirrored external currencies**: an exchange account per adapter
  currency, balances synced one-way into BRP as an exchange rate against a
  registered BRP currency. External money never becomes ledger money without
  going through an exchange journal — mirrors don't dilute M.
- Machine placement is a **licensed activity** (quota resource `machine_slot`):
  the compute quota is the economy lever that caps mass production. Overruns
  and high-cost chunks (`chunk.performance` telemetry over threshold) NOTIFY
  admins via the outbox; enforcement stays a human choice by design.

## 9. P3 anti-monopoly: progressive levies, quota caps, and the Emerald-TPS bimetallic standard

Detection -> diagnosis -> prescription. The Gini monitor (section 2) above
0.8 activates the "anti-monopoly regime"; everything below arms only then.

**Progressive compute levy (the anti-monopoly main battery).** Overage
billing scales with the tenant's SHARE OF FLEET mspt, not flat amounts:
share <= 20%: 1.0x | <= 35%: 1.2x | <= 50%: 1.6x | > 50%: 2.5x, multiplied by
a wealth-Gini surcharge (up to 1.5x in full regime). Small guilds never feel
it; a farm cartel at half the server's tick budget pays 2.5x+ per overage
minute. Hard quota ceilings remain per-tier (squad/guild/alliance), and
sustained ceiling breaches walk the existing admin-alert ladder.

**Progressive wealth levy (the monetary auxiliary).** Brackets on balances:
<= 1M: 0% | <= 10M: 0.5%/week | > 10M: 1.5%/week, plus a hoarding surcharge
when velocity V < 0.5. Only charges while the regime is active — the levy is
a diagnosed medicine, not a permanent tax. All takings route to SYSTEM_SINK
and appear in net emission as negative (burned, not spent).

**The Emerald-TPS bimetallic standard.** A single anchor is fragile: emerald
farms inflate with villager hall engineering; compute gets cheaper with
hardware. So BRC is pegged against BOTH:

- **Emerald leg** — the MC-native commodity anchor. Central-bank vault
  (a physically guarded storage region) holds emerald reserves; a mint
  window redeems 1 emerald stack <=> X BRC.
- **TPS leg** — the hard physical anchor (section 6): quota-minutes are the
  commodity, Y BRC <=> Z quota-minutes.

The peg band absorbs +/-5% of anchor drift, then re-pegs by policy vote.
The engineered teaching moment: **Gresham's law events** — when one leg
turns cheap (say an emerald-printing farm), redemptions flood that leg while
the "good money" leg is hoarded. The Gresham detector (redemption flow
asymmetry > 95% on one leg over 7 days) is a first-class crisis signal in
section 5's table, with two prescriptions: re-peg, or float the weak leg
temporarily. Bimetallism collapsed in real history for exactly this reason;
ours collapses on schedule, with dashboards, and that is the product.

P3 deliverables: regime activation wired to Gini, levy brackets as policy
table rows, mint window + vault accounting (escrow-backed, append-only),
Gresham flow detector, re-peg playbooks.
