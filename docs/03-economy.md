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
