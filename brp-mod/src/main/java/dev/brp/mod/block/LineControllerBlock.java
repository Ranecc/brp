package dev.brp.mod.block;

import dev.brp.mod.governor.ChunkGovernor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Line Controller (产线控制器) — the choreography executor: pipeline
 * program, conditional boot, rate caps (docs/09 sections 1 and 5).
 * The program model and condition evaluator are P2-11; this skeleton
 * carries the BE and the governor gate every execution path must pass.
 */
public class LineControllerBlock extends BaseEntityBlock {

    public LineControllerBlock() {
        this(Properties.of().strength(2.5f, 8.0f).requiresCorrectToolForDrops());
    }

    public LineControllerBlock(Properties props) {
        super(props);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(LineControllerBlock::new);
    }

    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BrpBlocks.LINE_CONTROLLER_BE.get().create(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (BrpBlocks.verdictAt(level, pos) == ChunkGovernor.Verdict.HALT) {
            player.displayClientMessage(BrpBlocks.STRIKE_MSG, true);
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof LineControllerBlockEntity be) {
            player.displayClientMessage(be.statusLine(), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Program slot + gate. P2-11 fills the program; P2-09 wires the verdict. */
    public static class LineControllerBlockEntity extends BlockEntity {

        public LineControllerBlockEntity(BlockPos pos, BlockState state) {
            super(BrpBlocks.LINE_CONTROLLER_BE.get(), pos, state);
        }

        public Component statusLine() {
            return Component.translatable("block.brp.line_controller.status.unprogrammed");
        }

        public ChunkGovernor.Verdict gate(ChunkGovernor.ChunkState chunk) {
            return ChunkGovernor.rule(chunk, ChunkGovernor.DEFAULT_GRACE_MINUTES);
        }
    }
}
