package fr.lkdm.homelink.dashboard.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DashboardDisplayBlock extends AccessPointBlock {
    public static final MapCodec<DashboardDisplayBlock> CODEC = simpleCodec(DashboardDisplayBlock::new);
    private static final VoxelShape NORTH = Block.box(0, 2, 12.875, 16, 14, 16);
    private static final VoxelShape SOUTH = Block.box(0, 2, 0, 16, 14, 3.125);
    private static final VoxelShape EAST = Block.box(0, 2, 0, 3.125, 14, 16);
    private static final VoxelShape WEST = Block.box(12.875, 2, 0, 16, 14, 16);

    public DashboardDisplayBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<DashboardDisplayBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DashboardDisplayBlockEntity(pos, state);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var face = context.getClickedFace();
        return defaultBlockState().setValue(FACING, face.getAxis().isHorizontal()
                ? face : context.getHorizontalDirection().getOpposite());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }
}
