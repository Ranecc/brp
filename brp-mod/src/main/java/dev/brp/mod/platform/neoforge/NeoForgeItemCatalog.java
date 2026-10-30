package dev.brp.mod.platform.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/**
 * The registry side of the catalog push (docs/14 §8, P5-09): every
 * registered item id, vanilla and modded alike — mod items are first-class
 * bourse instruments from the moment the server boots. ◆ BuiltInRegistries.ITEM
 * verified against 1.21.1 (the 1.20.5 registry split moved it here).
 */
public final class NeoForgeItemCatalog {

    private NeoForgeItemCatalog() {}

    public static List<String> ids() {
        return BuiltInRegistries.ITEM.keySet().stream()
                .map(Object::toString)
                .sorted()
                .toList();
    }
}
