package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.config.DashboardConfig;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import org.lwjgl.glfw.GLFW;

/** Functional load and lifecycle checks; deliberately makes no synthetic FPS claims. */
@EventBusSubscriber(modid = "homelink_dashboard_validation", value = Dist.CLIENT)
public final class PerformanceClientSmoke {
    private static final ResourceLocation EVENT = ResourceLocation.fromNamespaceAndPath("unknown_integration", "signal");
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static volatile BlockPos position;
    private static volatile UUID network;
    private static volatile Throwable serverFailure;
    private static DashboardClientState state;
    private static DashboardClientState closedState;
    private static long closedRevision;
    private static long snapshots;
    private static long filterBuilds;
    private static long started;
    private static long notBefore;
    private static int stage;
    private static int visibleTicks;
    private static int reopens;
    private static int originalWindowWidth;
    private static int originalWindowHeight;
    private static int originalGuiScale;

    private PerformanceClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.performanceSmoke") || stage == 99) return;
        var client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = 1; started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeLink 100 Device Verification", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homelink-load-" + System.currentTimeMillis(), settings,
                        new WorldOptions(0L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 300_000_000_000L) fail("Timeout at stage " + stage);
            if (serverFailure != null) throw new IllegalStateException("Load fixture failed", serverFailure);
            if (stage == 1 && client.player != null && client.level != null && client.screen == null && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID owner = client.player.getUUID();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(owner);
                    if (player == null) fail("Player missing");
                    var manager = DashboardAPI.networks(server);
                    var home = manager.createNetwork("100 generic devices / 800 metrics", owner);
                    for (int index = 0; index < 100; index++) {
                        var fixture = new Fixture(index); FIXTURES.add(fixture);
                        DashboardAPI.devices(server).register(fixture); manager.addDevice(home.id(), fixture.id());
                    }
                    BlockPos location = player.blockPosition().offset(1, 0, 0);
                    player.serverLevel().setBlock(location, DashboardRegistries.HOME_SERVER.get().defaultBlockState(), 3);
                    var point = (AccessPointBlockEntity) player.serverLevel().getBlockEntity(location);
                    point.initializeOwner(owner); point.setNetworkId(home.id());
                    network = home.id(); position = location;
                });
            } else if (stage == 2 && position != null && client.level != null && client.gameMode != null
                    && client.level.getBlockState(position).is(DashboardRegistries.HOME_SERVER.get())) {
                interact(client); stage = 3;
            } else if (stage == 3 && ready(client)) {
                state = screen(client).state();
                if (state.devices().size() != 100 || state.snapshotCount() != 100 || state.truncated() || !state.isNetworkWatch())
                    fail("Initial watch did not provide exactly 100 complete device snapshots");
                if (state.devices().stream().mapToInt(device -> device.metrics().size()).sum() != 800) fail("Initial 800 metrics incomplete");
                screen(client).showDevices();
                var explorer = screen(client).explorer();
                for (String query : List.of("d", "de", "dev", "device", "device099")) { explorer.setSearch(query); explorer.tick(); }
                if (explorer.filteredDevices().size() != 1) fail("Search did not find device beyond page16");
                explorer.setSearch("even"); explorer.tick();
                if (explorer.filteredDevices().size() != 50) fail("Capability search failed");
                explorer.setSearch(""); explorer.tick();
                if (explorer.filteredDevices().size() != 100 || state.requestCount() != 1) fail("Local search sent server queries");
                snapshots = state.snapshotCount();
                filterBuilds = explorer.filterBuildCount();
                serverTask(client, () -> FIXTURES.get(99).metrics.getFirst().setValue(9001));
                stage = 4;
            } else if (stage == 4 && metricEquals(99, 9001)) {
                if (state.snapshotCount() != snapshots || state.deltaCount() != 1) fail("One changed metric resent snapshots");
                screen(client).explorer().tick();
                if (screen(client).explorer().filterBuildCount() != filterBuilds) fail("Metric delta rebuilt the filtered device list");
                serverTask(client, () -> {
                    FIXTURES.get(99).status = DeviceStatus.State.OFFLINE;
                    DashboardAPI.events(client.getSingleplayerServer()).publish(new DeviceEvent(EVENT, deviceId(99), Instant.now(),
                            DeviceEvent.Severity.WARNING, Map.of("message", "Beyond first sixteen devices")));
                });
                stage = 5;
            } else if (stage == 5 && state.device(deviceId(99)).map(device -> device.status().equals("OFFLINE")).orElse(false)
                    && state.alerts().stream().anyMatch(alert -> alert.sourceId().equals(deviceId(99)))) {
                var explorer = screen(client).explorer();
                explorer.setStatusFilter("OFFLINE"); explorer.tick();
                if (explorer.filteredDevices().size() != 1) fail("Offline filter did not update live");
                explorer.setStatusFilter("ALL"); explorer.tick();
                explorer.select(deviceId(99));
                int[] windowWidth = new int[1]; int[] windowHeight = new int[1];
                GLFW.glfwGetWindowSize(client.getWindow().getWindow(), windowWidth, windowHeight);
                originalWindowWidth = windowWidth[0]; originalWindowHeight = windowHeight[0];
                originalGuiScale = client.options.guiScale().get();
                resizeWindow(client, 640, 480, 2);
                stage = 6;
            } else if (stage == 6 && ++visibleTicks >= 10) {
                assertViewport(client, 320, 240);
                screenshot(client, "homelink-100-small.png");
                resizeWindow(client, 1280, 720, 2);
                visibleTicks = 0; stage = 7;
            } else if (stage == 7 && ++visibleTicks >= 10) {
                assertViewport(client, 640, 360);
                screenshot(client, "homelink-100-large.png");
                resizeWindow(client, originalWindowWidth, originalWindowHeight, originalGuiScale);
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    DashboardAPI.devices(server).unregister(deviceId(99));
                    DashboardAPI.networks(server).removeDevice(network, deviceId(99));
                    var replacement = new Fixture(100); FIXTURES.add(replacement);
                    DashboardAPI.devices(server).register(replacement);
                    DashboardAPI.networks(server).addDevice(network, replacement.id());
                });
                stage = 8;
            } else if (stage == 8 && state.devices().size() == 100 && state.device(deviceId(99)).isEmpty() && state.device(deviceId(100)).isPresent()) {
                if (state.requestCount() != 1 || !state.error().isEmpty()) fail("Roster change required full refresh");
                if (state.snapshotCount() != snapshots + 2) fail("Status and one new device should add only two snapshots, got " + state.snapshotCount());
                if (state.alerts().stream().noneMatch(alert -> alert.sourceId().equals(deviceId(99)))) fail("Removed source lost alert history");
                screen(client).showSettings();
                int interval = DashboardConfig.uiInterval();
                var setting = screen(client).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getMessage().getContents() instanceof TranslatableContents text
                                && text.getKey().equals("screen.homelink_dashboard.settings_ui_interval")).findFirst().orElseThrow();
                if (!screen(client).mouseClicked(setting.getX() + 3, setting.getY() + 3, 0) || DashboardConfig.uiInterval() == interval)
                    fail("Actual settings button did not update client configuration");
                visibleTicks = 0; stage = 13;
            } else if (stage == 13 && ++visibleTicks >= 10) {
                String savedConfig = java.nio.file.Files.readString(client.gameDirectory.toPath().resolve("config/homelink_dashboard-client.toml"));
                if (!java.util.regex.Pattern.compile("uiUpdateIntervalTicks\\s*=\\s*" + DashboardConfig.uiInterval() + "\\b").matcher(savedConfig).find())
                    fail("Changed client setting was not saved to configuration file");
                screenshot(client, "homelink-settings-smoke.png");
                stage = 9;
            } else if (stage == 9) {
                closedState = state;
                client.screen.onClose();
                if (!closedState.isClosed() || !closedState.devices().isEmpty() || !closedState.alerts().isEmpty()) fail("Screen close retained state");
                closedRevision = closedState.revision();
                notBefore = System.nanoTime() + 750_000_000L; stage = 10;
            } else if (stage == 10 && client.screen == null && System.nanoTime() >= notBefore) {
                interact(client); stage = 11;
            } else if (stage == 11 && ready(client)) {
                state = screen(client).state();
                if (state.devices().size() != 100 || state.requestCount() != 1) fail("Reopened network watch incomplete");
                serverTask(client, () -> FIXTURES.get(0).metrics.getFirst().setValue(10000 + reopens));
                stage = 12;
            } else if (stage == 12 && metricEquals(0, 10000 + reopens)) {
                if (closedState.revision() != closedRevision || !closedState.devices().isEmpty()) fail("Closed state listener received new updates");
                if (++reopens < 3) stage = 9;
                else {
                    stage = 99;
                    LogUtils.getLogger().info("HOMELINK_PERFORMANCE_SMOKE_OK devices=100 metrics=800 snapshot_initial=100 delta_only=true filter_cache=true search_local=true capability_search=true offline=true dynamic_roster=true events_beyond16=true viewport_320x240_640x360=true settings_saved=true reopen=3 closed_listener_inert=true");
                    shutdown(client);
                }
            }
        } catch (Throwable failure) {
            int failed = stage; stage = 99;
            LogUtils.getLogger().error("HOMELINK_PERFORMANCE_SMOKE_FAILED stage={}", failed, failure);
            shutdown(client);
        }
    }

    private static boolean metricEquals(int index, int value) {
        return state.device(deviceId(index)).map(device -> device.metrics().getFirst().value() != null
                && device.metrics().getFirst().value().value().equals(value)).orElse(false);
    }
    private static boolean ready(Minecraft client) { return client.screen instanceof DashboardScreen screen && screen.state() != null && !screen.state().loading(); }
    private static DashboardScreen screen(Minecraft client) { return (DashboardScreen) client.screen; }
    private static void resizeWindow(Minecraft client, int width, int height, int guiScale) {
        client.options.guiScale().set(guiScale);
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), width, height);
        client.resizeDisplay();
    }
    private static void assertViewport(Minecraft client, int width, int height) {
        if (client.getWindow().getGuiScaledWidth() != width || client.getWindow().getGuiScaledHeight() != height
                || client.screen.width != width || client.screen.height != height)
            fail("Actual framebuffer/GUI viewport mismatch: " + client.getWindow().getGuiScaledWidth() + "x" + client.getWindow().getGuiScaledHeight());
    }
    private static UUID deviceId(int index) { return new UUID(0x100800L, index + 1L); }
    private static void interact(Minecraft client) {
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        if (!result.consumesAction()) fail("Interaction failed");
    }
    private static void screenshot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LogUtils.getLogger().info("Load screenshot: {}", message.getString()));
    }
    private static void serverTask(Minecraft client, Runnable task) {
        client.getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }
    private static void fail(String reason) { throw new IllegalStateException(reason); }
    private static void shutdown(Minecraft client) {
        try { if (client.level != null) client.level.disconnect(); client.disconnect(); }
        finally { LogUtils.getLogger().info("HOMELINK_PERFORMANCE_SMOKE_SHUTDOWN_OK"); client.stop(); }
    }
    private static final class Fixture implements DashboardDevice {
        private final int index;
        private final List<DeviceMetric<Integer>> metrics = new ArrayList<>();
        private DeviceStatus.State status = DeviceStatus.State.ONLINE;
        private Fixture(int index) {
            this.index = index;
            for (int metric = 0; metric < 8; metric++) metrics.add(DeviceMetric.builder(ResourceLocation.fromNamespaceAndPath("unknown_integration", "metric" + metric),
                    Component.literal("Telemetry " + metric), MetricTypes.INTEGER, metric).updatePolicy(UpdatePolicy.ON_CHANGE).build());
        }
        @Override public UUID id() { return deviceId(index); }
        @Override public ResourceLocation deviceType() { return ResourceLocation.fromNamespaceAndPath("unknown_integration", "load_fixture"); }
        @Override public Component displayName() { return Component.literal(String.format(java.util.Locale.ROOT, "Device%03d", index) + (index == 99 ? " long text".repeat(50) : "")); }
        @Override public DeviceStatus status() { return DeviceStatus.of(status); }
        @Override public List<DeviceMetric<?>> metrics() { return List.copyOf(metrics); }
        @Override public Set<ResourceLocation> capabilities() { return Set.of(ResourceLocation.fromNamespaceAndPath("unknown_integration", index % 2 == 0 ? "even" : "odd")); }
        @Override public Set<ResourceLocation> eventTypes() { return Set.of(EVENT); }
    }
}
