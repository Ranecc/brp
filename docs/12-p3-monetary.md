# 12 — P3 detailed design: the monetary machinery

> Chinese mirror: [12-p3-monetary.zh.md](12-p3-monetary.zh.md)
>
> Everything here is anchored in docs/03 section 9 as approved. Where an
> implementation proxy differs from the doc's ideal wording, the deviation
> is named in the open — the docs stay canon, the proxies stay honest.

## 1. Scope

P3 = detection -> diagnosis -> prescription (docs/03 §9): the shared weekly
snapshot, the hysteresis regime, the two levies (marginal compute, wealth),
the BRP-09xx alert family, the mint window + vault, the peg + re-peg vote,
the Gresham detector, plus the two graded backlog arrivals (R-4 wash cycles,
R-6 CPI feed).

**Non-goals** (unchanged): options stay sealed forever; enforcement beyond
NOTIFY stays human; no real money; no third governance channel — policy
votes ride the existing approval ladder.

## 2. Data model (V15–V17, one migration per task slice)

```sql
-- V15: the shared snapshot + the regime + the two levy ledgers
weekly_snapshots(week_of DATE PK, gini DOUBLE, top_share DOUBLE,
                 regime_after VARCHAR(8), captured_at)
snapshot_tenants(week_of DATE, tenant_id, wealth_cents BIGINT,
                 velocity DOUBLE, PRIMARY KEY (week_of, tenant_id))
policy_regimes(name VARCHAR(32) PK, state VARCHAR(8), entered_at,
               last_reading DOUBLE, updated_at)          -- 'antitrust'
levy_minutes(minute TIMESTAMP, tenant_id, share_pct DOUBLE, units DOUBLE,
             amount_cents BIGINT, posted BOOLEAN DEFAULT FALSE,
             PRIMARY KEY (minute, tenant_id))            -- insert-only idem
wealth_assessments(week_of DATE, tenant_id, bracket_cents BIGINT,
                   surcharge BOOLEAN, amount_cents BIGINT,
                   PRIMARY KEY (week_of, tenant_id))     -- insert-only idem

-- V16: the peg, the vault, policy requests
peg_state(leg VARCHAR(8) PK, rate DOUBLE, band_low, band_high, updated_at)
peg_history(id, leg, old_rate, new_rate, request_id, changed_at)
vault_registrations(tenant_id, container_ref, item_id, PRIMARY KEY(...))
vault_movements(id, tenant_id, direction, item_id, qty, correlation, at)
policy_requests(id PK, kind, payload_json, status, requested_by, at)
                 -- kind: 'repeg-vote' | 'peg-band-emergency'

-- V17: prices + wash findings
price_observations(id, item_id, price_cents, currency, qty, venue, observed_at)
cpi_daily(day DATE PK, index DOUBLE, basket_json TEXT)
wash_findings(id, tenant_id, accounts_json, cycle_len INT, score DOUBLE, at)
```

Rail tagging for Gresham needs NO schema: escrow journals carry the memo
prefix `rail:emerald:` / `rail:quota:` — the mint window and the quota
redemption flow write those prefixes, the detector reads them.

## 3. The shared weekly snapshot (P3-01)

- Cron `brp.snapshot.cron` (default Monday 00:05, `brp.reports.zone`).
- Per tenant: `wealth_cents` = treasury balance; `velocity` = nominal
  traded volume of the week / max(mean balance, 1). Volume from
  `journal_entries` sums per tenant (TRANSFER-flavored memos).
- Server row: Gini over tenant wealths (the standard formula, unloved
  tenants at zero wealth included — the denominator is the truth, not the
  marketing), `top_share` = largest tenant wealth / total.
- Idempotent per `week_of` (insert-first, DVE = already captured).

## 4. The hysteresis regime (P3-02)

`policy_regimes['antitrust']`: after each snapshot, reading = gini;
- >= 0.8 -> ACTIVE (BRP-0901 on entry), < 0.7 -> INACTIVE (BRP-0902),
- between: holds state (half-open breaker; flapping taxes are player-
  facing twitching). Sustained `top_share > 0.5` across 2 consecutive
  snapshots -> BRP-0950 (red line; pages humans, bills nobody).
Every levied mechanism asks the regime first. Regime state is served
read-only at `GET /api/v1/monetary/regime` (agent + portal share it).

## 5. The marginal compute levy (P3-04)

- **Share source, v1 proxy (named deviation):** docs/03 meters
  fleet-mspt share via flow meters; until per-machine mspt metering ships
  (P2-14 census is counts, not cycles), share = tenant chunk-load minutes
  / total minutes, from the SAME `chunk.load.minutes` rows the meter and
  APS already use. One number, many opinions (docs/09 §5). The proxy
  swaps for mspt share without touching anything downstream.
- Minute assessment (scheduled each minute for the prior minute):
  per tenant with load minutes, share = minutes/total; MARGINAL band
  pricing on share: 0-20% @1.0x | 20-35% @1.2x | 35-50% @1.6x | >50% @
  2.5x — applied to the tenant's minutes IN each band of its own share
  curve (income-tax semantics: 49.9% forever is not a strategy).
- Emerald denomination: `amount = units × base_emeralds × band × peg` —
  base `brp.levy.compute.base-emeralds-per-minute` (default 0 = armed but
  free until the owner prices compute), converted to BRC cents at the
  emerald-leg peg rate. Emeralds are the invoice language; the ledger
  still only speaks BRC.
- Ledger posting: rows accumulate in `levy_minutes`; a materializer posts
  one journal per tenant per minute batch (`treasury` CREDIT ->
  `expense-sink` DEBIT) with idem `tax:{tenant}:{yyyyMMddHHmm}` — replay
  safe, never double-billed (insert-first, the V12 discipline).
- Regime gate: ACTIVE required. INACTIVE rows still assess at 0 and mark
  `posted=TRUE` (the record of a clemency week is still a record).

## 6. The wealth levy (P3-03)

Weekly, regime-gated, from the shared snapshot: step brackets on
`wealth_cents`: <= 1M: 0% | <= 10M: 0.5% | > 10M: 1.5%, plus hoarding
surcharge `brp.levy.wealth.hoarding-surcharge` (default +50% relative)
when `velocity < 0.5`. One journal per (week, tenant), idem
`wealth:{tenant}:{week}`, treasury -> SYSTEM_SINK. Negative in net
emission: burned, not spent.

## 7. BRP-09xx alert family (P3-05)

| code | trigger | severity |
|---|---|---|
| 0901/0902 | regime ACTIVE/INACTIVE transition | INFO/WARN |
| 0903 | Gresham single-leg share > 95% (7d) | CRIT |
| 0904 | vault drift (stock ≠ cumulative books) | CRIT |
| 0905 | mint-window compensation exhausted | CRIT |
| 0906 | levy materializer failure streak > 5 | WARN |
| 0950 | top share > 50% sustained 2 weeks — the red line | CRIT |

Alerts land as `anomaly_hits` rows (rule id = the code) + outbox notify;
the 0950 copy states plainly that a tool cannot be both a levy and a
death sentence.

## 8. Mint window + vault (P3-06)

- Vault = registered container(s) per tenant (`vault_registrations`);
  movement recording in `vault_movements` keyed by correlation id.
- Saga `mint-window` (existing SagaEngine): step 1 `book-faucet` posts the
  FAUCET journal FIRST; step 2 `dispense` emits a `vault.dispense` signal
  command (mod-side executor, P3-12) and blocks on its ack; compensations
  run reverse (revoke-dispense, then burn-back journal). Crash recovery
  reuses the P2-fixed payload deserialization — the escrow lesson stays
  learned.
- Drift check (hourly): physical stock snapshot (agent inventory probe)
  vs cumulative FAUCET/dispense books; mismatch -> BRP-0904. The vault
  may not disagree with the ledger, on pain of paperwork.
- Mint above `brp.mint.approval-threshold` auto-files an approval request
  (existing labyrinth, no new channel).

## 9. Peg + re-peg (P3-07)

- `peg_state`: two legs ('emerald': BRC per stack; 'quota': BRC per
  quota-minute), band ±5%.
- Out-of-band reading 2 snapshots in a row auto-files a
  `policy_requests('repeg-vote')` with the proposed rate — OWNER approves
  (same role ladder); execution writes `peg_state` + `peg_history`
  atomically with the approval id. No third governance channel; RFC P3
  grants no exemptions here either.

## 10. Gresham detector (P3-08)

7-day sliding window over escrow journals grouped by `rail:*` memo
prefix; single-leg share > 95% -> BRP-0903 + crisis signal into the
docs/03 §5 rule table (rules as data — it IS an L1 hot rule with a
computed field). Prescription stays human: re-peg or float the weak leg.

## 11. Wash cycles (P3-09, docs/10 R-4)

Per tenant: accounts are vertices; every journal's DEBIT/CTEDIT pair
within the tenant is a directed weighted edge. Tarjan SCC; a finding is
an SCC of size >= 2 with a cycle length <= 4, scored by roundness
(amount % 1000 == 0), tightness (all legs inside one hour), and share of
the tenant's weekly volume. Cycles per se are legal commerce — the score
carries the accusation, and the accusation is paperwork (anomaly hit),
not punishment. Weekly + on-demand `POST /api/v1/monetary/wash/scan`.

## 12. Prices + CPI (P3-10, docs/10 R-6)

- Ingest: `market.price.observed` events (already in events.md) land in
  `price_observations` via the fanOut hook; manual entry via
  `POST /api/v1/monetary/prices` (portal form + API, the shop-plugin
  feeder arrives with P4 WMS).
- CPI daily: geometric mean of item price relatives vs the base week,
  basket + weights from `brp.cpi.basket` (config JSON). `cpi_daily`
  feeds the portal line and the §5 crisis rules (inflation/deflation).

## 13. Mod side (P3-12)

One thing only: the `vault.dispense` signal executor — the agent's
existing signal poll executes a registered vault-container withdrawal and
acks with the movement id. Everything else in P3 is backend + portal.

## 14. Portal + contract

- Portal page `货币 / Monetary`: regime badge, latest snapshot (Gini, V,
  top share), wealth/compute levy tables, Gresham rail bars, CPI line
  (inline SVG, no JS framework — the chrome stays classic), peg cards,
  policy request queue with approve/reject.
- OpenAPI: `/monetary/*` (regime, snapshot, levies, gresham, cpi, prices,
  wash/scan, policy-requests); events.md already carries
  `market.price.observed`.

## 15. Config keys (all defaults = armed but inert)

| key | default | meaning |
|---|---|---|
| brp.snapshot.cron | Mon 00:05 | weekly snapshot |
| brp.regime.gini.on / .off | 0.8 / 0.7 | hysteresis band |
| brp.regime.redline-share | 0.5 | BRP-0950 line |
| brp.levy.compute.base-emeralds-per-minute | 0 | compute levy price |
| brp.levy.compute.bands | 0.2/1.0,0.35/1.2,0.5/1.6,1.0/2.5 | marginal bands |
| brp.levy.wealth.hoarding-surcharge | 0.5 | V<0.5 surcharge (relative) |
| brp.peg.emerald.rate / brp.peg.quota.rate | 64 / 10 | BRC per stack / quota-minute |
| brp.peg.band | 0.05 | ±band |
| brp.mint.approval-threshold | 100000 | cents |
| brp.cpi.basket | {} | item->weight |

## 16. Task board (acceptance = tests + docs in the same commit)

| # | task | slice |
|---|---|---|
| P3-01 | V15 + weekly snapshot job (Gini/V/top-share, idempotent) | backend |
| P3-02 | hysteresis regime + BRP-0901/0902 + regime endpoint | backend |
| P3-03 | wealth levy engine (brackets, V surcharge, regime gate) | backend |
| P3-04 | compute levy: minute assessment (share proxy = load-minutes) + marginal bands + materializer | backend |
| P3-05 | BRP-09xx wiring (0950 sustained-share, 0906 streak) + copy | backend |
| P3-06 | V16 + mint-window saga + vault registration/movements + drift (0904) | backend |
| P3-07 | peg state + band + auto-filed re-peg votes + execution | backend |
| P3-08 | Gresham rail aggregation + 0903 | backend |
| P3-09 | V17 + wash-cycle SCC + scan endpoint | backend |
| P3-10 | price observations ingest + CPI daily | backend |
| P3-11 | portal Monetary page (badge, tables, rails, CPI, peg, votes) | portal |
| P3-12 | mod: vault.dispense signal executor | mod |
| P3-13 | contract sync (openapi /monetary/*) | contract |
| P3-14 | docs resync + counts (03/09/12 cross-links) | docs |
