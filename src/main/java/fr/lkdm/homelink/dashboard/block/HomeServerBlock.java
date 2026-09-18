package fr.lkdm.homelink.dashboard.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.dashboard.blockentity.HomeServerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class HomeServerBlock extends AccessPointBlock {
    public static final MapCodec<HomeServerBlock> CODEC = simpleCodec(HomeServerBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public HomeServerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }
    @Override protected MapCodec<HomeServerBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new HomeServerBlockEntity(pos, state) : null;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var pos = context.getClickedPos();
        return pos.getY() < context.getLevel().getMaxBuildHeight() - 1
                && context.getLevel().getBlockState(pos.above()).canBeReplaced(context)
                ? super.getStateForPlacement(context) : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        var half = state.getValue(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            if (!neighbor.is(this) || neighbor.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
            // Only the lower half owns authoritative state and the HomeCore association.
            if (half == DoubleBlockHalf.UPPER) return neighbor.setValue(HALF, DoubleBlockHalf.UPPER);
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state, level, pos))) {
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                var lowerPos = pos.below();
                var lower = level.getBlockState(lowerPos);
                if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    level.levelEvent(player, 2001, lowerPos, Block.getId(lower));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        var base = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return super.useWithoutItem(level.getBlockState(base), level, base, player, hit);
    }
}
