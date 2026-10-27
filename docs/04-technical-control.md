# 04 — Technical-MC Metering & Control

> [中文版](04-technical-control.zh.md)
>
> Tech-MC players treat farms as assets, output as cash flow, and a stalled
> farm as an incident. BRP models the farm as a first-class object:
> **meter → attribute → control → optimize**.

## 1. Farm as an asset (farm registry)

```
farm = {
  tenant, name, region(chunk set),
  meters:  [FlowMeter#1, ...],           // metering points
  cutoff:  SignalChannel("farm_x/off"),  // master switch
  output:  [iron_ingot, ...],
  quota:   {onhand_max: 27648},          // stock policy
}
```

## 2. Metering

- **Flow Meter**: a counting block on the logistics path (hopper chain /
  pipe endpoints); aggregates per-item counts into 10s/1min/1h windows,
  batched to the backend into `throughput` series (PG partitions now,
  TimescaleDB when it hurts)
- **capacity = EWMA(rate)**, not instantaneous; sustained >50% output drop =
  **"farm stalled" alert** (pages Discord)
- ME Bridge / Create vault deltas double as metering sources (no flow meter
  needed to compute net output)
- Carpet fake-player farms produce no player events, but flow meters sit on
  the path and count anyway → accounting decoupled from player presence ✔
- villager halls meter themselves as mints: `merchant.trade` events are the
  commodity leg's water gauge (docs/03 §10); conservation checks against
  flow-meter feed run in doc 05

## 3. Control

- **master switch**: farms bind a signal channel at registration; agent applies
  `signal.set(channel, level)` → real redstone cuts the farm clock
- **policy engine** (backend-computed, agent-executed, zero added latency):
  - stock cap: `iron_onhand >= quota` → channel 15 (stop); back under 80% →
    0 (resume) — **tech-MC min/max replenishment**
  - time windows: full throttle only in off-peak hours (with TPS quotas, doc 03 §6)
  - interlocks: several farms share one switch so they cannot all run at once —
    **staggered scheduling for redstone farms**
- **analog output**: channel 0-15 mapped to stock level / price index →
  comparators read it → players build redstone logic on top (vanilla-native)

## 4. Living with the tech-MC community (product principles)

1. **never bypass player redstone** — we provide the master-switch primitive;
   composition is the player's business
2. **programmability first** — the CC:T peripheral (doc 02 §4) lets technical
   players script BRP instead of being locked into our UI
3. **transparent metering** — any tenant can export all raw data (CSV/JSON)
4. performance budget: agent collection < 0.1ms per world tick (lock-free
   snapshots + batching, doc 01 §5)

## 5. Phasing

P2: flow meters + farm registry + throughput dailies + stall alerts
    + merchant.trade / villager.cured collection (docs/03 §10)
P3: stock-cap auto-cutoff (min/max)
P4: staggered scheduling (TPS quota interlock) + capacity forecasts feeding MRP/APS
