package fr.lkdm.homelink.dashboard.blockentity;

import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public final class DashboardDisplayBlockEntity extends AccessPointBlockEntity {
    /** Whether every viewer sees the owner's Home instead of their own; kept by the master cell. */
    private boolean sharedLayout;

    public DashboardDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(DashboardRegistries.DISPLAY_ENTITY.get(), pos, state);
    }

    @Override protected long energyPerMinute() {
        return fr.lkdm.homelink.dashboard.config.DashboardEnergyConfig.get(fr.lkdm.homelink.dashboard.config.DashboardEnergyConfig.DISPLAY_ENERGY);
    }

    public boolean sharedLayout() { return sharedLayout; }

    public void setSharedLayout(boolean value) {
        if (sharedLayout == value) return;
        sharedLayout = value;
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (sharedLayout) tag.putBoolean("SharedLayout", true);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        sharedLayout = tag.getBoolean("SharedLayout");
    }
}
