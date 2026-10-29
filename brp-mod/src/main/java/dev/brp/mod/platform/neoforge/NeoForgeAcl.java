package dev.brp.mod.platform.neoforge;

import dev.brp.mod.acl.Acl.GameThreads;
import dev.brp.mod.acl.Acl.PlayerDirectory;
import dev.brp.mod.acl.Acl.PlayerRef;
import dev.brp.mod.acl.Acl.WorldAccess;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * NeoForge 1.21.1 adapter for the ACL ports — implemented for P2-12 wiring.
 * The ONLY class allowed to touch MinecraftServer from the agent path
 * (docs/02 section 7): business code sees the three ports, nothing else.
 *
 * Threading (docs/01 section 5): reads like forcedChunks()/playerList are
 * cheap snapshots safe from the IO pool; anything mutating goes through
 * onServerThread(Runnable) -> server.execute.
 */
public final class NeoForgeAcl implements PlayerDirectory, GameThreads, WorldAccess {

    private final MinecraftServer server;
    private final Executor ioPool;

    public NeoForgeAcl(MinecraftServer server, Executor ioPool) {
        this.server = server;
        this.ioPool = ioPool;
    }

    // --- PlayerDirectory: the MC server IS the source of truth ---

    @Override
    public PlayerRef byUuid(String uuid) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> p.getStringUUID().equals(uuid))
                .map(p -> new PlayerRef(p.getStringUUID(), p.getGameProfile().getName()))
                .findFirst().orElse(null);
    }

    @Override
    public PlayerRef byName(String name) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(name))
                .map(p -> new PlayerRef(p.getStringUUID(), p.getGameProfile().getName()))
                .findFirst().orElse(null);
    }

    @Override
    public List<PlayerRef> online() {
        return server.getPlayerList().getPlayers().stream()
                .map(p -> new PlayerRef(p.getStringUUID(), p.getGameProfile().getName()))
                .toList();
    }

    @Override
    public List<PlayerRef> known() {
        // online roster only; full offline history belongs to the backend's
        // player_name_history, synced over time — we do not parse level data.
        return online();
    }

    // --- GameThreads ---

    @Override
    public boolean onServerThread() {
        return server.getRunningThread() == Thread.currentThread();
    }

    @Override
    public void onServerThread(Runnable r) {
        server.execute(r);
    }

    @Override
    public Executor io() {
        return ioPool;
    }

    // --- WorldAccess ---

    @Override
    public long gameTime() {
        return server.overworld().getGameTime();
    }

    @Override
    public double mspt() {
        long[] nanos = server.getTickTimesNanos();
        if (nanos == null || nanos.length == 0) {
            return 0.0;
        }
        long sum = 0;
        for (long n : nanos) {
            sum += n;
        }
        return sum / (double) nanos.length / 1_000_000.0;
    }

    @Override
    public double tps() {
        double mspt = mspt();
        return mspt <= 0 ? 20.0 : Math.min(20.0, 1000.0 / mspt);
    }

    @Override
    public Set<String> forcedChunks() {
        Set<String> keys = new HashSet<>();
        for (var level : server.getAllLevels()) {
            String dim = level.dimension().location().toString();
            for (Long packed : level.getForcedChunks()) {
                var pos = new net.minecraft.world.level.ChunkPos(packed);
                keys.add(dim + "|" + pos.x + "," + pos.z);
            }
        }
        return keys;
    }

    /**
     * P4 census (docs/13 section 2): players, entities, loaded chunks — the
     * three load predictors, sampled together. Read-only counts, safe from
     * the IO pool per the threading note in the class javadoc.
     */
    @Override
    public dev.brp.mod.acl.Acl.LoadCensus census() {
        int players = server.getPlayerCount();
        long entities = 0;
        int chunks = 0;
        for (var level : server.getAllLevels()) {
            for (var ignored : level.getAllEntities()) {
                entities++;
            }
            chunks += level.getChunkSource().getLoadedChunksCount();
        }
        return new dev.brp.mod.acl.Acl.LoadCensus(players, entities, chunks);
    }
}
