package dev.brp.mod.platform.neoforge;

import dev.brp.mod.agent.BoursePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * The bourse's physical desk (docs/14 §8, P5-10): one bound container
 * ("dim|x,y,z") is the intake/withdraw slot. Mutations hop through
 * server.execute with the docs/01 §5 threading rule; the vault dispense
 * port predates the helper and gets the same treatment next audit.
 */
public final class NeoForgeBourse implements BoursePort {

    private final MinecraftServer server;
    private final String containerRef;

    public NeoForgeBourse(MinecraftServer server, String containerRef) {
        this.server = server;
        this.containerRef = containerRef;
    }

    @Override
    public SortedMap<String, Long> inventory() {
        return onServer(() -> {
            SortedMap<String, Long> out = new TreeMap<>();
            Container container = container();
            if (container == null) {
                return out;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack held = container.getItem(slot);
                if (held.isEmpty()) {
                    continue;
                }
                String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
                out.merge(id, (long) held.getCount(), Long::sum);
            }
            return out;
        });
    }

    @Override
    public boolean extract(String itemId, long qty) {
        return onServer(() -> {
            Container container = container();
            if (container == null) {
                return false;
            }
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            long remaining = qty;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack held = container.getItem(slot);
                if (!held.isEmpty() && held.getItem() == item) {
                    int take = (int) Math.min(remaining, held.getCount());
                    container.removeItem(slot, take);
                    remaining -= take;
                }
            }
            return remaining == 0;
        });
    }

    @Override
    public boolean deposit(String itemId, long qty) {
        return onServer(() -> {
            Container container = container();
            if (container == null) {
                return false;
            }
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == null || BuiltInRegistries.ITEM.getKey(item)
                    .equals(ResourceLocation.parse("minecraft:air"))) {
                return false;   // an unknown id deposits nothing, on principle
            }
            // capacity first: a partial fit would double-count custody
            long capacity = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack held = container.getItem(slot);
                if (held.isEmpty()) {
                    capacity += item.getDefaultMaxStackSize();
                } else if (held.getItem() == item) {
                    capacity += item.getDefaultMaxStackSize() - held.getCount();
                }
            }
            if (capacity < qty) {
                return false;
            }
            long remaining = qty;
            for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                ItemStack held = container.getItem(slot);
                if (!held.isEmpty() && held.getItem() == item
                        && held.getCount() < item.getDefaultMaxStackSize()) {
                    int put = (int) Math.min(remaining,
                            item.getDefaultMaxStackSize() - held.getCount());
                    held.grow(put);
                    remaining -= put;
                } else if (held.isEmpty()) {
                    int put = (int) Math.min(remaining, item.getDefaultMaxStackSize());
                    container.setItem(slot, new ItemStack(item, put));
                    remaining -= put;
                }
            }
            return remaining == 0;
        });
    }

    private Container container() {
        String[] parts = containerRef.split("\\|");
        if (parts.length != 2 || server == null) {
            return null;
        }
        ServerLevel level;
        try {
            level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(parts[0])));
        } catch (RuntimeException badId) {
            return null;
        }
        if (level == null) {
            return null;
        }
        String[] xyz = parts[1].split(",");
        if (xyz.length != 3) {
            return null;
        }
        BlockPos pos = new BlockPos(Integer.parseInt(xyz[0].trim()),
                Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim()));
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container container ? container : null;
    }

    private <T> T onServer(java.util.function.Supplier<T> task) {
        if (server == null || server.isSameThread()) {
            return task.get();
        }
        CompletableFuture<T> done = new CompletableFuture<>();
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
