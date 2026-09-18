package fr.lkdm.homelink.dashboard.blockentity;

import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class DashboardDisplayBlockEntity extends AccessPointBlockEntity {
    public DashboardDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(DashboardRegistries.DISPLAY_ENTITY.get(), pos, state);
    }
}
