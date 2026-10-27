# 10 — Business Map Completion: Order-to-Cash and the Thin Tail

> [中文版](10-business-map.zh.md)
>
> Status: BRP is a production-side ERP. Procure → store → plan → execute →
> book is closed; **order-to-cash is open** — the MRP engine (P3) has no fuel
> tank. This doc settles the scope debate once: what ships thin, what ships
> as a field, what is deliberately not built. It is the standing answer to
> "what's still missing from the ERP", so nobody re-litigates it.

## 1. The three rails of demand

What players actually do money-wise on a server:

1. **guild-to-guild trade** — B2B between tenants
2. **player market** — stalls and auction, retail between players
3. **purchases from the server** — the system shop (already ledger-side, doc 03)

All three settle on the ledger's account rail in the single registered
currency (doc 03 §1): players hold and trade the owner's display name; nobody
needs to know the column value behind it. The rails exist; the missing piece
is that **nothing converts demand into work orders**. That is the spine below.

## 2. Three thin modules (the entire new build)

| Module | Reality-ERP equivalent | What it actually is here | Lands |
|---|---|---|---|
| **Sales orders** | SD / order-to-cash | quote → order → WMS fulfillment → delivery → invoice → receivable; P3's MRP explodes work orders from orders, not from admin vibes | P3 |
| **Agreements** | CLM, collapsed | recurring supply deals ("64 iron/week, deposit at stake") as ledger conditional releases — ESCROW + saga + the approval labyrinth already exist; this is a release rule, not a lifecycle suite | P3 |
| **Projects** | PS + RE, collapsed | construction jobs: milestones = the doc 09 task board (WBS), budget-vs-actual = the ledger, handover → asset registry (base / rail line / hall / golem) | P4 |

Sales orders is the only one MRP genuinely cannot live without.

## 3. Credit — what "CRM" means at guild scale

No funnels, no leads, no marketing. The guild layer keeps exactly:

- **counterparty profile**: price history per tenant, fulfillment record
- **credit lines + receivables aging**: selling on credit between players
  makes default risk real — the buyer quit the server, the receivable is a
  writeoff; the escrow verdict rides the approval queue
- churn analytics is **platform-side only** (doc 05 already collects
  `player.left`/`playedMinutes`; "no login N days" is a feature of the
  anomaly substrate, not a module)

Consequence worth noting: doc 03's crisis table gains a receivables signal
for free, and "customer relationship management" finally means what it should
mean here — knowing who pays and who logs off forever.

## 4. Modules that ship as fields, not systems

Each was reviewed as a real-ERP candidate and vetoed as a module. The
residual capability lands where noted; the veto is part of the spec — this
table is load-bearing, cite it in future scope arguments:

| Candidate | Verdict | Where the core survives |
|---|---|---|
| PLM | vetoed | recipes are game-fixed; a farm redesign = a **BOM version field** on MRP recipes (P3 schema) |
| QM | vetoed | WMS **inbound discrepancy ticket** (3 gold nuggets in my iron shipment, P2); enchant/anvil RNG = an NBT grading rule on receipt, not a lab |
| PM / EAM | vetoed | P4 **downtime ledger** (chunk unloaded / hopper jammed / nobody rendering) — OEE falls out of data already collected; spare parts are ordinary WMS items |
| TMS / LE | dissolved | construction side → Projects (§2); inter-warehouse movement → WMS **transfer orders** (P2); daily logistics is players + ME pipes, already integrated (doc 02) |
| Tax engine | merged | doc 03 §4 sinks + §10 mint fee — server "taxation" was always monetary policy, never compliance paperwork |
| CRM suite | dissolved | §3 credit + §2 agreements; player lifecycle → doc 05 (platform tenant) |
| CO (costing) | as field | **completion cost stamped on the work order** (materials + machine-minutes), standard-vs-actual as columns — decide the schema NOW so P4 reports are free, module never |
| DMS / wiki | not built | players have the wiki mod; SOPs are redstone, blueprints are build kits |

## 5. Roadmap deltas

- P2: WMS += inbound discrepancy + transfer orders; mint-metering events land
  with the adapter batch (doc 03 §10)
- P3: MRP += **sales-order spine**, completion-cost fields, BOM version;
  agreements/escrow rule; mint-fee engine
- P4: APS += downtime ledger + OEE; Projects + asset registry
- P5: unchanged — except the exchange will notice the order book now carries
  real orders

## 6. One-line doctrine

An ERP with procure-to-pay but no order-to-cash is an engine bolted to a
fuel truck: everything spins, nothing moves. Ship the spine in P3; the rest
of this document is insurance against re-litigation.
