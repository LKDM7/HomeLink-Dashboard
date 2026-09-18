package fr.lkdm.homelink.dashboard.blockentity;

import fr.lkdm.homelink.dashboard.block.AccessPointBlock;
import fr.lkdm.homelink.dashboard.block.AccessPointStatus;
import fr.lkdm.homelink.dashboard.server.DashboardAccess;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent access point metadata only. HomeCore owns networks and devices. */
public abstract class AccessPointBlockEntity extends BlockEntity {
    private UUID owner;
    private UUID networkId;
    private boolean active = true;

    protected AccessPointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public Optional<UUID> owner() { return Optional.ofNullable(owner); }
    public Optional<UUID> networkId() { return Optional.ofNullable(networkId); }
    public boolean active() { return active; }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
    }

    public void initializeOwner(UUID player) {
        requireServer();
        if (owner == null) { owner = java.util.Objects.requireNonNull(player); setChanged(); }
    }

    /** Trusted server-side mutation; callers must authorize configuration first. */
    public void setNetworkId(UUID networkId) {
        requireServer();
        this.networkId = networkId;
        setChanged();
        DashboardAccess.refreshStatus(this);
    }

    /** Trusted server-side local configuration, never accepted directly from a client. */
    public void setActive(boolean active) {
        requireServer();
        this.active = active;
        setChanged();
        DashboardAccess.refreshStatus(this);
    }

    public void setStatus(AccessPointStatus status) {
        requireServer();
        var state = getBlockState();
        if (state.getValue(AccessPointBlock.STATUS) != status) {
            level.setBlock(worldPosition, state.setValue(AccessPointBlock.STATUS, status), 3);
        }
    }

    private void requireServer() {
        if (level == null || level.isClientSide || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Access point mutations require the owning server thread");
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("Owner", owner);
        if (networkId != null) tag.putUUID("HomeNetwork", networkId);
        tag.putBoolean("Active", active);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        networkId = tag.hasUUID("HomeNetwork") ? tag.getUUID("HomeNetwork") : null;
        active = !tag.contains("Active", 1) || tag.getBoolean("Active");
    }
}
