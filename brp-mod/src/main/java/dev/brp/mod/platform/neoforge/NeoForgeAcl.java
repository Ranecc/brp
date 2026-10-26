package dev.brp.mod.platform.neoforge;

import dev.brp.mod.acl.Acl.GameEvents;
import dev.brp.mod.acl.Acl.GameThreads;
import dev.brp.mod.acl.Acl.PlayerDirectory;
import dev.brp.mod.acl.Acl.PlayerRef;
import dev.brp.mod.acl.Acl.WorldAccess;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * NeoForge 1.21.1 adapter for the ACL ports. Sketch only — fills in during P1
 * wiring (event bus subscriptions, server lifecycle). Kept here to pin the
 * port surface early so business code never waits on platform details.
 *
 * Implementation notes (verify against 1.21.1 NeoForge at wiring time):
 * - PlayerDirectory: MinecraftServer.getPlayerList() — ServerPlayer.gameProfile()
 *   gives UUID + name; offline servers yield type-3 undashed UUIDs (fine, the
 *   backend stores verbatim).
 * - GameThreads: NeoForgeEventHooks / server.getRunningThread() for the
 *   onServerThread check; agent IO pool is our own ExecutorService.
 * - GameEvents: PlayerLoggedInEvent / PlayerLoggedOutEvent / ServerStoppingEvent.
 * - WorldAccess: ServerLevel.getGameTime(); MSPT via ServerTickRateManager or
 *   our own tick-duration EWMA measured in the agent (prefer our own — no API
 *   dependency, works across versions).
 */
public final class NeoForgeAcl {

    private NeoForgeAcl() {}

    public static PlayerDirectory playerDirectory(/* MinecraftServer server */) {
        // P1: return new PlayerDirectoryImpl(server);
        throw new UnsupportedOperationException("wired in P1");
    }

    public static GameThreads threads(/* agent io pool */) {
        throw new UnsupportedOperationException("wired in P1");
    }

    public static GameEvents events(/* IEventBus neoBus */) {
        throw new UnsupportedOperationException("wired in P1");
    }

    public static WorldAccess worldAccess(/* MinecraftServer server */) {
        throw new UnsupportedOperationException("wired in P1");
    }

    @SuppressWarnings("unused")
    private static final class Placeholder {
        static List<PlayerRef> ONLINE = List.of();
        static Consumer<PlayerRef> NOOP = p -> {};
        static Executor DIRECT = Runnable::run;
    }
}
