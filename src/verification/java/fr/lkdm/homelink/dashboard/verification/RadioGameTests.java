package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.RadioNetworkService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadioGameTests {
    @GameTest(template = "empty")
    public static void distanceChainBreakDimensionAndPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var networks = DashboardAPI.networks(level.getServer());
        var registry = DashboardAPI.devices(level.getServer());
        UUID network = networks.createNetwork("Radio fixture", UUID.randomUUID()).id();
        BlockPos base = helper.absolutePos(new BlockPos(1, 81, 1));
        BlockPos first = base.offset(64, 0, 0), second = base.offset(128, 0, 0);
        var root = place(helper, base, DashboardRegistries.HOME_SERVER.get(), network);
        var device = new LocatedDevice(UUID.randomUUID(), base.offset(64, 0, 0), level.dimension());
        registry.register(device); networks.addDevice(network, device.id());
        helper.assertTrue(networks.isReachable(network, device), "64 blocks must be inclusive");
        device.pos = base.offset(65, 0, 0);
        helper.assertTrue(!networks.isReachable(network, device), "65 blocks must be out of range");
        device.pos = base.offset(46, 46, 0);
        helper.assertTrue(!networks.isReachable(network, device), "Range must be Euclidean in three dimensions");
        var relay1 = place(helper, first, DashboardRegistries.SIGNAL_REPEATER.get(), network);
        var relay2 = place(helper, second, DashboardRegistries.SIGNAL_REPEATER.get(), network);
        device.pos = base.offset(192, 0, 0);
        helper.assertTrue(networks.isReachable(network, device), "Two repeaters must extend the signal to 192 blocks");
        device.dimension = Level.NETHER;
        helper.assertTrue(!networks.isReachable(network, device), "Signal must not cross dimensions");
        device.dimension = level.dimension();
        relay1.setActive(false);
        helper.assertTrue(!networks.isReachable(network, device), "Disabled middle relay must break the chain immediately");
        relay1.setActive(true);
        helper.assertTrue(networks.isReachable(network, device), "Restoring relay must restore reachability");
        var saved = relay1.saveWithFullMetadata(level.registryAccess());
        level.removeBlock(first, false);
        helper.assertTrue(!networks.isReachable(network, device), "Broken relay must block immediately");
        relay1 = place(helper, first, DashboardRegistries.SIGNAL_REPEATER.get(), network);
        relay1.loadWithComponents(saved, level.registryAccess());
        RadioNetworkService.changed(relay1);
        helper.assertTrue(relay1.networkId().orElseThrow().equals(network) && networks.isReachable(network, device), "Relay association must survive NBT reload");
        relay1.onChunkUnloaded();
        helper.assertTrue(!networks.isReachable(network, device), "Unloaded relay must not transmit");
        relay1.onLoad();
        helper.assertTrue(networks.isReachable(network, device), "Reloaded relay must transmit again");
        root.setActive(false);
        helper.assertTrue(!networks.isReachable(network, device) && !RadioNetworkService.hasSignal(relay2), "Relay islands must not generate signal");
        root.setActive(true);
        UUID other = networks.createNetwork("Other radio fixture", UUID.randomUUID()).id();
        relay1.setNetworkId(other);
        helper.assertTrue(!networks.isReachable(network, device), "Other networks must not bridge the signal");
        level.removeBlock(base, false); level.removeBlock(first, false); level.removeBlock(second, false);
        helper.assertTrue(!networks.isReachable(network, device), "Removing every transmitter must not restore unlimited range");
        var persisted = fr.lkdm.homelink.dashboard.server.RadioNetworksSavedData.get(level.getServer())
                .save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
        helper.assertTrue(fr.lkdm.homelink.dashboard.server.RadioNetworksSavedData.load(persisted).contains(network),
                "A physical network must remain marked after saved-data reload without any transmitter");
        registry.unregister(device.id()); networks.deleteNetwork(network); networks.deleteNetwork(other);
        helper.succeed();
    }

    private static AccessPointBlockEntity place(GameTestHelper helper, BlockPos position, Block block, UUID network) {
        helper.getLevel().getChunkAt(position); // Test fixture only: production never loads chunks.
        helper.getLevel().setBlock(position, block.defaultBlockState(), 3);
        var point = (AccessPointBlockEntity) helper.getLevel().getBlockEntity(position);
        point.setNetworkId(network);
        return point;
    }
    private static final class LocatedDevice implements DashboardDevice {
        private final UUID id;
        private BlockPos pos;
        private ResourceKey<Level> dimension;
        LocatedDevice(UUID id, BlockPos pos, ResourceKey<Level> dimension) { this.id = id; this.pos = pos; this.dimension = dimension; }
        public UUID id() { return id; }
        public ResourceLocation deviceType() { return ResourceLocation.parse("unknown_integration:radio_fixture"); }
        public Component displayName() { return Component.literal("Radio fixture"); }
        public DeviceStatus status() { return DeviceStatus.ONLINE; }
        public Optional<BlockPos> position() { return Optional.of(pos); }
        public Optional<ResourceKey<Level>> dimension() { return Optional.of(dimension); }
    }
}
