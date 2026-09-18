package fr.lkdm.homelink.dashboard.blockentity;

import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class SignalRepeaterBlockEntity extends AccessPointBlockEntity {
    public SignalRepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(DashboardRegistries.REPEATER_ENTITY.get(), pos, state);
    }
}
