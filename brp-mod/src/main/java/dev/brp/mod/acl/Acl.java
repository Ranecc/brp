package dev.brp.mod.acl;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Anti-corruption layer ports (docs/02 section 7, extended).
 *
 * The agent/core packages speak ONLY these interfaces. Platform adapters
 * (platform/neoforge) and mod adapters (adapters/) implement them against
 * real game classes. Rules:
 * - ZERO Minecraft / NeoForge / other-mod imports above this line;
 * - ports are versioned additively — a 26.x port or a loader switch replaces
 *   adapters, never the core;
 * - ports are intentionally narrow: capabilities, not game APIs.
 */
public final class Acl {

    private Acl() {}

    public record PlayerRef(String uuid, String name) {}

    /** Player identity directory — the uuid/name source of truth lives in the MC server. */
    public interface PlayerDirectory {
        PlayerRef byUuid(String uuid);
        PlayerRef byName(String name);
        List<PlayerRef> online();
        /** Full sync feed for POST /api/v1/players/batch (renames included). */
        List<PlayerRef> known();
    }

    /** Threading contract: game thread vs agent IO pool (docs/01 section 5). */
    public interface GameThreads {
        boolean onServerThread();
        /** Schedule onto the server/main thread (world mutation must use this). */
        void onServerThread(Runnable r);
        /** Shared IO pool for serialization/HTTP/WAL — NEVER the tick thread. */
        Executor io();
    }

    /** Lifecycle event hooks the core subscribes to. */
    public interface GameEvents {
        void onPlayerJoin(Consumer<PlayerRef> handler);
        void onPlayerLeave(Consumer<PlayerRef> handler);
        void onServerStopping(Runnable handler);
    }

    /** World-side capabilities needed later (P2 probes) — declared now, pinned early. */
    public interface WorldAccess {
        long gameTime();
        double mspt();       // mean ms per tick, for the TPS-commodity metrics
        double tps();
    }
}
