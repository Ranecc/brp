package dev.brp.mod.block;

import dev.brp.mod.BrpMod;
import dev.brp.mod.governor.ChunkGovernor;
import dev.brp.mod.governor.GovernorFeed;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry foundation for the shop-floor terminals (docs/09 section 1).
 * IDs stay boring on purpose: plumbing is not a punchline. The personality
 * budget is spent in lang files and nowhere else.
 */
public final class BrpBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(BrpMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(BrpMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BrpMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BrpMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BrpMod.MOD_ID);

    public static final DeferredHolder<Block, DispatchTerminalBlock> DISPATCH_TERMINAL =
            BLOCKS.register("dispatch_terminal", id -> new DispatchTerminalBlock());
    public static final DeferredHolder<Block, LineControllerBlock> LINE_CONTROLLER =
            BLOCKS.register("line_controller", id -> new LineControllerBlock());
    public static final DeferredHolder<Block, FormTerminalBlock> FORM_TERMINAL =
            BLOCKS.register("form_terminal", FormTerminalBlock::new);

    public static final DeferredItem<BlockItem> DISPATCH_TERMINAL_ITEM =
            ITEMS.registerSimpleBlockItem(DISPATCH_TERMINAL);
    public static final DeferredItem<BlockItem> LINE_CONTROLLER_ITEM =
            ITEMS.registerSimpleBlockItem(LINE_CONTROLLER);
    public static final DeferredItem<BlockItem> FORM_TERMINAL_ITEM =
            ITEMS.registerSimpleBlockItem(FORM_TERMINAL);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DispatchTerminalBlock.DispatchTerminalBlockEntity>> DISPATCH_TERMINAL_BE =
            BLOCK_ENTITIES.register("dispatch_terminal",
                    () -> BlockEntityType.Builder.of(DispatchTerminalBlock.DispatchTerminalBlockEntity::new,
                            DISPATCH_TERMINAL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LineControllerBlock.LineControllerBlockEntity>> LINE_CONTROLLER_BE =
            BLOCK_ENTITIES.register("line_controller",
                    () -> BlockEntityType.Builder.of(LineControllerBlock.LineControllerBlockEntity::new,
                            LINE_CONTROLLER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<FormTerminalMenu>> FORM_TERMINAL_MENU =
            MENUS.register("form_terminal", () -> IMenuTypeExtension.create(
                    (id, inv, buf) -> new FormTerminalMenu(id, inv)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TERMINALS_TAB =
            TABS.register("terminals", () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.brp.terminals"))
                    .icon(() -> new ItemStack(FORM_TERMINAL_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(DISPATCH_TERMINAL_ITEM.get());
                        output.accept(LINE_CONTROLLER_ITEM.get());
                        output.accept(FORM_TERMINAL_ITEM.get());
                    })
                    .build());

    /** What every BRP machine says when the governor rules HALT (docs/09 section 4). */
    public static final net.minecraft.network.chat.Component STRIKE_MSG =
            net.minecraft.network.chat.Component.translatable("block.brp.strike");

    /** The one gate every terminal passes before duty: the governor's cached verdict. */
    public static ChunkGovernor.Verdict verdictAt(net.minecraft.world.level.Level level,
                                                  net.minecraft.core.BlockPos pos) {
        String dimension = level.dimension().location().toString();
        String chunkKey = (pos.getX() >> 4) + "," + (pos.getZ() >> 4);
        return GovernorFeed.verdictFor(dimension, chunkKey);
    }

    private BrpBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
    }
}
