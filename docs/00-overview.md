# 00 — Vision & Boundaries

> [中文版](00-overview.zh.md)

## One line

BRP turns a Minecraft server's resource management into a **multi-tenant ERP**:
the economy is the finance module, chests/AE2/Create vaults are WMS, recipes are
BOMs, farms and machines are capacity, and redstone is the IO bus.

## Glossary (enterprise-speak ↔ game reality)

| Term | Game meaning | Enterprise-speak |
|---|---|---|
| Tenant | guild / team / tech-MC group | tenant_id on every table, row-level isolation |
| Ledger | server currency books | double-entry, append-only, auditable |
| Stock | chests / AE2 ME network / Create vaults | WMS inventory snapshots |
| BOM | recipe tree (64 TNT → gunpowder + sand) | bill-of-materials explosion |
| Work order | "mine 10 stacks of sand" assigned to a member | order, optionally with approval |
| Capacity | farm output rate (items/h), machine SU/rpm | production capacity / equipment load |
| Signal | wireless redstone channel (analog 0-15) | IO signal bus |
| Flow meter | counting block on a logistics path | metering point / SCADA probe |
| Quota | per-team chunk tickets / machine caps | resource quota (where TPS gets sold, see doc 03) |

## Components & trust boundaries

ONE universal mod jar; the runtime decides what to start:

```
player client (untrusted) ──► MC dedicated server [brp-mod = Agent] ──HTTPS──► BRP-Backend (authoritative)
        │                                                                      ▲
        └──────── web portal / OAuth2 login (read-only + request submission) ───┘
```

Iron rules:
1. The **client runtime never writes state directly** — it renders dashboards and
   submits requests; writes go through the server-side agent or the web portal.
2. The **agent is the only game-side data source**, yet the backend still
   range/rate-checks its reports (the agent process can be tampered with).
3. The **backend is the single authority** for ledger and stock snapshots;
   in-game balances are cached views.

## Explicit non-goals

- Not a permission-plugin replacement (integrates LuckPerms; we don't police execution)
- Not a ban-hammer anti-cheat (detection flags + human review queue, doc 05)
- No Forge/Fabric builds (Create locks us to NeoForge, doc 02)
- No Spring inside the game process (classloader hell + crash coupling, doc 01)
- Nothing touching real money before P5 — and arguably ever

## The "enterprise experience" checklist (satirical but useful)

Member needs approval to withdraw 64 diamonds ✔ (theft prevention is real)
Farm stall pages the on-call Discord alert ✔
Quarterly guild OKRs ✘ (even we have limits)
