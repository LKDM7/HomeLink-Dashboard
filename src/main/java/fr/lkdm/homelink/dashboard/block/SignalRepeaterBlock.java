package fr.lkdm.homelink.dashboard.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.dashboard.blockentity.SignalRepeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SignalRepeaterBlock extends AccessPointBlock {
    public static final MapCodec<SignalRepeaterBlock> CODEC = simpleCodec(SignalRepeaterBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 5, 14),
            Block.box(4, 5, 6, 6, 16, 8), Block.box(10, 5, 6, 12, 16, 8));
    private static final VoxelShape EAST = Shapes.or(Block.box(2, 0, 2, 14, 5, 14),
            Block.box(8, 5, 4, 10, 16, 6), Block.box(8, 5, 10, 10, 16, 12));
    private static final VoxelShape SOUTH = Shapes.or(Block.box(2, 0, 2, 14, 5, 14),
            Block.box(4, 5, 8, 6, 16, 10), Block.box(10, 5, 8, 12, 16, 10));
    private static final VoxelShape WEST = Shapes.or(Block.box(2, 0, 2, 14, 5, 14),
            Block.box(6, 5, 4, 8, 16, 6), Block.box(6, 5, 10, 8, 16, 12));
    public SignalRepeaterBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<SignalRepeaterBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SignalRepeaterBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) { case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST; default -> SHAPE; };
    }
}
