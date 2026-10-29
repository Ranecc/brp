# P4 — The Load Desk: compute market, demand response, credit, bonds

> Status: **design approved, implementation in progress** · Phase: P4
> Predecessor: docs/12 (P3 monetary machinery) · Successor: P5 (exchange, satire ceiling)
>
> P3 answered *who pays for the ticks*. P4 answers *when the ticks get used*.
> The server has a diurnal pulse — full at 21:00, idle at 06:00 — and every
> forced chunk bills the same flat rate regardless. That is the pricing error
> P4 fixes, with the three instruments every grid operator eventually reaches
> for: time-of-use tariffs, demand response, and debt. Plus the satire crown
> jewel: bonds whose coupon is settled against **actual ticks served**.

## 1. Field research: what a Minecraft server actually spends ticks on

Everything below is from minecraft.wiki (`/w/Tick`, `/w/Chunk_loading`,
`/w/Mob_spawning`), read October 2026, and each fact maps to a line item in
§2–§5. No folklore.

| Wiki fact | Number | Economic consequence |
|---|---|---|
| Tick budget | 50 ms = 20 TPS; MSPT is the realized cost | MSPT/50 is a **capacity utilization** in [0,∞); the whole P4 price apparatus keys off it |
| Forced chunk ticket | level 31, entity-ticking, **no timeout**, persists across saves | A forced chunk is a *perpetual* compute claim — exactly what managed_chunks bill (P2). Vanilla never reclaims it; our billing must price it |
| Dimension idle timeout | 300 ticks (15 s) of no activity stops entity ticking | Vanilla already prices idleness down to zero. BRP's chunk fees are the inverse: players pay to keep what vanilla would drop |
| Global mob cap | `mobCap × chunks ÷ 289`, hostile 70 at full; chunks shared by players count once | Spreading players across the map **raises** the cap → more entities → more MSPT. A formal tragedy of the commons, printed in the wiki |
| Per-player spawn range | spawning needs a player within 128 blocks; immediate despawn beyond 128 | Player presence is the scarce trigger for entity load — hence census counts entities, not just chunks |
| Random ticks | 3 random block picks per 16³ section per chunk tick (avg 68.27 s per block); `randomTickSpeed` multiplies | Crop-farm chunks are cheap; mob-farm and redstone chunks are not. Uniform pricing over-weights the cheap ones — tariff multipliers fix it |
| Scheduled ticks ceiling | 65,536 per game tick (block first, fluid after) | A batch farm can slam the ceiling at 21:00 exactly when everyone logs in — the canonical **peak** DR exists for |
| Autosave | every 6,000 ticks (5 min) | Periodic MSPT spike with a known period; the forecaster's EWMA must not chase it (§3 smoothing) |
| Hoppers | continuously scan for items above; covered hoppers stop | The canonical idle drain; OEE's "jammed/idle" row (§6) is measured, not vibes |
| Block entities | tick after entities, every entity-ticking chunk | The census (P2-14) counts them per namespace — that count is the load model's best predictor and now rides the heartbeat |

Design rule extracted from the table: **players are the load generator**.
Tickets come from players, spawn caps scale with player-spread, DR windows
exist because players log in on a schedule. So the load plane samples players,
entities, and chunks *together* per minute — the forecaster is meaningless
with any one of them missing.

## 2. Load telemetry plane (slice A)

**V19 `load_samples`** — one row per UTC minute:

```
load_samples(bucket_minute PK, sample_count, mspt_avg, mspt_max,
             player_max, entity_max, chunk_max, forced_max)
```

Heartbeats already stream `mspt/tps/backlog` (P1 wiring); the census stage now
adds `players`, `entities`, `chunks`, `forced` (from the agent's registered
set — no new game-thread IO; `getPlayerCount()`, per-level entity count,
`getLoadedChunksCount()`). `TelemetryServiceImpl` upserts the minute bucket:
running average for mspt_avg, max for the rest.

**`load_profile` (V19)** — durable minute-of-day seasonality:
`minute_of_day PK, ewma_mspt, sample_days`. Updated once per day at 00:1x by a
scheduled job: `ewma = 0.95·ewma + 0.05·today` per minute (α = 0.05,
`brp.load.profile-alpha`). Cold start (sample_days < 3) → forecaster says so
instead of inventing numbers — same honesty rule as Gresham's empty baseline.

**`LoadForecastService`** — `forecast(hours)` returns the next N hourly
buckets:

```
predicted(m) = clamp01utilization( profile(m) + 0.5 · (live − profile(now)) )
```

- `live` = 30-minute EWMA of mspt_avg (α = 0.3, `brp.load.live-alpha`);
- the deviation term is damped 0.5 (`brp.load.live-damp`) — the profile is the
  prior, the present is the correction, and autosave spikes are transient by
  construction;
- output also classifies each hour **VALLEY / SHOULDER / PEAK** from the
  tariff (§4) and a boolean `schedulable` = predicted MSPT ≤ budget (§5).

`GET /api/v1/monetary/load/forecast` and `.../load/samples?hours=` expose it.
Portal draws the curve (same SVG pattern as CPI on monetary.html).

## 3. Utilization & the DR budget

`brp.dr.mspt-budget` (default **40**, of the 50 ms budget): above it the
scheduler refuses to award new windows, running windows get shed (§5), and
the forecast marks hours unschedulable. Utilization itself is published on the
forecast response: `utilization = mspt / 50`. The 0950 red line (P3) stays
sovereign — paging is the regime desk's job; DR is the market's job. One
measures harm, the other prices it.

## 4. Time-of-use tariff (slice B)

**V19 `tariff_windows`** — versioned TOU table:

```
tariff_windows(id PK, version, day_of_week -1..6 (-1=all), start_minute,
               end_minute, multiplier_pct, label)   -- label: VALLEY/SHOULDER/PEAK
```

`TariffService.multiplierAt(dateTime)` resolves highest-version matching row,
defaulting to 100%. Seeded from the chunk billing config that already exists
(`brp.chunk.billing.peak-start-hour 19 / peak-end-hour 23 /
peak-surcharge-percent` → one PEAK window at version 0). ChunkServiceImpl then
**consults the tariff table instead of its private fields** — the R-3b peak
surcharge becomes the first customer of the general tariff, one source of
truth, PeakPricingTest stays green and gains table-driven cases. Windows may
be negative (valley discount, `multiplier_pct 60`) — 平峰填谷 is literally a
row in a table now.

Billing identity everywhere: `price = base × multiplier(resolved minute)`,
and the invoice line records `tariff_version` + resolved multiplier. No
retroactive repricing — version stamped at posting time, like every real
utility's terms-of-service footnote.

## 5. Demand response — 平峰填谷 (slice B)

**V19 `deferred_jobs`**:

```
deferred_jobs(id PK, tenant_id, idem_key UNIQUE, resource, quantity,
              est_minutes, earliest, deadline, status, awarded_start,
              awarded_minutes, shed_count, created_at)
status: FILED → AWARDED → RUNNING → DONE
                    ↘ SHED (back to AWARDED, deadline extended)
```

**`DemandResponseService`**:

- **Filing** (`POST /tenants/{id}/deferred-jobs`, idem `dr:{tenant}:{key}`):
  tenant declares deferrable work — "run the mob farm for 90 min sometime
  before Saturday". `est_minutes × base-emeralds-per-minute` (same
  `brp.levy.compute.base-emeralds-per-minute` as the compute levy — one price
  of compute) is the cost base.
- **Awarding** (scheduler tick, `brp.dr.award-every`): for each FILED job,
  candidate windows = hours before `deadline` where forecast says
  `schedulable`; sort windows by `(tariff multiplier, predicted mspt)` asc —
  cheapest first, that's the *填谷*; greedily award contiguous minutes,
  fitting around already-awarded windows of the same tenant. Award bills a
  **reservation** at the awarded window's multiplier (idem
  `dr-reserve:{jobId}:{windowSeq}`); the reservation is the revenue. Un-awarded
  at deadline = job expires FILED→EXPIRED, no charge — a promise to schedule is
  not a bill.
- **Dispatch**: awarded window OPEN/CLOSE rides the existing signal plane
  (`dr.window` events, acked like every signal; idempotent by jobId+seq). The
  mod's SignalDesk routes it to the governor feed: a tagged production line may
  start only inside its window. Outside the window the governor holds the line
  closed — the machine literally cannot ignore the tariff.
- **Shed**: live mspt > budget while windows run → OPEN windows CLOSE early
  (DR sheds *first* — it is by definition the interruptible load; the unpaid-
  chunk enforcement from P2 is firmer than DR, credit cutoff firmer still),
  `shed_count++`, deadline += shed duration, window re-awarded. Max
  `brp.dr.max-sheds` (2) then the job cancels with reservation refunded.
  Shed events land in the downtime ledger (§7) as `DR_SHED` — OEE sees the
  grid operator's hand.

This is 平峰填谷 in one sentence: valley multipliers make 06:00 the cheapest
hours, the scheduler walks every deferrable farm into them, and 21:00's
scheduled-tick ceiling stops being a mob-farm traffic jam.

## 6. Credit & debt — 债务 (slice C)

**V19 `credit_lines` / `credit_events`**:

```
credit_lines(tenant PK, limit_cents, drawn_cents, accrued_interest_cents,
             status, status_since, annual_rate_pct, updated_at)
  status: GOOD → WATCH (≥80% utilization) → RESTRICTED (100%) → DEFAULTED (grace out)
credit_events(id PK, tenant, kind, amount_cents, memo, created_at)
  kind: DRAW|REPAY|INTEREST|LIMIT_SET|STATUS
```

- **Limit**: admin-set, else auto = `limit-factor × trailing-30d BRC income`
  recomputed weekly (floor `brp.credit.limit-floor-cents`). Debt in BRC —
  fees are BRC, so smoothing chunk-fee spikes needs BRC credit, not quota.
- **Draw**: implicit. When a posting would overdraw the *cash* account, the
  ledger refuses (P0 invariant — no silent mint); the **payer** may then
  settle from credit: draw journal `credit-draw:{txn}` (SYSTEM lender leg),
  cash completes. Drawn ≤ limit or 402 `credit.limit.exceeded`. The ledger's
  zero-mint invariant is intact: credit moves existing BRC, it never creates it.
- **Interest**: daily accrual job `brp.credit.accrual-cron`: `drawn ×
  annual_rate/365` (`brp.credit.annual-rate-percent` 12) — simple, not
  compound; compounding is how you get arrested in Minecraft. Interest joins
  `drawn`.
- **Status machine**: GOOD → WATCH at 80% utilization (advisory, portal badge)
  → RESTRICTED at 100% → DEFAULTED if RESTRICTED for `grace-days` (7) with no
  net repayment. Recovery: drawn below threshold demotes one step (RESTRICTED
  → WATCH → GOOD); DEFAULTED requires admin `REINSTATE` — debt forgiveness is
  a board decision even here. REINSTATE clears the default; the machine then
  re-runs from utilization — still maxed means RESTRICTED again, honestly.
- **Cutoff** (RESTRICTED+): the P2 promise holds — *movement and play stay
  free*. What stops: (a) all managed chunks auto-close via the admin
  remote-off path (the chunks are the collateral, literally), (b) DR filing
  and running windows suspended (running ones shed), (c) market order
  placement blocked (§8), (d) bonds: existing holdings keep earning —
  creditors are not punished for the debtor, that's the whole point of limited
  liability. Every cutoff action is a `credit_events` row: an audit trail or
  it didn't happen.

## 7. TPS bonds (slice D, R-7 pulled forward from P5)

**V20 `bond_series` / `bond_subscriptions` / `bond_accruals`**:

```
bond_series(id PK, name, face_cents, coupon_pct_annual, term_days,
            total_units, sold_units, threshold_mspt, status, issued_at,
            matures_at)
  status: OPEN → AWARDED(full) → MATURED → REDEEMED / FAILED(<60% sold → refunded)
bond_subscriptions(id PK, series_id, tenant, units, filled_cents,
                   coupon_paid_cents, principal_paid_cents)
bond_accruals(series_id, tenant, day, amount_cents, PK(series,tenant,day))
```

Mechanics, deliberately boring:

- **Issue** (admin): treasury announces size, face, coupon, term, and the
  performance threshold `brp.bond.threshold-mspt` (default **45**).
- **Subscribe** (tenant, while OPEN): BRC moves tenant → treasury escrow sink
  (idem `bond-sub:{series}:{tenant}`). Under-subscribed at open+`ttl` → full
  refund, status FAILED — bonds that fail, fail honestly.
- **Coupon accrual**: daily `face × coupon/365` into `bond_accruals`, the same
  scheduled-tick discipline as credit interest, same clock-injection test
  pattern.
- **Maturity settlement**: `avg_mspt` over [issued, matured] from
  `load_samples` (slice A is a hard dependency — bonds settle against
  *measured* ticks, which is the entire joke):

  ```
  performanceFactor = clamp(threshold / avg_mspt, 0.5, 1.0)
  coupon_paid = accrued × performanceFactor
  ```

  Ran cool all term (30 ms avg) → factor capped at 1.0, full coupon. Ran hot
  (90 ms — double the budget) → factor 0.5: the treasury pays **in ticks it
  actually had**. Principal always repaid in full — default is not satire,
  default is P5's problem and we intend to never have it.
- Payment idems: `bond-coupon:{series}:{tenant}`, `bond-principal:{series}:{tenant}`.
  Bonds pay no ACH fees, waive nothing, and options remain permanently sealed
  (docs/00 non-goals) — futures were sanctioned; derivatives of derivatives
  are how servers die.

## 8. Quota market (slice E)

The industrial rail gets a secondary market. Producers with surplus compute
minutes quote them; consumers with peak-hour farms buy. The peg (quota leg,
10.0 BRC, ±5% band — V16) is the anchor the market oscillates around, and the
treasury's fee book finally has a second line.

**V20 `market_orders` / `market_trades`**:

```
market_orders(id PK, tenant, idem_key UNIQUE, side ASK|BID,
              qty, qty_remaining, price_cents, status, created_at, expires_at)
  status: NEW → PARTIAL → FILLED / CANCELLED / EXPIRED
market_trades(id PK, ask_id, bid_id, qty, price_cents, fee_cents, created_at)
```

- **Placement** (`POST /api/v1/market/orders`, idem `ord:{tenant}:{key}`):
  price must sit inside the peg band (`|price − anchor| ≤ band × anchor` from
  `peg_state`) — the market trades compute, not peg-breaking bets; that's the
  repeg desk's jurisdiction. RESTRICTED tenants blocked (§6c).
- **Matching**: on every placement, taker crosses the book in **price-time
  priority** (`price, created_at, id` — the ordering every real exchange
  inherited from paper). Both order rows locked `findForUpdate` in one
  transaction; fills recorded with per-trade idempotency
  `fill:{askId}:{bidId}:{seq}`.
- **Settlement** (same transaction): BRC journal buyer → seller
  (`price × qty` cents, memo `rail:quota` — the Gresham desk sees industrial
  volume grow), quota delivery = both tenants' `compute` bucket delta,
  treasury fee `brp.market.fee-bips` (50 bips) to the sink.
- **Self-trade prevention**: same tenant on both sides of a cross → that
  candidate fill is skipped (no wash-ticket for the wash detector to find —
  R-4 gets to stay bored).
- **Lifecycle**: TTL `brp.market.order-ttl-days` (7) expiry job; owner
  cancel; partial fills leave `qty_remaining`. No margin, no shorting — you
  cannot sell minutes you do not have (bucket delta checked ≥ 0 at settle).

## 9. OEE, costing, assets (slice F)

The docs/10-business-map P4 promises, kept small and data-honest:

- **Downtime ledger (V21 `downtime_events`)**: `tenant, machine/line, reason
  enum (CHUNK_UNLOADED | HOPPER_JAM | NO_POWER | MANUAL | DR_SHED |
  CREDIT_CUTOFF), since, until, minutes`. Populated from telemetry events the
  agent already has vocabulary for, plus §5/§6 machine actions. **OEE =
  Availability × Performance** per tenant-week; Quality stays 1.0 until rework
  data exists — A×P is measured, Q=1 is *disclosed*, and the footnote says so.
- **Work-order costing**: on completion, stamp `material_cost_cents` (Σ
  inventory issues) + `machine_cost_cents` (machine-minutes × tariff-resolved
  base rate) onto the work order — the CO recommendation from the business
  map, schema now so P4 reports are free.
- **Asset registry (V21 `assets`)**: handover of a project milestone creates
  `{id, tenant, kind, label, location_chunk, cost_basis_cents, acquired_at,
  status}`. Straight-line depreciation is deliberately **deferred** — the
  registry holds cost basis; if P5 wants MACRS for Minecraft it can file a
  proposal like everyone else.

## 10. CC:T adapter (slice G, R-5 pulled forward)

Crafting-Computers don't need a mod dependency — they need HTTP and a Lua
file:

- `GET /api/v1/cc/{tenant}/state` — read-only, scoped bearer token
  (`brp.cc.token`, separate from agent tokens): quota balance, open DR windows,
  mspt forecast for the next hour, active credit status. One poll, no history.
- `cc-brp.lua` shipped as a backend static resource: wraps the endpoint into
  `brp.state()`, `brp.canRun()` — a turtle gate is twelve lines of user code.
- The transcription exercise (AE2/Create deep adapters) stays in P5 where the
  compileOnly roulette belongs; P4 ships the integration that cannot rot.

## 11. Schema & config summary

Migrations: **V19** load & tariff & DR & credit (`load_samples`, `load_profile`,
`tariff_windows`, `deferred_jobs`, `credit_lines`, `credit_events`) ·
**V20** bonds & market (`bond_series`, `bond_subscriptions`, `bond_accruals`,
`market_orders`, `market_trades`) · **V21** shop floor (`downtime_events`,
`workorder_costs` columns, `assets`).

Config keys (defaults): `brp.load.profile-alpha 0.05` ·
`brp.load.live-alpha 0.3` · `brp.load.live-damp 0.5` ·
`brp.load.retention-days 90` · `brp.dr.mspt-budget 40` ·
`brp.dr.award-every 60s` · `brp.dr.max-sheds 2` ·
`brp.credit.annual-rate-percent 12` · `brp.credit.utilization-watch 0.8` ·
`brp.credit.grace-days 7` · `brp.credit.limit-factor 0.25` ·
`brp.credit.limit-floor-cents 50000` · `brp.bond.threshold-mspt 45` ·
`brp.bond.factor-floor 0.5` · `brp.market.fee-bips 50` ·
`brp.market.order-ttl-days 7` · `brp.cc.token (empty=disabled)`.

## 12. Task board

| # | Task | Slice | Status |
|---|---|---|---|
| P4-01 | V19 migration: load_samples, load_profile, tariff_windows, deferred_jobs, credit_lines/events | A | ✓ |
| P4-02 | Census enrichment: players/entities/chunks/forced on heartbeat; minute-bucket upsert | A | ✓ |
| P4-03 | LoadForecastService: profile EWMA + live correction + hourly forecast + endpoints | A | ✓ |
| P4-04 | TariffService + chunk billing refactor onto the table (PeakPricingTest green) | B | ✓ |
| P4-05 | DemandResponseService: file/award/shed/expire + billing | B | ✓ |
| P4-06 | Mod: `dr.window` signals → governor feed gating | B | ✓ |
| P4-07 | CreditService: draw/repay/interest/status machine + cutoff actions | C | ✓ |
| P4-08 | BondService: issue/subscribe/accrue/mature with performanceFactor | D | ✓ |
| P4-09 | QuotaMarketService: orders, matching, settlement, fees, self-trade ban | E | ✓ |
| P4-10 | Downtime ledger + OEE (A×P, Q disclosed) + APS page columns | F | ✓ |
| P4-11 | Work-order completion costing + asset registry | F | ✓ |
| P4-12 | CC:T state endpoint + cc-brp.lua resource | G | ✓ |
| P4-13 | Portal load page: forecast SVG, tariff table, DR windows, credit, bonds, book | H | ☐ |
| P4-14 | OpenAPI sync: load/credit/bonds/market/oee/cc paths | H | ☐ |
| P4-15 | Counts synced (AGENTS.md + READMEs), docs/12 handoff note | H | ☐ |
| P4-16 | Completion audit: full backend suite + mod build + CI green both remotes | H | ☐ |

## 13. Test plan

Pure-math cores stay static and table-driven (the P3 pattern that made
Gresham's rebaseline a one-file review): `LoadForecastMath` (EWMA + damped
correction, cold start), `TariffMath` (window boundaries, version precedence),
`DemandResponseMath` (window sorting, packing, shed extension), `CreditMath`
(interest accrual, utilization thresholds), `BondMath` (accrual, factor
clamp, under-subscription refund), `MarketMath` (price-time priority, partial
fills, band check). Integration: `LoadSamplesTest` (minute upsert from
heartbeat batches), `DemandResponseTest` (award→signal→shed→refund through the
saga), `CreditCutoffTest` (RESTRICTED closes chunks, blocks DR/market, players
unaffected), `BondLifecycleTest` (escrow→accrue→mature→settle to the cent),
`MarketMatchTest` (crossing, fees, idempotent replay, self-trade skip),
`OeeTest` (A×P from downtime rows). Mod units: census counts, DR desk routing,
Lua-adjacent DTO shape.

Non-goals for P4: options and any derivative of a derivative (sealed,
docs/00), margin lending, market making by the treasury itself (the fee is the
treasury's only market income — a central bank that trades its own book is a
conflict of interest with extra steps), AE2/Create deep adapters (P5).
