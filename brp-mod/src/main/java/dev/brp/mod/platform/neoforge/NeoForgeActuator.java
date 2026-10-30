package dev.brp.mod.platform.neoforge;

import dev.brp.mod.agent.ChunkActuator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The real ticket desk (docs/16 §3): FORCED-class tickets held by the mod —
 * level 31, entity-ticking, no timeout, exactly the lease vanilla /forceload
 * writes but WITHOUT the player standing there. Held set tracks only what
 * the mod placed; the operator's own /forceload tickets stay untouched.
 *
 * Persistence note: vanilla persists /forceload through SavedData; our
 * tickets re-assert themselves on the next actuate pass instead — desired
 * state lives in the backend's registry, so restart-safe comes from
 * reconciliation, not from persistence. ◆ TicketType.FORCED is
 * ChunkPos-keyed in 1.21.1; the compiler check against the mappings is the
 * verification.
 */
public final class NeoForgeActuator implements ChunkActuator {

    private static final int DISTANCE = 2;   // vanilla /forceload's own radius

    private final MinecraftServer server;
    private final Set<String> held = ConcurrentHashMap.newKeySet();

    public NeoForgeActuator(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean activate(String dimension, String chunkKey) {
        String key = dimension + "|" + chunkKey;
        if (!held.add(key)) {
            return true;   // already ours
        }
        Boolean placed = onServer(() -> {
            ServerLevel level = levelFor(dimension);
            if (level == null) {
                return false;
            }
            ChunkPos pos = parse(chunkKey);
            if (pos == null) {
                return false;
            }
            level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, DISTANCE, pos);
            return true;
        });
        if (!Boolean.TRUE.equals(placed)) {
            held.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public boolean deactivate(String dimension, String chunkKey) {
        String key = dimension + "|" + chunkKey;
        if (!held.remove(key)) {
            return false;   // not ours to release
        }
        onServer(() -> {
            ServerLevel level = levelFor(dimension);
            ChunkPos pos = parse(chunkKey);
            if (level != null && pos != null) {
                level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, DISTANCE, pos);
            }
            return true;
        });
        return true;
    }

    @Override
    public Set<String> held() {
        return Set.copyOf(held);
    }

    private ServerLevel levelFor(String dimension) {
        try {
            return server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(dimension)));
        } catch (RuntimeException badId) {
            return null;
        }
    }

    private static ChunkPos parse(String chunkKey) {
        String[] parts = chunkKey.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new ChunkPos(Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException badKey) {
            return null;
        }
    }

    private <T> T onServer(java.util.function.Supplier<T> task) {
        if (server == null || server.isSameThread()) {
            return task.get();
        }
        java.util.concurrent.CompletableFuture<T> done = new java.util.concurrent.CompletableFuture<>();
        server.execute(() -> {
            try {
                done.complete(task.get());
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        return done.join();
    }
}
