# P6 — Zero-arg boot, the local ERP, and the chunk actuator

> [中文版](16-p6-local-actuator.zh.md)
>
> Status: **shipped** · Phase: P6
> Predecessor: docs/15 (deployment) · Triggered by: the operator's three
> questions — "can it boot with no args?", "is singleplayer really inert?",
> "can chunks run without players or fake players?"
>
> Three answers, one theme: BRP stops being a server-admin instrument and
> becomes an economy the player actually holds. No args, no prep — drop the
> jar in, join the world, and the ERP is behind you with the lights on.

## 1. Zero-arg boot (docs/15 §6 amended)

Defaults now carry the first boot end to end:

| Property | Before | Now |
|---|---|---|
| `brp.tenant` | `server` | `server` (unchanged — assigned later via the portal) |
| `brp.token` | `dev` ← a weak default that reached real machines | **minted on first boot**, persisted at `brp/agent.token`, stable across restarts |
| `brp.agent.enabled` | — | `true`; `false` makes the mod fully inert |

The `dev` default is retired on principle: a constant printed in source is
not a token, it is a sticky note on the vault door. The backend's v0
posture is unchanged (no-auth-internal-only; HMAC instance auth is P1,
docs/01 §6) — minting the token makes the machine identity REAL before the
auth machinery that will check it arrives.

## 2. Singleplayer: the local ERP (docs/15 §6 corrected)

The `Dist.DEDICATED_SERVER` gate is gone. The agent boots on the
integrated server's lifecycle too — singleplayer/LAN gets the full local
ERP: the supervisor's sidecar backend (separate JVM, H2 file under
`brp/`, red line 1 holds — Spring still never enters the game JVM), the
line gates, demand response, the bourse, the portal at 127.0.0.1:8080.

What this deliberately does NOT do: touch the client-only rendering path.
The agent reads the integrated server through the same ACL as always;
blocks stay registered on both dists; the player is just the only employee
now.

## 3. The chunk actuator: 免假人激活, by the books

P4's wiki research established the physics: a FORCED-class ticket is level
31, entity-ticking, **no timeout** — chunks stay alive with zero players
in range. Until now BRP only *metered* tickets someone else placed
(`/forceload`). P6 adds the actuator half: **the mod holds the tickets
itself**, driven by the machinery it already has.

```
registered chunks (backend registry, P2)
  ∩ governor verdict != HALT          → desired ticket set
held tickets (the mod's own)          → actual ticket set
reconcile(), budget 16 changes/pass   → addRegionTicket / removeRegionTicket
```

- **Decision** stays where it was: `GovernorFeed` verdicts (RUN/THROTTLE/
  HALT) from the same poll that has fed the terminals since P2. Unknown
  chunk = HALT = strike; a chunk the backend stopped vouching for loses
  its ticket within one pass.
- **THROTTLE keeps the ticket**: a throttled line runs every Nth window
  (`LineGate`); a loaded chunk is the prerequisite for running at all.
  Only HALT releases. Fail-closed: a missing verdict is a HALT, never an
  invitation.
- **Smart start/stop is inherited, not invented**: credit exhaustion,
  admin disable, and DR shedding all land as HALT/THROTTLE verdicts
  upstream — the actuator just makes them physical. A shed window now
  actually stops the farm's chunk from ticking... no — THROTTLE semantics
  mean the chunk stays loaded (the line still runs windows); what stops is
  governed by `LineGate` as before. The actuator's contribution is the
  no-player half and theHALT half.
- **免假人**: the ticket is FORCED-class — mobs spawn, machines tick,
  hoppers push, with nobody and nothing pretending to be a player. No fake
  player entities are created (nothing to leak, nothing for anticheat to
  chase, nothing for other mods to trip over).
- **Storm guard**: at most 16 ticket changes per 30 s pass, each direction.
  Tickets load chunks; chunks cost mspt; a loader that stamps forty
  tickets in one tick is how loaders get themselves banned.
- **Restart-safe by reconciliation, not persistence**: unlike vanilla
  `/forceload` (SavedData), mod-held tickets are re-asserted on the first
  pass after boot — desired state lives in the backend's registry, so a
  stale ticket cannot outlive its verdict.
- **Only our tickets**: the held set tracks what the mod placed; the
  operator's own `/forceload` tickets are never touched.
- ◆ `TicketType.FORCED` (ChunkPos-keyed, distance 2 — vanilla's own radius)
  verified against 1.21.1 by compiling against the mappings.

**Performance posture** (the second half of the question): the actuator
does not add load — it moves existing load behind a decision layer. The
load plane (P4) already samples mspt/players/entities/chunks per minute;
the forecaster and DR already know when the server is drowning; the
actuator gives them a hand on the switch. Bounded passes, server-thread
hops through `server.execute`, one log line per non-empty pass.

## 4. Task board

| # | Slice | Deliverable | Status |
|---|---|---|---|
| P6-01 | A | Token mint + persist, `brp.agent.enabled`, zero-arg defaults | ✓ |
| P6-02 | A | Dual-dist boot (singleplayer local ERP) | ✓ |
| P6-03 | B | `ChunkActuator` port + `NeoForgeActuator` (FORCED tickets) | ✓ |
| P6-04 | B | `ActuatorLogic` reconcile (fail-closed, budget) + 5 units | ✓ |
| P6-05 | B | Runtime `actuate` stage (30 s) + wiring | ✓ |
| P6-06 | C | docs/16 + docs/15 §6 correction + counts | ✓ |

## 5. Non-goals

- No fake players, ever — that was the point.
- No portal ticket types beyond FORCED: PORTAL/DRAGON/START tickets are
  vanilla's business.
- No per-chunk ticket distances: distance 2, vanilla's own /forceload
  radius, one knob for a future operator who asks.
- No in-game command UI yet (tenant/token re-binding stays portal +
  properties until the command tree lands as its own slice).
