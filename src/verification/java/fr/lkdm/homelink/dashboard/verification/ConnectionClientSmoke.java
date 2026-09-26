package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real integrated client/server verification, deliberately excluded from the release artifact. */
@EventBusSubscriber(modid = "homelink_dashboard_validation", value = Dist.CLIENT)
public final class ConnectionClientSmoke {
    private static final ResourceLocation PROGRESS = ResourceLocation.fromNamespaceAndPath("unknown_integration", "progress");
    private static volatile BlockPos position;
    private static volatile UUID network;
    private static volatile Throwable serverFailure;
    private static DashboardClientState state;
    private static UUID selected;
    private static long snapshots;
    private static long started;
    private static int stage;
    private static int quietTicks;

    private ConnectionClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.connectionSmoke") || stage == 99) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = 1;
                started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeLink Connection Verification", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homelink-connection-" + System.currentTimeMillis(),
                        settings, new WorldOptions(0L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 240_000_000_000L) fail("Timeout at stage " + stage);
            if (serverFailure != null) throw new IllegalStateException("Server verification failed", serverFailure);
            if (stage == 1 && client.player != null && client.level != null && client.screen == null
                    && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) fail("Connected player missing");
                    var networks = DashboardAPI.networks(server);
                    var home = networks.createNetwork("Generic Connection Verification", UUID.randomUUID());
                    networks.setMember(home.id(), playerId, NetworkRole.VIEWER);
                    for (int index = 0; index < 17; index++) {
                        var fixture = new Fixture(index);
                        DashboardAPI.devices(server).register(fixture);
                        networks.addDevice(home.id(), fixture.id());
                    }
                    BlockPos location = player.blockPosition().offset(1, 0, 0);
                    player.serverLevel().setBlock(location, DashboardRegistries.HOME_SERVER.get().defaultBlockState(), 3);
                    var point = (AccessPointBlockEntity) player.serverLevel().getBlockEntity(location);
                    if (point == null) fail("Server block entity missing");
                    point.initializeOwner(playerId);
                    point.setNetworkId(home.id());
                    network = home.id();
                    position = location;
                });
            } else if (stage == 2 && position != null && client.gameMode != null && client.level != null
                    && client.level.getBlockState(position).is(DashboardRegistries.HOME_SERVER.get())) {
                var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
                if (!result.consumesAction()) fail("Server interaction was not consumed");
                stage = 3;
            } else if (stage == 3 && client.screen instanceof DashboardScreen screen) {
                screen.showDevices();
                state = screen.state();
                if (state.loading() || state.devices().size() != 17) return;
                if (state.totalDeviceCount() != 17 || state.nextOffset() != -1 || !state.isNetworkWatch()) fail("Invalid network watch metadata");
                if (!state.networkId().equals(network)) fail("Wrong connected network");
                selected = state.devices().getFirst().id();
                if (!metricEquals(73.0)) fail("Initial metric missing from Dashboard state");
                screen.explorer().setSearch("Unknown device 0");
                screen.explorer().tick();
                if (screen.explorer().filteredDevices().size() != 1) fail("Local device search failed");
                screen.explorer().setStatusFilter("OFFLINE");
                screen.explorer().tick();
                if (!screen.explorer().filteredDevices().isEmpty()) fail("Status filter failed");
                screen.explorer().setSearch("");
                screen.explorer().setStatusFilter("ALL");
                screen.explorer().tick();
                int left = (screen.width - Math.min(520, screen.width - 16)) / 2;
                int top = (screen.height - Math.min(340, screen.height - 16)) / 2;
                screen.mouseClicked(left + 24, top + 172, 0);
                if (!screen.explorer().selectedDeviceId().orElseThrow().equals(screen.explorer().filteredDevices().get(1).id())) {
                    fail("Compact second row click selected the wrong device");
                }
                screen.explorer().back();
                screen.mouseScrolled(left + 24, top + 128, 0, -1);
                screen.mouseClicked(left + 24, top + 128, 0);
                if (!screen.explorer().selectedDeviceId().orElseThrow().equals(screen.explorer().filteredDevices().get(2).id())) {
                    fail("Compact list scroll/click positions are inconsistent");
                }
                screen.explorer().back();
                LogUtils.getLogger().info("HOMELINK_COMPACT_LIST_HITBOXES_OK second_row=true scroll=true");
                screen.explorer().select(selected);
                stage = 30;
            } else if (stage == 30 && client.screen instanceof DashboardScreen screen) {
                if (!screen.explorer().showingDetails()) fail("Device detail selection failed");
                if (++quietTicks < 10) return;
                quietTicks = 0;
                Screenshot.grab(client.gameDirectory, "phase3-device-details.png", client.getMainRenderTarget(),
                        result -> LogUtils.getLogger().info("HOMELINK_SCREENSHOT {}", result.getString()));
                snapshots = state.snapshotCount();
                int w = Math.min(520, screen.width - 16), h = Math.min(340, screen.height - 16);
                screen.mouseClicked((screen.width - w) / 2 + w - 24, (screen.height - h) / 2 + 15, 0);
                if (!screen.manualOpen()) fail("Manual help button did not open the manual");
                serverTask(client, () -> fixture(client).progress.setValue(85.0));
                stage = 301;
            } else if (stage == 301 && client.screen instanceof DashboardScreen screen && metricEquals(85.0)) {
                if (++quietTicks < 10) return;
                quietTicks = 0;
                Screenshot.grab(client.gameDirectory, "homelink-manual.png", client.getMainRenderTarget(),
                        result -> LogUtils.getLogger().info("HOMELINK_SCREENSHOT {}", result.getString()));
                int w = Math.min(520, screen.width - 16), h = Math.min(340, screen.height - 16);
                int x = (screen.width - w) / 2, y = (screen.height - h) / 2;
                screen.mouseScrolled(x + 30, y + 100, 0, -100);
                if (screen.manual().offset() == 0) fail("Manual did not scroll");
                for (int chapter = 1; chapter < 5; chapter++) {
                    screen.mouseClicked(x + w - 24, y + 72, 0);
                    if (screen.manual().chapter() != chapter || screen.manual().offset() != 0) fail("Manual chapter navigation failed");
                }
                screen.mouseClicked(x + w - 24, y + 72, 0);
                if (screen.manual().chapter() != 4) fail("Manual navigated past the last chapter");
                screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                if (screen.manualOpen() || screen.state() != state || !screen.explorer().showingDetails()
                        || state.snapshotCount() != snapshots) fail("Manual did not preserve the active session");
                LogUtils.getLogger().info("HOMELINK_MANUAL_OK chapters=5 scroll=true escape=true live_delta=true session_preserved=true");
                stage = 4;
            } else if (stage == 4 && metricEquals(85.0)) {
                if (state.deltaCount() < 1 || state.snapshotCount() != snapshots) fail("Metric update did not use a delta");
                serverTask(client, () -> fixture(client).location = position.offset(65, 0, 0));
                stage = 40;
            } else if (stage == 40 && state.devices().stream().noneMatch(device -> device.id().equals(selected))) {
                serverTask(client, () -> {
                    var level = client.getSingleplayerServer().overworld();
                    var relayPos = position.offset(64, 0, 0);
                    level.getChunkAt(relayPos);
                    level.setBlock(relayPos, DashboardRegistries.SIGNAL_REPEATER.get().defaultBlockState(), 3);
                    ((AccessPointBlockEntity) level.getBlockEntity(relayPos)).setNetworkId(network);
                });
                stage = 41;
            } else if (stage == 41 && metricEquals(85.0)) {
                serverTask(client, () -> client.getSingleplayerServer().overworld().removeBlock(position.offset(64, 0, 0), false));
                stage = 42;
            } else if (stage == 42 && state.devices().stream().noneMatch(device -> device.id().equals(selected))) {
                serverTask(client, () -> fixture(client).location = null);
                stage = 43;
            } else if (stage == 43 && metricEquals(85.0)) {
                if (state.requestCount() != 1) fail("Radio recovery required a full resubscription");
                LogUtils.getLogger().info("HOMELINK_RADIO_CLIENT_OK out_of_range=true relay_recovery=true relay_break=true automatic_roster=true");
                serverTask(client, () -> fixture(client).status = DeviceStatus.State.WARNING);
                stage = 5;
            } else if (stage == 5 && state.devices().stream().anyMatch(device -> device.id().equals(selected)
                    && device.status().equals("WARNING"))) {
                if (state.snapshotCount() <= snapshots) fail("Status update did not replace device metadata");
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    DashboardAPI.devices(server).unregister(selected);
                    DashboardAPI.networks(server).removeDevice(network, selected);
                });
                stage = 8;
            } else if (stage == 8 && !state.loading() && state.devices().size() == 16 && state.totalDeviceCount() == 16) {
                if (state.devices().stream().anyMatch(device -> device.id().equals(selected))) fail("Removed device retained in network watch");
                if (!state.error().isEmpty() || state.requestCount() != 1) fail("Live removal required a refresh or invalidated the subscription");
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> DashboardAPI.networks(client.getSingleplayerServer()).removeMember(network, playerId));
                stage = 9;
            } else if (stage == 9 && !(client.screen instanceof DashboardScreen)) {
                if (!state.isClosed() || !state.devices().isEmpty()) fail("Revoked session retained state or listener");
                if (++quietTicks < 20) return;
                stage = 99;
                LogUtils.getLogger().info("HOMELINK_CONNECTION_SMOKE_OK devices=17 network_watch=true search=true filter=true details=true metric_delta=true status=true live_removal=true viewer_revocation=true cleanup=true");
                shutdown(client);
            }
        } catch (Throwable failure) {
            int failedStage = stage;
            stage = 99;
            LogUtils.getLogger().error("HOMELINK_CONNECTION_SMOKE_FAILED stage={}", failedStage, failure);
            shutdown(client);
        }
    }

    private static Fixture fixture(Minecraft client) {
        return (Fixture) DashboardAPI.devices(client.getSingleplayerServer()).get(selected).orElseThrow();
    }

    private static boolean metricEquals(double expected) {
        return state.devices().stream().filter(device -> device.id().equals(selected))
                .flatMap(device -> device.metrics().stream())
                .anyMatch(metric -> metric.id().equals(PROGRESS.toString()) && metric.displayValue().equals(Double.toString(expected)));
    }

    private static void serverTask(Minecraft client, Runnable task) {
        client.getSingleplayerServer().execute(() -> {
            try { task.run(); } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void fail(String reason) { throw new IllegalStateException(reason); }

    private static void shutdown(Minecraft client) {
        try {
            if (client.level != null) client.level.disconnect();
            client.disconnect();
        } finally {
            LogUtils.getLogger().info("HOMELINK_CONNECTION_SMOKE_SHUTDOWN_OK");
            client.stop();
        }
    }

    private static final class Fixture implements DashboardDevice {
        private final UUID id;
        private final int index;
        private BlockPos location;
        private DeviceStatus.State status = DeviceStatus.State.ONLINE;
        private final DeviceMetric<Double> progress = DeviceMetric.builder(PROGRESS, Component.literal("Progress"), MetricTypes.DOUBLE, 73.0)
                .updatePolicy(UpdatePolicy.ON_CHANGE).build();

        private Fixture(int index) { this.index = index; id = new UUID(0x74657374L, index + 1); }
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.fromNamespaceAndPath("unknown_integration", "fixture"); }
        @Override public Component displayName() { return Component.literal("Unknown device " + index); }
        @Override public DeviceStatus status() { return DeviceStatus.of(status); }
        @Override public List<DeviceMetric<?>> metrics() { return List.of(progress); }
        @Override public java.util.Optional<BlockPos> position() { return java.util.Optional.ofNullable(location); }
        @Override public java.util.Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> dimension() {
            return location == null ? java.util.Optional.empty() : java.util.Optional.of(net.minecraft.world.level.Level.OVERWORLD);
        }
    }
}
