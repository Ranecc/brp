package dev.brp.mod.adapters;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * P2-14 census (docs/02, Adapters.java): walk the registered chunks' block
 * entities and count machines by block namespace. The only Create/AE2/CC:T
 * integration that ships without compileOnly roulette: ModList-guarded,
 * vanilla world data, honest numbers.
 */
public final class ChunkCensus {

    private ChunkCensus() {}

    /**
     * @param chunkKeys "dimension|x,z" keys (the agent's registered set)
     * @return namespace -> machine block-entity count (watched mods only,
     *         each gated by ModList.isLoaded)
     */
    public static Map<String, Integer> byNamespace(MinecraftServer server,
                                                   Set<String> chunkKeys,
                                                   NamespacePolicy policy) {
        Map<String, Integer> totals = new HashMap<>();
        for (String key : chunkKeys) {
            int split = key.indexOf('|');
            if (split <= 0) {
                continue;
            }
            ServerLevel level = levelFor(server, key.substring(0, split));
            String[] coords = key.substring(split + 1).split(",");
            if (level == null || coords.length != 2) {
                continue;
            }
            try {
                var chunk = level.getChunk(Integer.parseInt(coords[0].trim()),
                        Integer.parseInt(coords[1].trim()));
                for (var be : chunk.getBlockEntities().values()) {
                    String ns = be.getBlockState().getBlock()
                            .builtInRegistryHolder().key().location().getNamespace();
                    totals.merge(ns, 1, Integer::sum);
                }
            } catch (RuntimeException notLoadedOrMalformed) {   // NFE included by subtyping
                // a chunk key that never loads is nobody's machine
            }
        }
        return policy.watched(totals);
    }

    /** @return true when the namespace is BOTH watched and actually present. */
    public static boolean modLoaded(String namespace) {
        return ModList.get().isLoaded(namespace);
    }

    private static ServerLevel levelFor(MinecraftServer server, String dimension) {
        try {
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimension));
            return server.getLevel(key);
        } catch (RuntimeException badId) {
            return null;
        }
    }
}
