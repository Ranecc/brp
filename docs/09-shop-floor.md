# 09 — Shop floor & chunk governance (P2)

P2 turns BRP from a surveillance system into an operating system: WMS/MES/APS
for Minecraft. Players get physical terminals instead of web forms; the web
stays a console. Chunk loading becomes a metered, billable, remotely
switchable utility. Casual players can still bootstrap without a finance
degree.

Design rule carried over from P0/P1: the words may judge you, the state
machines may not (AGENTS.md, the meme boundary).

## 1. The three terminals

Naming rationale: block IDs stay boring and descriptive (IDs are plumbing);
personality lives in lang/tooltips only. A name that needs a footnote has
failed code review.

| Block (id) | Chinese | Role |
|---|---|---|
| **Dispatch Terminal** (`brp:dispatch_terminal`) | 调度终端 | The HMI where work orders and signals are issued AND received: order inbox/outbox, signal ack, per-line status at a glance. Everything a "收发令" station does, with a paper trail. |
| **Line Controller** (`brp:line_controller`) | 产线控制器 | The choreography executor: holds a pipeline program (ordered steps, each with a condition — energy, quota, price window, time), enforces conditional boot (`条件开机`) and per-line rate caps. Vanilla redstone farms join as metered lines; mod lines join via adapters. |
| **Form Terminal** (`brp:form_terminal`) | 表单终端 | The in-game form kiosk: lists the tenant's pending forms, opens each in a NATIVE Minecraft screen (`AbstractContainerMenu` + `Screen`, vanilla widgets — no custom GUI toolkit, no web view). The only place a player ever fills paperwork inside the game. |

All three are tenant-neutral hardware: they bind to the chunk's tenant at
placement time and refuse duties in chunks that fail governance (section 4).

## 2. Forms policy: forms are a last resort

- The web console AUTO-ADVANCES every form it can: pre-filled from context,
  auto-submitted where policy allows, one-click approve otherwise.
- A player sees a form only when a human decision is genuinely required
  (approvals above threshold, currency registry changes, re-peg votes).
- When they must fill one: Form Terminal → pick form → native MC UI →
  submit through the normal REST endpoints with the same idempotency keys
  as the web path. One API, two faces.
- The goal is 玩家无感: automation pushes paperwork away from players and
  toward machines and the owner's console.

## 3. Chunk governance (区块管理)

Chunk loading is server compute; compute is metered and billed.

- **Managed chunks**: tenants register chunk slots (quota resource
  `chunk_ticket`, already reserved in V8's comment). Registration is
  idempotent per (tenant, dimension, chunk).
- **Metering**: the agent reports `chunk.load.minutes` per chunk per
  minute-bucket; the backend burns prepaid credits as they arrive
  (row-locked; credit may go negative — debt stays visible, never hidden).
- **Billing (prepaid)**: tenants PURCHASE chunk-load minutes (`purchase`
  endpoint): `treasury` CREDIT → `chunk-sink` (SYSTEM_SINK) DEBIT through
  the ledger's same-transaction idempotency; V12 guards the minutes grant
  in the same unit — money charged once, minutes granted once. Crossing
  zero → HALT + one notification. Price is owner-configured
  (`brp.chunk.billing.cents-per-minute`, default 0 = free compute, i.e.
  dev mode). Integral cents only; float money is a firing offense.
- **Admin remote switch**: owner/officer can DISABLE a managed chunk from
  the console; the governor must halt BRP-controlled activity in that
  chunk within one report cycle. Movement, interaction and rendering are
  never touched — we govern machines, not players.
- **Rogue loaders** (third-party chunk loaders in unregistered chunks):
  detected, metered at the rogue rate, billed to the owning tenant if
  attributable, notified to admins (existing NOTIFY posture). BRP does not
  rip out other mods' tickets — that is a human's crowbar.
- **Bootstrap**: the FIRST registered slot ships a starter credit bundle
  (default 1440 minutes, `brp.chunk.governor.starter-minutes`) — starter
  CREDITS, not currency: the ledger never mints onboarding. Console
  top-ups grant more. 起家 stays possible without a loan shark.

## 4. The Governor (decision table)

Pure function, agent-side, unit-tested, same table served read-only from
the backend for consistency:

| Inputs | Verdict |
|---|---|
| chunk unregistered OR credit exhausted OR billing overdue OR admin-disabled | **HALT** — BRP machinery (terminals, line controllers, BRP tickets) stops; players walk, mine and build freely |
| registered, credit low (below grace margin) | **THROTTLE** — line rate caps drop to the survival floor; dispatch terminal warns |
| registered, credit healthy, admin-enabled | **RUN** |

HALT never means "chunk unloaded": vanilla simulation, movement and
interaction continue. It means BRP stops working for you until the ledger
says otherwise. The machines are on strike; the world is not.

## 5. APS: production balancing (产能平衡)

- **Line rate caps** (docs/03 §9's quantity quotas, applied per line):
  the Line Controller refuses to exceed its cap; over-target marginal
  output already attracts the levy.
- **Fair-share scheduling**: when global capacity is constrained, compute
  budget (quota-minute) is distributed across tenants by share weight =
  paid + granted credits, never by negotiation. Same weekly snapshot that
  feeds the Gini levy (docs/03 §9) feeds the share weights — one snapshot,
  many opinions.
- **Conditional boot conditions**: energy_available, quota_remaining,
  price_window (TPS spot price within band), time_window. All evaluated
  locally against cached backend state; a HALT verdict overrides all.

## 6. Task board (P2)

Small tasks, each independently mergeable, CI-green, with tests. Marked ✓
when landed.

| # | Task | Slice |
|---|---|---|
| P2-01 ✓ | Backend: V10 `managed_chunks` + chunk slot CRUD (register/list) + tests | backend |
| P2-02 ✓ | Backend: governor read model endpoint (agent-facing status: RUN/THROTTLE/HALT inputs) | backend |
| P2-03 ✓ | Backend: admin remote power on/off + audit + portal chunk console page | backend+portal |
| P2-04 ✓ | Backend: prepaid billing — `purchase` (ledger money path, V12 idem) + `chunk.load.minutes` accrual | backend |
| P2-05 ✓ | Backend: first-slot starter credit bundle (`starter-minutes`, credits not currency) | backend |
| P2-06 ✓ | Mod: registry foundation + three terminal blocks + BEs + creative tab | mod |
| P2-07 ✓ | Mod: Form Terminal native menu/screen (form picker, vanilla widgets) | mod |
| P2-08 ✓ | Mod: ChunkGovernor decision engine (pure Java) + unit tests | mod |
| P2-09 ✓ | Mod: BE gating — terminals refuse duty on HALT via `GovernorFeed` (fetch wiring rides P2-12) | mod |
| P2-10 ✓ | Mod: live form feed (`FormDesk` -> Form Terminal tray) + Dispatch Terminal signal status (`SignalDesk`) | mod |
| P2-11 ✓ | Mod: Line Controller pipeline program + conditional boot evaluator (`PipelineProgram`) | mod |
| P2-12 ✓ | Mod: load-minute reporter (`chunk.load.minutes`) + governor poll feeding `GovernorFeed` + dedicated-server lifecycle wiring (BrpAgent) | mod |
| P2-13 ✓ | Portal: chunk console (list / power switch / credit grants) | portal |
| P2-14 | Adapters: AE2 + Create metering hooks behind ModList guards | mod |
| P2-15 | APS: fair-share scheduler + line rate cap enforcement (needs P2-11 runtime wiring) | backend+mod |
| P2-16 ✓ | Contract sync: OpenAPI + events.md for everything above (kept live per slice) | contract |
| P2-17 ✓ | Backend: reconciliation desk v1 (Σ balances ≡ 0, findings + hourly job + portal page) — docs/10 R-1 | backend |
| P2-18 ✓ | Backend: L1 hot-rule table on fanOut (rules as data, hits + notify) — docs/10 R-2 | backend |
| P2-19 ✓ | Backend: time-of-use chunk pricing (peak surcharge, integral cents) — docs/10 R-3b | backend |

Task board discipline: a task leaves the board only with its tests and its
docs in the same commit (CONTRIBUTING rule 0 still rules).
