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

    /** P2-15 runtime wiring: the gate runs on the server tick, cheaply. */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type != BrpBlocks.LINE_CONTROLLER_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof LineControllerBlockEntity controller) {
                controller.serverTick(lvl, pos);
            }
        };
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

    /**
     * Program slot + gate (P2-11 model, P2-09 verdict, P2-15 rate): the BE
     * samples the governor verdict and the APS factor once a second and
     * names what it is doing. Program execution rides PipelineProgram when
     * the richer feeds (energy/price windows) arrive with P3.
     */
    public static class LineControllerBlockEntity extends BlockEntity {

        private long window;
        private String mode = "IDLE";
        private ChunkGovernor.Verdict verdict = ChunkGovernor.Verdict.RUN;

        public LineControllerBlockEntity(BlockPos pos, BlockState state) {
            super(BrpBlocks.LINE_CONTROLLER_BE.get(), pos, state);
        }

        void serverTick(Level level, BlockPos pos) {
            if (level.isClientSide || level.getGameTime() % 20 != 0) {
                return;   // once a second: the gate is cheap, not free
            }
            window++;
            verdict = BrpBlocks.verdictAt(level, pos);
            mode = dev.brp.mod.line.LineGate.decide(verdict,
                    dev.brp.mod.line.ApsFeed.factor(), window).name();
        }

        public Component statusLine() {
            return switch (mode) {
                case "RUNNING" -> Component.literal("line: RUNNING (rate "
                        + Math.round(dev.brp.mod.line.LineGate.effectiveRate(
                                verdict, dev.brp.mod.line.ApsFeed.factor()) * 100) + "%)");
                case "THROTTLED" -> Component.literal("line: THROTTLED (fair-share window skipped)");
                case "ON_STRIKE" -> BrpBlocks.STRIKE_MSG;
                default -> Component.translatable("block.brp.line_controller.status.unprogrammed");
            };
        }

        public ChunkGovernor.Verdict gate(ChunkGovernor.ChunkState chunk) {
            return ChunkGovernor.rule(chunk, ChunkGovernor.DEFAULT_GRACE_MINUTES);
        }
    }
}
