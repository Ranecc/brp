# 02 — Tech Mod Integrations ⭐

> [中文版](02-integrations.zh.md)
>
> Research date 2026-10. Marks: ✅ verified · ◆ high confidence (training
> knowledge, re-check javadocs before implementation) · ❓ needs verification.
> Verdict up front: **target 1.21.1 NeoForge**; Create locks the loader;
> AE2 and CC:Tweaked are the two highest-value integrations.

## 1. Target matrix

| Mod | Version | What we take from it | Priority |
|---|---|---|---|
| Applied Energistics 2 | 1.21.1 NeoForge ✅ | WMS: ME network inventory; MRP: crafting simulation as BOM oracle | P0 |
| Create 6 | 1.21.1 NeoForge ✅ | machine probes (rpm/SU); logistics interop; arm-driven work stations | P0 |
| CC:Tweaked | 1.21.1 both loaders ✅ | BRP peripheral: Lua access to stock/prices/orders/signals | P0 (best value) |
| Wireless redstone (own + bridges) | — | channel-based wireless analog signals 0-15 | P1 |
| Vanilla containers/comparators | — | baseline adapter: chests/barrels/hoppers + analog levels | P0 |
| Carpet | 1.21.1 ✅ | fake-player farm accounting compat, tick performance source | P2 |

## 2. AE2 integration design

Status: official NeoForge 1.21.1 builds ✅ (appliedenergistics.org). Public
API lives in `appeng.api` and addons integrate through it without touching
internals.

Entry points (◆ re-check against current javadocs):
- **read network inventory**: place an "ME Bridge" block that joins the network
  as an `IGridNode` (appeng.api.networking), subscribe to `IStorageGrid`, stream
  `IMEMonitor<IAEItemStack>` change lists → incremental stock events. Also read
  `ICraftingGrid` CPU state (in-progress work orders, free).
- **crafting simulation**: `ICraftingGrid` plan/simulation → **MRP BOM-feasibility
  oracle** ("64 TNT: enough materials? which CPUs? how long?"). AE2 already
  handles substitutions/priorities better than our own recipe walk; we use AE2
  simulation inside networks and our own BOM engine outside.
- **writes (dispense/reclaim)**: the bridge holds a restricted `IActionSource`
  scoped to designated storage cells — cell per tenant = physically isolated
  tenant warehouses, which is a genuinely pleasing mapping.

Risks: AE2 API renames across versions → isolate in its own module,
compileOnly, reflection-registered fallback. Test matrix includes latest AE2
(a 2026-04 world-creation crash issue #8818 shows why).

## 3. Create 6 integration design

Status (✅ verified): Create 6.0 added the **logistics network** — Stock Link
(exposes a storage to the network), Stock Ticker (requests items), Packager
(ships on redstone). Redstone links moved to andesite tier with a new
transmitter ingredient — **Create ships its own wireless redstone**, so we
bridge, not rebuild. The wiki maintains a "Depend on Create (NeoForge 1.21.1)"
addon guide. A third-party CC:T↔Stock-Ticker API (CC Total Logistics) exists,
proving programmatic logistics is practical.

Entry points:
- **machine probe**: attach to kinetic blocks and read speed/stress from
  `KineticBlockEntity` (◆) → machine registry on the backend. **SU utilization
  is a born capacity-planning metric**; "server-wide SU load heatmap" writes
  itself.
- **logistics interop (P1)**: BRP work orders surface as Stock-Ticker-style
  requests / redstone-triggered Packager shipments. BRP does not duplicate
  Create's logistics — **Create executes transport, BRP plans** (classic
  MRP-vs-logistics split).
- **mechanical arm automation**: BRP "workstation" registers as an arm target
  (◆ `ArmInteractionPoint`): arms drop materials in → work-order step completes →
  arms pick products up. Assembly line meets ERP, maximum flavor.
- **display link**: register a `DisplaySource` (◆) showing BRP KPIs (stock,
  prices, CPI) on in-world Create displays.

## 4. CC:Tweaked bridge (the technical-player killer feature)

CC:T is standard on tech servers (✅ both loaders). Expose a BRP peripheral
(◆ `dan200.computercraft.api` `IPeripheral` +
`ComputerCraftAPI.registerPeripheralProvider`):

```lua
brp.stock("minecraft:iron_ingot")      -- network-wide stock
brp.price("minecraft:tnt")             -- market price / CPI
brp.order({tnt=64}, {approve=true})    -- place/approve orders
brp.signal("farm_north", 15)           -- set a wireless redstone channel
brp.metrics("farm_north")              -- farm throughput
```

Technical players scripting BRP into their own automation moves us from
"admin tool" to "programmable platform". Wireless modems + our signal hub =
cross-chunk control.

## 5. Signal hub (wireless redstone) design

Own implementation; single-mod dependencies are fragile and ❓ on 26.x:

- **channels**: `@team/name` namespaces, per-team isolation
- **values**: 0-15 analog, comparator-compatible semantics
- **entities**: signal hub block — receiving side outputs real redstone /
  comparator levels; sending side subscribes to a channel
- **control path**: backend push → agent → block level (policy engine: stock
  threshold auto-cuts farm power = quota interlock)
- **compat**: physical bridging with Create Redstone Links (we emit real
  redstone; no API coupling). The comparator protocol (0-15) is the lingua
  franca every mod already reads.
- **security**: channel ownership = tenant; cross-tenant subscribe needs grant

## 6. BRP block list

| Block | Function | Module | Phase |
|---|---|---|---|
| Resource Terminal | in-game UI: stock/orders/approvals | WMS/MRP | P1 |
| ME Bridge | AE2 access (read stock / simulate / restricted transfer) | WMS | P2 |
| Machine Probe | kinetic & AE2 device telemetry (rpm/SU/energy) | SCADA | P2 |
| Flow Meter | counting metering point on logistics paths | tech-MC | P2 |
| Signal Hub | channel-based wireless analog signals | control | P2 |
| Workstation | work-order step node (arm-interactable) | MRP | P3 |

## 7. Adapter SPI (code: brp-mod/src/main/java/dev/brp/mod/spi/)

```java
public interface BrpAdapter { String targetModId(); int priority(); }
public interface InventoryProvider extends BrpAdapter {   // AE2/vanilla/Create vaults
  String containerId();
  StockReport snapshot(long sinceCursor);
  TransferResult transfer(TransferCmd cmd);               // idempotent by transferId
}
public interface MachineProbe extends BrpAdapter {
  MachineStats sample();                                  // rpm / SU / loadFactor / stalled
}
public interface SignalPort extends BrpAdapter {
  void set(String channel, int level0to15, Cause cause);  // last-writer-wins idempotent
}
```

Rule: **the SPI imports no Minecraft or mod classes** (pure Java). MC/mod
interaction lives only in adapter modules. Result: unit tests without MC,
zero-diff SPI and business logic on a 26.x port.

## Sources

- Create 6.0.0 changelog (logistics network): https://wiki.createmod.net/users/changelogs/6.0.0
- Depend on Create / NeoForge 1.21.1: https://wiki.createmod.net/developers/depend-on-create/neoforge-1.21.1
- Stock Link / Stock Ticker: https://create.fandom.com/wiki/Stock_Link · https://create.fandom.com/wiki/Stock_Ticker
- CC Total Logistics (existence proof of CC:T↔Create logistics APIs): https://xeli-cloud.gitlab.io/website/2025/05/11/cc-logistics-api-0.2.0.html
- AE2 downloads (official 1.21.1 NeoForge): https://appliedenergistics.org/download
