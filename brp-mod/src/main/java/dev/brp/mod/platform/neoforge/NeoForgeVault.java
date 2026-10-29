package dev.brp.mod.platform.neoforge;

import dev.brp.mod.agent.VaultPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The real dispense (P3-12): containerRef is "dim|x,y,z"; the block entity
 * must be a vanilla Container. Withdraws WHOLE stacks of the bearer item
 * (emeralds — the registry's chosen placeholder for now); any shortfall
 * fails the whole dispense so the mint window's saga can compensate.
 */
public final class NeoForgeVault implements VaultPort {

    private final MinecraftServer server;
    private final String containerRef;

    public NeoForgeVault(MinecraftServer server, String containerRef) {
        this.server = server;
        this.containerRef = containerRef;
    }

    @Override
    public boolean dispense(int stacks) {
        String[] parts = containerRef.split("\\|");
        if (parts.length != 2 || server == null) {
            return false;
        }
        ServerLevel level = levelFor(parts[0]);
        if (level == null) {
            return false;
        }
        String[] xyz = parts[1].split(",");
        if (xyz.length != 3) {
            return false;
        }
        BlockPos pos = new BlockPos(Integer.parseInt(xyz[0].trim()),
                Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim()));
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            return false;
        }
        ItemStack bearer = new ItemStack(Items.EMERALD);
        int remaining = stacks * 64;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack held = container.getItem(slot);
            if (!held.isEmpty() && held.getItem() == bearer.getItem()) {
                int take = Math.min(remaining, held.getCount());
                container.removeItem(slot, take);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private ServerLevel levelFor(String dimension) {
        try {
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.parse(dimension));
            return server.getLevel(key);
        } catch (RuntimeException badId) {
            return null;
        }
    }
}
