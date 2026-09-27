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
    /** HomeLink Energy received through the HomeCore energy capability. */
    private final fr.lkdm.homecore.api.energy.EnergyBuffer energy = new fr.lkdm.homecore.api.energy.EnergyBuffer(() -> energyPerMinute() * 2, this::setChanged);
    private boolean powered;

    protected AccessPointBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    public Optional<UUID> owner() { return Optional.ofNullable(owner); }
    public Optional<UUID> networkId() { return Optional.ofNullable(networkId); }
    public boolean active() { return active; }

    /** HE per minute this block uses while active, from the server config; 0 means no energy port. */
    protected abstract long energyPerMinute();
    /** Energy port exposed on every face, or null when this block needs no energy. */
    public fr.lkdm.homecore.api.energy.EnergyBuffer energyPort() { return energyPerMinute() > 0 ? energy : null; }
    /** Whether the last check found enough HE. */
    public boolean powered() { return powered || energyPerMinute() <= 0; }
    /** Switched on by its owner and powered: only then does it emit, relay or show anything. */
    public boolean working() { return active && powered(); }

    /** Server: pays one second of running cost; called once a second by the block's scheduled tick. */
    public void drawEnergy() {
        requireServer();
        boolean now = energy.draw(energyPerMinute(), 60, level.getGameTime() / 20);
        if (now != powered) {
            powered = now;
            setChanged();
            fr.lkdm.homelink.dashboard.server.RadioNetworkService.changed(this);
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            fr.lkdm.homelink.dashboard.server.RadioNetworkService.changed(this);
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override public void setRemoved() {
        fr.lkdm.homelink.dashboard.server.RadioNetworkService.removed(this);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        fr.lkdm.homelink.dashboard.server.RadioNetworkService.removed(this);
        super.onChunkUnloaded();
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
        fr.lkdm.homelink.dashboard.server.RadioNetworkService.changed(this);
        DashboardAccess.refreshStatus(this);
    }

    /** Trusted server-side local configuration, never accepted directly from a client. */
    public void setActive(boolean active) {
        requireServer();
        this.active = active;
        setChanged();
        fr.lkdm.homelink.dashboard.server.RadioNetworkService.changed(this);
        DashboardAccess.refreshStatus(this);
    }

    public void setStatus(AccessPointStatus status) {
        requireServer();
        var state = getBlockState();
        if (state.getBlock() instanceof fr.lkdm.homelink.dashboard.block.DashboardDisplayBlock display) {
            display.synchronizeStatus(level, worldPosition, state, status);
            return;
        }
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
        energy.save(tag, "Energy");
        tag.putBoolean("Powered", powered);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        networkId = tag.hasUUID("HomeNetwork") ? tag.getUUID("HomeNetwork") : null;
        active = !tag.contains("Active", 1) || tag.getBoolean("Active");
        energy.load(tag, "Energy");
        powered = tag.getBoolean("Powered");
    }
}
