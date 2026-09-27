package fr.lkdm.homelink.dashboard.blockentity;

import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class HomeServerBlockEntity extends AccessPointBlockEntity {
    public HomeServerBlockEntity(BlockPos pos, BlockState state) {
        super(DashboardRegistries.SERVER_ENTITY.get(), pos, state);
    }

    @Override protected long energyPerMinute() {
        return fr.lkdm.homelink.dashboard.config.DashboardEnergyConfig.get(fr.lkdm.homelink.dashboard.config.DashboardEnergyConfig.SERVER_ENERGY);
    }
}
