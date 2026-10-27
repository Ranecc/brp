# 05 — Anomaly Detection & Behavioral Anti-Cheat

> [中文版](05-anomaly-anticheat.zh.md)
>
> One data substrate, two jobs: economic risk control and behavioral
> anti-cheat. Iron rule: **detection only flags; humans convict.** Output
> goes to a review queue.

## 1. Data substrate

Agent-collected (with a player-visible data disclosure on join):
- behavior stream: movement (chunk-granular sampling, never per-tick),
  click/interaction rates, dig/place rates with block types
- economy stream: every trade/order/transfer (free with the ledger); villager
  trades enter via `merchant.trade` (docs/03 §10)
- logistics stream: inventory deltas, flow meter readings (doc 04)

Features materialize daily into `player_features_daily` (offline batch, never
on the hot path).

## 2. Layered detection

### L1 rules (realtime, hot-reloadable rule table)
Dig rate > 1.2× theoretical cap (per tool/block lookup) · inventory net gain
beyond legal-source bounds (coarse dupe screen) · intra-IP circular transfers ·
large transfers on day-zero accounts. All rules parameterized + versioned
(auditable rule changes — enterprise ✔).

### L2 statistics (daily batch, per-player adaptive baselines)
- robust stats: median + MAD per player per feature (contamination-proof:
  cheating episodes don't drag the baseline); robust z > 6 flags
- EWMA drift: variance-collapse + 24/7 activity patterns (bot signature)
- cohort comparison: cross-sectional percentiles within cohorts
  (playtime/tenant), top 0.1% flagged
- non-player employees count too: per-villager baselines from
  `merchant.trade` feed the rank-and-yank inputs, including the p95/p5
  honesty check (docs/07 §7)

### L3 models (weekly batch, offline)
- unsupervised: Isolation Forest / LOF (Smile on JVM or a Python sidecar,
  from P3) → outlier clusters for human labeling
- graph detection (economic):
  - **wash cycles**: transfer-graph loops (A→B→A with noise), scored by
    cycle detection + amount-conservation
  - **shill networks**: auction self-trades → fund-return graph between
    counterparties
  - **market manipulation**: order-book depth share > threshold with heavy
    cancels (spoofing pattern)

## 3. Ledger invariants (free with double-entry)

The structure gives four reconciliation lines (implementation in doc 06 §5):
1. **Σ balances ≡ 0** across the system's books — breakage = bug or money
   printed outside the ledger
2. **item conservation**: Σ(sources − consumption − destruction) ≡ current
   stock — breakage = dupe; the residual localizes a time window + player
3. **faucet/sink conservation**: every unit born has a faucet entry —
   breakage = direct DB tampering
4. **mint conservation**: Σ emeralds paid out by metered villager trades ≤
   theoretical max from metered farm feed (per-offer rate × trade-lock budget
   — the ceiling is written into game mechanics, docs/03 §10) — breakage =
   unregistered feeder (hopper bypass) or agent underreporting

Daily reconciliation job; any breach pages the highest alert level —
**harder evidence than any model output**.

## 4. Fairness principles (a note to our future selves)

- a flag is not a verdict: L2/L3 output lands in a review queue with an
  evidence pack (feature snapshot + related journal entries); review verdicts
  become labels that retrain L3
- explainability: every flag must be narratable to a human (feature
  contribution list); black-box scores are not evidence
- appeals channel; L1 rules public to players (server owners may hide L3
  details for anti-adversarial reasons)
- adversarial drift: detection feature sets are versioned; the first two
  weeks after a major version switch are observe-only (no false-positive wave)

## 5. Phasing

P1: collection + L1 rule table + review queue (human dashboard)
P2: L2 robust baselines + economy invariant reconciliation job
P3: L3 unsupervised + graph detection
