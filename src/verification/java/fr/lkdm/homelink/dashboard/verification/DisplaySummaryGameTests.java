package fr.lkdm.homelink.dashboard.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.network.DisplaySummary;
import fr.lkdm.homelink.dashboard.network.DisplaySummaryPayloads;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DisplaySummaryService;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The in-world summary must obey the same server authority as the dashboard menu. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DisplaySummaryGameTests {
    @GameTest(template = "empty")
    public static void permissionsRevocationAndDistance(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.add("Private machine", DeviceStatus.ONLINE);
            var manager = DashboardAPI.networks(helper.getLevel().getServer());
            var viewer = player(helper.getLevel(), fixture.display.getBlockPos());
            helper.assertTrue(fixture.summary().mode() == DisplaySummary.Mode.LIVE,
                    "A nearby owner must see the live facade without opening a menu");
            assertHidden(helper, DisplaySummaryService.describe(viewer, fixture.display), DisplaySummary.Mode.RESTRICTED);
            manager.setMember(fixture.network, viewer.getUUID(), NetworkRole.VIEWER);
            fixture.favorites(viewer, Set.copyOf(fixture.devices));
            helper.assertTrue(DisplaySummaryService.describe(viewer, fixture.display).equals(fixture.summary()),
                    "VIEWER must receive the same permitted summary as the owner");
            manager.removeMember(fixture.network, viewer.getUUID());
            assertHidden(helper, DisplaySummaryService.describe(viewer, fixture.display), DisplaySummary.Mode.RESTRICTED);
            var elsewhere = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel().getServer().getLevel(Level.NETHER),
                    fixture.owner.getGameProfile(), ClientInformation.createDefault());
            elsewhere.setPos(fixture.owner.getX(), fixture.owner.getY(), fixture.owner.getZ());
            assertHidden(helper, DisplaySummaryService.describe(elsewhere, fixture.display), DisplaySummary.Mode.RESTRICTED);
            fixture.owner.setPos(fixture.owner.getX() + 17, fixture.owner.getY(), fixture.owner.getZ());
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.RESTRICTED);
            fixture.owner.setPos(fixture.owner.getX() - 17, fixture.owner.getY(), fixture.owner.getZ());
            helper.getLevel().removeBlock(fixture.display.getBlockPos(), false);
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.RESTRICTED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void countsRespectStatusAndRadioReachability(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.add("Online", DeviceStatus.ONLINE);
            fixture.add("Warning", DeviceStatus.WARNING);
            fixture.add("Error", DeviceStatus.ERROR);
            fixture.add("Offline", DeviceStatus.OFFLINE);
            fixture.add("Disabled", DeviceStatus.DISABLED);
            fixture.add("Unknown", DeviceStatus.UNKNOWN);
            var far = fixture.add("Out of radio range", DeviceStatus.ERROR);
            far.pos = fixture.root.getBlockPos().offset(65, 0, 0);
            far.dimension = helper.getLevel().dimension();
            var otherDimension = fixture.add("Other dimension", DeviceStatus.ERROR);
            otherDimension.pos = fixture.root.getBlockPos();
            otherDimension.dimension = Level.NETHER;
            var partialLocation = fixture.add("Incomplete location", DeviceStatus.ERROR);
            partialLocation.pos = fixture.root.getBlockPos();
            var unloaded = fixture.add("Unregistered device", DeviceStatus.ERROR);
            DashboardAPI.devices(helper.getLevel().getServer()).unregister(unloaded.id());
            var foreign = fixture.add("Removed network member", DeviceStatus.ERROR);
            DashboardAPI.networks(helper.getLevel().getServer()).removeDevice(fixture.network, foreign.id());
            var summary = fixture.summary();
            helper.assertTrue(summary.mode() == DisplaySummary.Mode.LIVE && summary.total() == 6,
                    "Only registered devices reachable from this physical network belong in the summary");
            helper.assertTrue(summary.online() == 1 && summary.attention() == 2 && summary.offline() == 3,
                    "Counts must distinguish online, warning/error and unavailable devices");
            helper.assertTrue(summary.devices().size() == 4 && summary.devices().getFirst().name().equals("Disabled")
                            && summary.devices().get(1).name().equals("Error"),
                    "Favorite rows must retain a stable alphabetical order");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void widgetsKeepTheirSavedLayout(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var device = fixture.add("Widget device", DeviceStatus.ONLINE);
            String secondary = device.metrics().get(1).id().toString();
            var layout = List.of(
                    new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(),
                            fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.METRIC, device.id(), secondary, 6, 0, 6, 3),
                    new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(),
                            fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.DEVICE_SUMMARY, device.id(), "", 0, 0, 6, 3),
                    new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(),
                            fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.DEVICE_SUMMARY, UUID.randomUUID(), "", 0, 3, 12, 3));
            DashboardPreferencesSavedData.get(helper.getLevel().getServer()).put(fixture.owner.getUUID(), fixture.network,
                    new DashboardProfile(layout, Set.of()));
            var widgets = fixture.summary().widgets();
            helper.assertTrue(widgets.size() == 3, "Every saved widget must reach the display");
            var first = widgets.getFirst();
            helper.assertTrue(first.x() == 0 && first.y() == 0 && first.width() == 6 && !first.metric()
                            && first.device().name().equals("Widget device") && first.device().metrics().size() == 2,
                    "Widgets must come in reading order with their saved geometry and summary metrics");
            var metric = widgets.get(1);
            helper.assertTrue(metric.x() == 6 && metric.metric() && metric.device().metrics().size() == 1
                            && metric.device().metrics().getFirst().name().equals("Secondary"),
                    "A metric widget must carry only its chosen metric");
            helper.assertTrue(widgets.get(2).width() == 12 && widgets.get(2).device().name().isEmpty(),
                    "A widget of a missing device must stay in place as unavailable");
            var packet = new DisplaySummaryPayloads.Update(helper.getLevel().dimension().location(), fixture.display.getBlockPos(), fixture.summary());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                DisplaySummaryPayloads.Update.CODEC.encode(buffer, packet);
                helper.assertTrue(DisplaySummaryPayloads.Update.CODEC.decode(buffer).equals(packet) && !buffer.isReadable(),
                        "Widget tiles must survive transport");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void summaryPayloadRoundTripAndBounds(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.add("Codec favorite", DeviceStatus.ONLINE);
            var packet = new DisplaySummaryPayloads.Update(helper.getLevel().dimension().location(),
                    fixture.display.getBlockPos(), fixture.summary());
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                DisplaySummaryPayloads.Update.CODEC.encode(buffer, packet);
                helper.assertTrue(DisplaySummaryPayloads.Update.CODEC.decode(buffer).equals(packet),
                        "Recipient summary, location and live metric values must survive transport");
                helper.assertTrue(!buffer.isReadable(), "Decoding must consume the whole summary packet");
                buffer.clear();
                buffer.writeResourceLocation(packet.dimension());
                buffer.writeBlockPos(packet.master());
                buffer.writeEnum(DisplaySummary.Mode.LIVE);
                buffer.writeUtf("Bounded summary");
                buffer.writeVarInt(5);
                buffer.writeVarInt(5);
                buffer.writeVarInt(0);
                buffer.writeVarInt(0);
                buffer.writeVarInt(5);
                boolean rejected = false;
                try { DisplaySummaryPayloads.Update.CODEC.decode(buffer); }
                catch (IllegalArgumentException expected) { rejected = true; }
                helper.assertTrue(rejected, "A packet claiming more than four rows must be rejected before row allocation");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changesAppearWithoutAnyMenuSession(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var device = fixture.add("Pump", DeviceStatus.ONLINE);
            helper.assertTrue(fixture.owner.containerMenu == fixture.owner.inventoryMenu,
                    "The fixture must not open the dashboard");
            var initial = fixture.summary();
            device.status = DeviceStatus.ERROR;
            device.firstMetric.setValue(87);
            var updated = fixture.summary();
            helper.assertTrue(initial.online() == 1 && updated.online() == 0 && updated.attention() == 1,
                    "A device status change must update the facade without a menu subscription");
            helper.assertTrue(initial.devices().getFirst().metrics().getFirst().value().value().equals(10)
                            && updated.devices().getFirst().metrics().getFirst().value().value().equals(87),
                    "Favorite metric values must refresh without a menu subscription");
            var manager = DashboardAPI.networks(helper.getLevel().getServer());
            manager.renameNetwork(fixture.network, "Renamed network");
            helper.assertTrue(fixture.summary().networkName().equals("Renamed network"),
                    "The facade must reflect an updated network name");
            DashboardAPI.devices(helper.getLevel().getServer()).unregister(device.id());
            helper.assertTrue(fixture.summary().total() == 0 && fixture.summary().devices().isEmpty(),
                    "Unregistering a device must remove stale facade data");
            helper.assertTrue(fixture.owner.containerMenu == fixture.owner.inventoryMenu,
                    "Reading a facade must not create or replace a menu session");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void favoritesArePersonalAndEmptyMeansNoRows(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var pump = fixture.add("Pump", DeviceStatus.ONLINE);
            var furnace = fixture.add("Furnace", DeviceStatus.WARNING);
            var viewer = player(helper.getLevel(), fixture.display.getBlockPos());
            DashboardAPI.networks(helper.getLevel().getServer()).setMember(fixture.network, viewer.getUUID(), NetworkRole.VIEWER);
            var empty = DisplaySummaryService.describe(viewer, fixture.display);
            helper.assertTrue(empty.mode() == DisplaySummary.Mode.LIVE && empty.total() == 0 && empty.devices().isEmpty(),
                    "A viewer without favorites must not inherit another player's device list");
            fixture.favorites(fixture.owner, Set.of(pump.id()));
            fixture.favorites(viewer, Set.of(furnace.id()));
            var ownerSummary = fixture.summary();
            var viewerSummary = DisplaySummaryService.describe(viewer, fixture.display);
            helper.assertTrue(ownerSummary.total() == 1 && ownerSummary.devices().getFirst().name().equals("Pump")
                            && viewerSummary.total() == 1 && viewerSummary.devices().getFirst().name().equals("Furnace"),
                    "Two authorized nearby players must receive their own dashboard favorites");
            fixture.favorites(fixture.owner, Set.of(UUID.randomUUID()));
            helper.assertTrue(fixture.summary().devices().isEmpty(), "Unknown favorite IDs must not reveal unrelated devices");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unboundOfflineAndDeletedNetworkHideData(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.add("Private machine", DeviceStatus.ERROR);
            fixture.display.setNetworkId(null);
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.UNBOUND);
            var stranger = player(helper.getLevel(), fixture.display.getBlockPos());
            assertHidden(helper, DisplaySummaryService.describe(stranger, fixture.display), DisplaySummary.Mode.RESTRICTED);
            fixture.display.setNetworkId(fixture.network);
            fixture.display.setActive(false);
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.OFFLINE);
            fixture.display.setActive(true);
            fixture.root.setActive(false);
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.OFFLINE);
            fixture.root.setActive(true);
            helper.assertTrue(fixture.summary().total() == 1, "Restoring signal must restore live data");
            DashboardAPI.networks(helper.getLevel().getServer()).deleteNetwork(fixture.network);
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.RESTRICTED);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void summaryNamesAndRowsAreBounded(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            for (int index = 0; index < 12; index++) fixture.add("Machine " + index + "x".repeat(256), DeviceStatus.ONLINE);
            var summary = fixture.summary();
            helper.assertTrue(summary.total() == 12 && summary.devices().size() == 4,
                    "Four visible rows must not truncate the reachable favorite count");
            helper.assertTrue(summary.networkName().length() <= 128
                            && summary.devices().stream().allMatch(line -> line.name().length() <= 128),
                    "Network and device labels must remain within the bounded transport limit");
            helper.assertTrue(summary.devices().stream().allMatch(line -> line.metrics().size() == 2
                            && line.metrics().getFirst().name().equals("Primary") && line.metrics().get(1).name().equals("Secondary")),
                    "Each favorite must expose at most the first two provider metrics");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "energy")
    public static void powerLossHidesFacadeUntilPowerReturns(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.add("Powered machine", DeviceStatus.ONLINE);
            fixture.root.energyPort().insert(Long.MAX_VALUE, false);
            fixture.root.drawEnergy();
            helper.assertTrue(!fixture.display.powered(), "This test must begin with an unpowered display");
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.OFFLINE);
            fixture.display.energyPort().insert(Long.MAX_VALUE, false);
            fixture.display.drawEnergy();
            helper.assertTrue(fixture.summary().mode() == DisplaySummary.Mode.LIVE && fixture.summary().total() == 1,
                    "Powering the display must expose authorized data without a menu");
            fixture.display.energyPort().setStored(0);
            fixture.display.drawEnergy();
            assertHidden(helper, fixture.summary(), DisplaySummary.Mode.OFFLINE);
        }
        helper.succeed();
    }

    private static void assertHidden(GameTestHelper helper, DisplaySummary summary, DisplaySummary.Mode mode) {
        helper.assertTrue(summary.mode() == mode, "Expected " + mode + ", got " + summary.mode());
        helper.assertTrue(summary.networkName().isEmpty() && summary.total() == 0 && summary.online() == 0
                        && summary.attention() == 0 && summary.offline() == 0 && summary.devices().isEmpty(),
                "An unavailable or unauthorized facade must expose no network name, count or device");
    }

    private static ServerPlayer player(ServerLevel level, BlockPos pos) {
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "SummaryTest"), ClientInformation.createDefault());
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return player;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerPlayer owner;
        private final UUID network;
        private final DashboardDisplayBlockEntity display;
        private final AccessPointBlockEntity root;
        private final List<UUID> devices = new ArrayList<>();
        private final List<UUID> profileOwners = new ArrayList<>();

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            var pos = helper.absolutePos(new BlockPos(1, 1, 1));
            owner = player(helper.getLevel(), pos);
            network = DashboardAPI.networks(helper.getLevel().getServer()).createNetwork("Private network", owner.getUUID()).id();
            var displayBlock = DashboardRegistries.DASHBOARD_DISPLAY.get();
            helper.getLevel().setBlock(pos, displayBlock.defaultBlockState(), 3);
            displayBlock.setPlacedBy(helper.getLevel(), pos, displayBlock.defaultBlockState(), owner, new ItemStack(displayBlock));
            display = (DashboardDisplayBlockEntity) helper.getLevel().getBlockEntity(pos);
            var rootPos = pos.offset(3, 0, 0);
            var serverBlock = DashboardRegistries.HOME_SERVER.get();
            helper.getLevel().setBlock(rootPos, serverBlock.defaultBlockState(), 3);
            serverBlock.setPlacedBy(helper.getLevel(), rootPos, serverBlock.defaultBlockState(), owner, new ItemStack(serverBlock));
            root = (AccessPointBlockEntity) helper.getLevel().getBlockEntity(rootPos);
            root.setNetworkId(network);
            display.setNetworkId(network);
        }

        private FixtureDevice add(String name, DeviceStatus status) {
            var device = new FixtureDevice(name, status);
            DashboardAPI.devices(helper.getLevel().getServer()).register(device);
            DashboardAPI.networks(helper.getLevel().getServer()).addDevice(network, device.id());
            devices.add(device.id());
            favorites(owner, Set.copyOf(devices));
            return device;
        }

        private void favorites(ServerPlayer player, Set<UUID> favorites) {
            DashboardPreferencesSavedData.get(helper.getLevel().getServer()).put(player.getUUID(), network,
                    new DashboardProfile(List.of(), favorites));
            if (!profileOwners.contains(player.getUUID())) profileOwners.add(player.getUUID());
        }

        private DisplaySummary summary() { return DisplaySummaryService.describe(owner, display); }

        @Override public void close() {
            for (UUID player : profileOwners) DashboardPreferencesSavedData.get(helper.getLevel().getServer())
                    .put(player, network, DashboardProfile.EMPTY);
            for (UUID device : devices) DashboardAPI.devices(helper.getLevel().getServer()).unregister(device);
            DashboardAPI.networks(helper.getLevel().getServer()).deleteNetwork(network);
            helper.getLevel().removeBlock(display.getBlockPos(), false);
            helper.getLevel().removeBlock(root.getBlockPos(), false);
        }
    }

    private static final class FixtureDevice implements DashboardDevice {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private DeviceStatus status;
        private BlockPos pos;
        private ResourceKey<Level> dimension;
        private final DeviceMetric<Integer> firstMetric = metric("primary", "Primary", 10);
        private final List<DeviceMetric<?>> metrics = List.of(firstMetric,
                metric("secondary", "Secondary", 20), metric("third", "Third", 30));

        private FixtureDevice(String name, DeviceStatus status) { this.name = name; this.status = status; }
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.parse("dashboard_validation:display_summary"); }
        @Override public Component displayName() { return Component.literal(name); }
        @Override public DeviceStatus status() { return status; }
        @Override public List<DeviceMetric<?>> metrics() { return metrics; }
        @Override public Optional<BlockPos> position() { return Optional.ofNullable(pos); }
        @Override public Optional<ResourceKey<Level>> dimension() { return Optional.ofNullable(dimension); }
        private static DeviceMetric<Integer> metric(String id, String name, int initial) {
            return DeviceMetric.builder(ResourceLocation.fromNamespaceAndPath("dashboard_validation", id),
                    Component.literal(name), MetricTypes.INTEGER, initial).build();
        }
    }
}
