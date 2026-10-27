package dev.brp.mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Form Terminal (表单终端) — the only place a player fills paperwork in
 * game, using NATIVE Minecraft UI (docs/09 section 2): right-click opens
 * a vanilla container screen fed by FormTerminalMenu. Submission travels
 * the same REST endpoints as the web console — one API, two faces. The
 * form list is a P2-10 fetch away from being live.
 */
public class FormTerminalBlock extends Block {

    public static final Component TITLE = Component.translatable("screen.brp.form_terminal");

    public FormTerminalBlock() {
        super(Properties.of().strength(2.0f, 8.0f).requiresCorrectToolForDrops());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The desk checks credentials with the governor first: HALT means
        // the paperwork is on strike too (docs/09 section 4).
        if (BrpBlocks.verdictAt(level, pos) == dev.brp.mod.governor.ChunkGovernor.Verdict.HALT) {
            player.displayClientMessage(BrpBlocks.STRIKE_MSG, true);
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new FormTerminalMenu(id, inv), TITLE));
        return InteractionResult.SUCCESS;
    }
}
