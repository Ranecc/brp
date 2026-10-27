package dev.brp.mod.adapters;

/**
 * Soft-dependency integration notes (researched, docs/02-integrations.md).
 *
 * All classes in this package load ONLY behind ModList.isLoaded() guards.
 * P2-14 SHIPPED the census metering (NamespacePolicy + ChunkCensus: block
 * namespace counts over registered chunks, ModList-gated, zero external
 * maven). The API-deep probes below remain a transcription exercise for
 * the compileOnly wiring — kept here so that exercise needs no research.
 *
 * ── AE2 (target: NeoForge 1.21.1) ─────────────────────────────────────────
 * ME Bridge block:
 *   - join the network as an appeng.api.networking IGridNode
 *   - subscribe to IStorageGrid; stream IMEMonitor<IAEItemStack> change lists
 *     into InventoryProvider.snapshot() deltas
 *   - ICraftingGrid crafting-plan simulation becomes the MRP BOM-feasibility
 *     oracle (docs/02 section 2)
 *   - transfers execute under a restricted IActionSource bound to one storage
 *     cell per tenant (cell = tenant warehouse, physical isolation)
 *
 * ── Create 6 (target: NeoForge 1.21.1) ────────────────────────────────────
 * Machine Probe:
 *   - KineticBlockEntity exposes speed/stress -> MachineProbe.MachineStats
 *     (rpm, su, loadFactor = su / network capacity, stalled = overStressed)
 * Display Link:
 *   - register a DisplaySource so Create displays show BRP KPIs in-world
 * Workstation (P3):
 *   - register ArmInteractionPoint types so Mechanical Arms feed BRP
 *     work-order stations (docs/02 section 3)
 * Logistics interop:
 *   - BRP dispatches work orders that surface as Stock-Ticker-style requests
 *     / redstone-triggered Packagers; we plan on top of Create's logistics,
 *     we do not duplicate it
 *
 * ── CC:Tweaked (target: NeoForge 1.21.1) ──────────────────────────────────
 * brp_bridge peripheral via dan200.computercraft.api:
 *   - ComputerCraftAPI.registerPeripheralProvider(...)
 *   - methods: stock(itemId), price(itemId), order(table, opts),
 *     signal(channel, level), metrics(farm) -> Lua tables
 *   - wireless modems give cross-chunk reach; this is the technical-player
 *     programmability surface (docs/02 section 4)
 *
 * ── Wireless redstone compat ──────────────────────────────────────────────
 * BRP Signal Hub emits REAL redstone levels (0..15); Create Redstone Links
 * and comparator-based contraptions pick it up with zero API coupling.
 */
public final class Adapters {

    private Adapters() {}

    /** P2: called from mod init with ModList checks, e.g. isLoaded("ae2"). */
    public static int plannedAdapterCount() {
        return 4; // ae2, create, cct, vanilla
    }
}
