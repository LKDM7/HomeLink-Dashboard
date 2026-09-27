package fr.lkdm.homelink.dashboard.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.network.DiscoveryPayloads;
import fr.lkdm.homelink.dashboard.network.MachineListing;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.MachineDiscoveryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The "Add" tab: machines in the radio area, their binding as seen by the player, and additions through HomeCore. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineDiscoveryGameTests {
    @GameTest(template = "empty")
    public static void listsMachinesInRadioRangeAndAddsThem(GameTestHelper helper) {
        var level = helper.getLevel();
        var networks = DashboardAPI.networks(level.getServer());
        var registry = DashboardAPI.devices(level.getServer());
        var owner = player(helper, "discovery_owner");
        var stranger = UUID.randomUUID();
        UUID home = networks.createNetwork("Base", owner.getUUID()).id();
        UUID workshop = networks.createNetwork("Atelier", owner.getUUID()).id();
        UUID foreign = networks.createNetwork("Voisin", stranger).id();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        var server = place(helper, base, home);
        owner.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 2.5);

        var free = new Machine("Éolienne nord", base.offset(10, 0, 0), level.dimension(), true);
        var moved = new Machine("Batterie atelier", base.offset(20, 0, 0), level.dimension(), true);
        var theirs = new Machine("Carrière du voisin", base.offset(30, 0, 0), level.dimension(), false);
        var far = new Machine("Hors de portée", base.offset(70, 0, 0), level.dimension(), true);
        var elsewhere = new Machine("Autre dimension", base.offset(5, 0, 0), Level.NETHER, true);
        var plain = new Plain(base.offset(8, 0, 0), level.dimension());
        var devices = new ArrayList<DashboardDevice>(List.of(free, moved, theirs, far, elsewhere, plain));
        try {
            devices.forEach(registry::register);
            bind(networks, moved, workshop);
            bind(networks, theirs, foreign);

            var listing = MachineDiscoveryService.describe(owner, server);
            helper.assertTrue(listing.code() == ActionResult.Code.SUCCESS, "Owner listing refused: " + listing.code());
            helper.assertTrue(listing.total() == 4 && find(listing, far.id).isEmpty() && find(listing, elsewhere.id).isEmpty(),
                    "Only machines inside the radio area and dimension may be listed");
            var freeEntry = find(listing, free.id).orElseThrow();
            helper.assertTrue(freeEntry.state() == MachineListing.State.FREE && freeEntry.canAdd() && freeEntry.name().equals("Éolienne nord")
                    && freeEntry.distance() == 10, "Free machine: " + freeEntry);
            var movedEntry = find(listing, moved.id).orElseThrow();
            helper.assertTrue(movedEntry.state() == MachineListing.State.OTHER_NETWORK && movedEntry.networkName().equals("Atelier") && movedEntry.canAdd(),
                    "A machine on another of the player's networks can be moved: " + movedEntry);
            var theirEntry = find(listing, theirs.id).orElseThrow();
            helper.assertTrue(theirEntry.state() == MachineListing.State.OTHER_NETWORK && theirEntry.networkName().isEmpty() && !theirEntry.canAdd(),
                    "Another player's machine must be locked and its network name hidden: " + theirEntry);
            helper.assertTrue(find(listing, plain.id).orElseThrow().state() == MachineListing.State.UNSUPPORTED, "Devices without NetworkMember are listed as not compatible");
            helper.assertTrue(listing.entries().getFirst().canAdd(), "Addable machines come first");

            helper.assertTrue(MachineDiscoveryService.add(owner, server, Optional.of(free.id)) == ActionResult.Code.SUCCESS
                    && free.network.equals(Optional.of(home)) && networks.getDevices(home).contains(free.id), "Adding one machine must bind block and network");
            helper.assertTrue(MachineDiscoveryService.add(owner, server, Optional.of(theirs.id)) == ActionResult.Code.DENIED
                    && theirs.network.equals(Optional.of(foreign)), "Another player's machine must not move");
            helper.assertTrue(MachineDiscoveryService.add(owner, server, Optional.of(far.id)) == ActionResult.Code.DEVICE_OFFLINE, "Out of range machines cannot be added");
            helper.assertTrue(MachineDiscoveryService.add(owner, server, Optional.empty()) == ActionResult.Code.SUCCESS
                    && moved.network.equals(Optional.of(home)) && !networks.getDevices(workshop).contains(moved.id), "Add all must move the addable machines");
            var after = MachineDiscoveryService.describe(owner, server);
            helper.assertTrue(find(after, free.id).orElseThrow().state() == MachineListing.State.IN_NETWORK
                    && after.entries().stream().noneMatch(MachineListing.Entry::canAdd), "Nothing left to add");

            var viewer = player(helper, "discovery_viewer");
            viewer.setPos(owner.getX(), owner.getY(), owner.getZ());
            networks.setMember(home, viewer.getUUID(), NetworkRole.VIEWER);
            helper.assertTrue(MachineDiscoveryService.describe(viewer, server).code() == ActionResult.Code.DENIED,
                    "Without MANAGE_NETWORK the nearby machines, including other players' blocks, stay hidden");
            helper.assertTrue(DiscoveryPayloads.handle(owner, new DiscoveryPayloads.Request(7, DiscoveryPayloads.Operation.LIST, Optional.empty()))
                    .result() == ActionResult.Code.DENIED, "Packets without a live Dashboard menu must be denied");
            helper.succeed();
        } finally {
            devices.forEach(device -> registry.unregister(device.id()));
            networks.deleteNetwork(home);
            networks.deleteNetwork(workshop);
            networks.deleteNetwork(foreign);
        }
    }

    private static Optional<MachineListing.Entry> find(MachineListing listing, UUID id) {
        return listing.entries().stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    private static void bind(fr.lkdm.homecore.api.network.HomeNetworkManager networks, Machine machine, UUID network) {
        networks.addDevice(network, machine.id);
        machine.network = Optional.of(network);
    }

    private static AccessPointBlockEntity place(GameTestHelper helper, BlockPos position, UUID network) {
        helper.getLevel().setBlock(position, DashboardRegistries.HOME_SERVER.get().defaultBlockState(), 3);
        var point = (AccessPointBlockEntity) helper.getLevel().getBlockEntity(position);
        point.setNetworkId(network);
        return point;
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name), ClientInformation.createDefault());
    }

    /** A machine block recording its own network, like HomeLink Energy, Farm and Quarry do. */
    private static final class Machine implements DashboardDevice, NetworkMember {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final BlockPos pos;
        private final ResourceKey<Level> dimension;
        private final boolean configurable;
        private Optional<UUID> network = Optional.empty();
        Machine(String name, BlockPos pos, ResourceKey<Level> dimension, boolean configurable) {
            this.name = name; this.pos = pos; this.dimension = dimension; this.configurable = configurable;
        }
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.parse("homelink_energy:wind_turbine"); }
        @Override public Component displayName() { return Component.literal(name); }
        @Override public DeviceStatus status() { return DeviceStatus.ONLINE; }
        @Override public Optional<BlockPos> position() { return Optional.of(pos); }
        @Override public Optional<ResourceKey<Level>> dimension() { return Optional.of(dimension); }
        @Override public Optional<UUID> homeNetwork() { return network; }
        @Override public Optional<UUID> owner() { return Optional.empty(); }
        @Override public boolean canConfigure(ServerPlayer player) { return configurable; }
        @Override public void homeNetworkChanged(Optional<HomeNetwork> value) { network = value.map(HomeNetwork::id); }
    }

    /** A located device that does not record its network (for example HomeLink Storage). */
    private record Plain(UUID id, BlockPos pos, ResourceKey<Level> level) implements DashboardDevice {
        Plain(BlockPos pos, ResourceKey<Level> level) { this(UUID.randomUUID(), pos, level); }
        @Override public ResourceLocation deviceType() { return ResourceLocation.parse("homelink_storage:storage_controller"); }
        @Override public Component displayName() { return Component.literal("Stockage"); }
        @Override public DeviceStatus status() { return DeviceStatus.ONLINE; }
        @Override public Optional<BlockPos> position() { return Optional.of(pos); }
        @Override public Optional<ResourceKey<Level>> dimension() { return Optional.of(level); }
    }
}
