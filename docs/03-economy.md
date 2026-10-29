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

**Currency identity: one money, two names.** `code` (`BRC`) is the ledger's
internal primary key — journal rows reference it, it never changes.
`name`/`symbol` are the owner's display skin: portal, Create panels, chat and
every player-facing string render those and nothing else. Players trade the
displayed currency between themselves on the account rail — the primary means
of circulation, fully player-to-player transferable. Nobody perceives a
"layer underneath" because the underneath has no brand, exactly like nobody
knows their bank's reserve account: the code is plumbing, the money is one
thing. When a bearer form is registered (section 8) it is a ledger-signed
token of the SAME unit, moved between account and item 1:1 via the mint
window — deposit and withdrawal, not an exchange. No second currency, no
peg between equals, no Gresham on the rails.

## 2. Money supply observability (full faucet/sink accounting)

Every unit of currency is born and dies with a journal entry, or the system
is not observable:

| Faucets | Sinks |
|---|---|
| quest/daily rewards, system shop sales, events, mint-window redemptions (section 9) | transaction tax, system shop purchases, teleport fees, auction commissions, fines, lottery, mint fee (section 10) |

Daily metrics materialized into `economy_daily`:
- **M** (money mass) = Σ balances
- **net emission** = faucet − sink
- **velocity V** = daily traded volume / M (Fisher's MV=PQ; collapsing V
  precedes either inflation or a confidence shock)
- **Gini coefficient** (>0.8 means whale hoarding creates artificial deflation)
- **banked share** = Σ account balances / (accounts + outstanding bearer
  tokens issued by mint windows) — how much of the supply lives on rails we
  can see; a bearer form exists only if registered (section 8)
- **self-sufficiency line (ALICE)** — reference household Alice: cheapest
  basket of food, bed rent (chunk ticket amortized), transport (distance ×
  teleport fee), minimum `machine_slot` license, basic tool upkeep; cost
  materialized per dimension, chained monthly. Per-player classification on
  the section 9 shared weekly snapshot: self-sufficient / ALICE / struggling,
  where income = labor inflows net of FAUCET entries — subsidies never count
  as income, or the targeting poisons its own metric. The Gini watches the
  upper tail; Alice watches the floor: whale noise dominates Gini, ALICE share
  is robust there by construction
- **villager census** — three vanilla states per tenant, docs/07 §7:
  employed / reserve (unemployed & nitwit breeding wing) / cold storage
  (zombie villagers). Headcount accounting, not welfare accounting —
  villagers are a scarce, slowly-renewable resource

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
| licensed mint fee + redemption margin (section 10) | commodity-leg issuance | villager-hall minting drift |
| open-market operations (central-bank counter-orders) | supply/demand | acute crises, targeted |

Discipline: one knob per change, greylist 10% of trades, observe CPI/V for a
week, then roll out or revert — **monetary policy gets change management and
rollback** (peak enterprise).

## 5. Crisis detection (rules first, models later)

| Crisis | Signal | Default response (alert-only) |
|---|---|---|
| hyperinflation | CPI > +2%/day for 7 days, faucet-heavy | alert + faucet cap recommendation |
| deflationary spiral | CPI < −0.5%/day for 14 days, V falling, sink > faucet | alert + spread narrowing / voucher recommendation (means-tested against Alice, §2) |
| demand fragility | ALICE share rising week-over-week while employed headcount flat | alert + voucher targeting review — the floor thins before the price index notices (§2) |
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
- `PATCH /api/v1/currencies/{code}` edits the display skin only (`name`,
  `symbol`, `bearer_item_id`) — `code` is immutable because journals reference it.
  Renaming money's brand is branding, not monetary policy (section 1).
- **Bearer form is optional**: `bearer_item_id` (V11; nullable — null = pure
  account money). When set, mint windows dispense ledger-signed tokens of
  that item (persistent-data tag + serial = the signature). A marked token
  is money; a loose stack of the same material is a commodity — villager
  halls mint commodities, never money (section 10). The owner picks the
  placeholder: emeralds are the meme of record, gold nuggets work
  identically, the registry decides.
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

Detection -> diagnosis -> prescription. Everything below arms only when the
anti-monopoly regime is ACTIVE.

**Regime activation has HYSTERESIS** (half-open breaker heritage): wealth
Gini > 0.8 activates; Gini < 0.7 deactivates. Between 0.7 and 0.8 the regime
holds its previous state — Gini oscillating around a single threshold would
make the tax flap, and players feel flapping as twitching.

**One weekly snapshot feeds every mechanism.** Gini AND velocity V compute
from the same weekly snapshot job (V = nominal traded volume / mean held
balance, from TRANSFERRED/CRAFTED telemetry). When two mechanisms share a
number, they share the calculation — never recompute per-consumer, or the
books stop agreeing with themselves.

**Progressive compute levy — MARGINAL brackets, two separate tools.**
The levy is a TAX: priced in EMERALDS, paid into SYSTEM_SINK (real
liquidity contraction, one ledger event per tenant per minute, idempotency
key `tax:tenant:minute`; mspt share is metered by the agent's flow-meter
infrastructure and AGGREGATED per tenant-minute — sample streams never
touch the ledger). Marginal pricing per segment of fleet-mspt share:
0-20% @ 1.0x | 20-35% @ 1.2x | 35-50% @ 1.6x | >50% @ 2.5x. No cliff: the
first emerald over a bracket pays the next bracket's rate, like an income
tax, so running at 49.9% forever is not a strategy.

The >50% line is NOT a tax rate — it is a RED LINE. Sustained share above
50% (the operational definition of a farm cartel) escalates through the
existing admin-alert ladder as `BRP-0950` (with the alert family reserved
`BRP-09xx`). A tool cannot be both a levy and a death sentence: the tax
bills emeralds, the redline pages humans. QUOTAS remain the pure quantity
instrument (hard caps, machine licensing) — money tool and quantity tool
never twist into each other.

**Progressive wealth levy (monetary auxiliary, regime-gated).** Brackets on
balances: <= 1M: 0% | <= 10M: 0.5%/week | > 10M: 1.5%/week, plus a hoarding
surcharge when V < 0.5 (from the shared weekly snapshot). Takings route to
SYSTEM_SINK and appear in net emission as negative (burned, not spent).

**The Emerald-TPS bimetallic standard.** A single anchor is fragile: emerald
farms inflate with villager-hall engineering; compute gets cheaper with
hardware. (The first half of that sentence has stopped being weather —
section 10 licenses the hall.) BRC is pegged against BOTH:

- **Emerald leg** — the MC-native commodity anchor. Central-bank vault (a
  physically guarded storage region) holds emerald reserves; the mint window
  redeems 1 emerald stack <=> X BRC.
- **TPS leg** — the hard physical anchor (section 6): quota-minutes are the
  commodity, Y BRC <=> Z quota-minutes.

The peg band absorbs +/-5% drift; re-pegging beyond the band is a POLICY
VOTE that rides the EXISTING approval workflow — no third governance
channel for money matters, RFC P3 grants no exemptions here.

**Gresham detector.** Legs are defined by PAYMENT RAIL (emerald-priced vs
quota-priced redemption flows through ESCROW), never by item movement — and
the two rails have different NATURES: the emerald rail is the CONSUMER
currency (the something-to-spend-it-on leg: netherite, blocks, whatever the
economy actually buys), while the quota rail is the INDUSTRIAL currency
(means of production — which players mostly hold to mint emeralds with).
One rail dominating the volume split is therefore NOT a crisis; it is
Tuesday. The signal is BEHAVIORAL, measured against each rail's own moving
baseline: current 7-day flow per leg vs its 4-week moving average. The
alarm fires when the industrial rail's throughput collapses against ITS
OWN baseline while the consumer rail holds or grows — spend the bad coin,
hoard the good one, the actual Gresham mechanism. Absolute share thresholds
were tried and retired here: they fired every week, because of course they
did. Cold start: fewer than the baseline weeks of history, the detector
keeps silent. Prescription unchanged: re-peg, or float the weak leg
temporarily.

**Mint ordering (vault cannot drift from ledger).** Every mint is FAUCET
ledger event FIRST, item dispense SECOND — the @Compensable saga pattern
exists precisely for "books moved, goods didn't" compensation ordering.

P3 deliverables: hysteresis-wired regime, shared weekly snapshot job,
marginal levy engine (emerald-denominated, tenant-minute aggregation),
BRP-09xx alert family, mint window saga, Gresham detector, re-peg playbook.

## 10. The villager hall is a mint — license it, don't fight it

Villager trading halls convert crops into emeralds at game-law rates. Left
unobserved, the commodity leg inflates with whatever redstone engineering
players invent — section 9 states this as a fragility. Prohibition is off the
table: cancelling trades violates "never intervene silently", and it turns a
monetary problem into a policing problem. The enterprise answer absorbs the
hall instead — quantity-capped, money-taxed, fully metered.
**"Villager confinement is a licensed activity"** (the machine-placement
sentence, with the entity pointed at something that gets confined).

**Metering — two new events (contract rows land with the agent mixin):**

- `merchant.trade` (TELEMETRY) — one per completed villager trade. Hook:
  mixin around `MerchantResultSlot#onTake` ◆ (the player taking the result
  slot IS the trade; no dedicated NeoForge post-trade event exists ◆ —
  verify against 1.21.1). Payload: villager entity id, profession, offer,
  in/out item + count, chunk key. Same collection job the machine probe
  does for AE2/Create (doc 02).
- `villager.cured` (TELEMETRY) — NeoForge has NO dedicated cure event; hook
  `LivingConversionEvent.Post` (ZombieVillager → Villager) ✅. Cures preserve
  profession AND offers — they change only the gossip discount: permanent,
  deepest on the FIRST cure (1.20.2 stopped the stack ◆). Cured librarians
  are an arbitrage asset; cured intake clerks are unchanged. Wire this event
  for doc 05's discount forensics, not for throughput.

**Attribution.** Hall chunk → tenant via the chunk-ticket registry (doc 09
§3). Trades in ticketed space bill the ticket holder. An unattributed minting
villager is an incident, not a crime — the same human-choice page as machine
overruns (section 8). Admins' own halls book to an explicit `SYSTEM_MINT`
tenant, or section 2's net emission quietly goes crooked. Wandering traders
carry up to two purchasing trades (hay bale / baked potato class) alongside
their sell stock ✅ — not a pure drain: route their rare mints to
`SYSTEM_MINT` too.

**The game already built the speed limiter; we only read it.** Per offer: a
stock of uses per restock (≈16 on buy trades), restock up to twice a day at
the workstation ✅ — mint velocity = uses × restocks, and `merchant.trade`
counts it. Offers are rolled ONCE: re-roll works only before a villager's
first trade, after which offers lock permanently ✅. And the hall buys raw
inputs only — finished goods are not a purchasable category anywhere in the
trade tables ✅, so daily mint output is bounded by game data, not by player
cleverness.

**Two tools, never twisted into each other** (the section 9 constitution,
applied):

- quantity tool — quota resource `villager` in the existing buckets
  (0 = unlimited licensing; `quota.exceeded` pages admins, humans decide);
- money tool — **mint fee**: per-tenant daily aggregate over `merchant.trade`
  output, ONE ledger event per tenant per DAY (idempotency key
  `tax:tenant:mint:day`). Trade-by-trade is a sampling stream and sampling
  streams never touch the ledger — the section 9 lesson, reused verbatim.
  Emerald-denominated into SYSTEM_SINK like the compute levy: a real
  contraction of commodity-leg liquidity.

**Why this closes the economist's loop.** The emission equation was
`dM/dt = I + m − burn` with m (private minting) the one term neither
observable nor controllable. Licensing makes m a measured policy term:
m = θ·O_hall, where O_hall is counted by the events and θ is the mint-window
redemption margin. "Villager-hall engineering" stops being weather and becomes
a tariff schedule with a meter. Wörgl (1932) got the demurrage half of this
right with stamp scrip — it never got to meter the printer. We meter the
printer.

**The mint's workforce is on the books too.** `merchant.trade` carries the
villager id; employment states, trade-table job descriptions, culling codes
and the census live in doc 07 §7. Accounting correction: villagers draw no
wage — the emeralds players pay them for goods are destroyed on receipt ✅.
The hall is a two-way desk: intake trades MINT, output trades BURN, and the
"employee income" line in any report is a sink, not a payroll.

Deliverables: mixin + two event rows (P2 batch, same shipment as the WMS
adapters), `villager` quota resource, `SYSTEM_MINT` tenant, mint-fee
aggregation engine (P3, bolted next to the levy engine), redemption-margin θ
knob in section 4.

## 11. Research note: the circulation steady state

The intended circuit: the server prints at the top (welfare, quest rewards,
system-shop purchases), the auction burns at the bottom (mspt overage, compute
levy, mint fee, teleport fees). Every unit either circulates or dies in the
ledger. If burn rises monotonically with circulating M — the auction clears at
higher bids when the street is flush — the system is a float valve: the
equilibrium where faucet = sink is self-stabilizing.

Open properties, tracked as research, not engineering (no thresholds here):

- hoarding flattens the burn slope — the section 9 wealth levy is the
  demurrage spring that pushes M back onto the rail, its V < 0.5 surcharge is
  elasticity insurance;
- speculators who time the auction floor produce oscillations around the
  fixed point instead of convergence; oscillating non-equilibrium steady
  states are still steady states. Canonical failure mode, verbatim:
  "Bimetallism: ours collapses on schedule, with dashboards, and that is the
  product."
- whale-dominated floors damp elasticity; where the model is uncertain the
  section 5 breakers stay alert-only.

The portal econ page eventually carries the line chart and exactly one joke.
