package dev.brp.mod.block;

import com.mojang.serialization.MapCodec;
import dev.brp.mod.governor.ChunkGovernor;   // still used by the BE's gate() below
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Dispatch Terminal (调度终端) — the 收发令 station: work orders in,
 * signals out, acks back (docs/09 section 1). Order inbox/outbox UI rides
 * the agent API in P2-10; the block itself refuses duty in HALTed chunks
 * (P2-09): the strike is the product.
 */
public class DispatchTerminalBlock extends BaseEntityBlock {

    public DispatchTerminalBlock() {
        this(Properties.of().strength(2.0f, 8.0f).requiresCorrectToolForDrops());
    }

    public DispatchTerminalBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DispatchTerminalBlock::new);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;   // no BER yet: plain block model renders
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BrpBlocks.DISPATCH_TERMINAL_BE.get().create(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // P2-09: the gate. HALT = the desk is on strike; the player walks on.
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (BrpBlocks.verdictAt(level, pos) == ChunkGovernor.Verdict.HALT) {
            player.displayClientMessage(BrpBlocks.STRIKE_MSG, true);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DispatchTerminalBlockEntity be) {
            player.displayClientMessage(be.statusLine(), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Minimal BE: identity + strike status. Choreography lands with P2-09/P2-10. */
    public static class DispatchTerminalBlockEntity extends BlockEntity {

        public DispatchTerminalBlockEntity(BlockPos pos, BlockState state) {
            super(BrpBlocks.DISPATCH_TERMINAL_BE.get(), pos, state);
        }

        public net.minecraft.network.chat.Component statusLine() {
            int orders = dev.brp.mod.desk.SignalDesk.pendingCount();
            if (orders > 0) {
                return net.minecraft.network.chat.Component.literal(
                        orders + " signal order" + (orders == 1 ? "" : "s") + " pending");
            }
            return net.minecraft.network.chat.Component.translatable(
                    "block.brp.dispatch_terminal.status.idle");
        }

        /** P2-09: the one gate every BRP machine obeys before doing anything. */
        public ChunkGovernor.Verdict gate(ChunkGovernor.ChunkState chunk) {
            return ChunkGovernor.rule(chunk, ChunkGovernor.DEFAULT_GRACE_MINUTES);
        }
    }
}
