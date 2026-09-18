package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
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
import net.minecraft.nbt.CompoundTag;
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

/** Integrated GUI, packets and authoritative profile checks using no production mock devices. */
@EventBusSubscriber(modid = "homelink_dashboard_validation", value = Dist.CLIENT)
public final class PreferencesClientSmoke {
    private static final UUID DEVICE = UUID.fromString("64e87c10-8d93-41e8-a182-f8dd0de25878");
    private static volatile BlockPos position;
    private static volatile UUID network;
    private static volatile Throwable serverFailure;
    private static volatile boolean rebound;
    private static DashboardPreferencesClient preferences;
    private static DashboardProfile expected;
    private static DashboardWidget beforeEdit;
    private static long started;
    private static long notBefore;
    private static int stage;
    private static int visibleTicks;

    private PreferencesClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.preferencesSmoke") || stage == 99) return;
        var client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                if (client.options.languageCode.equals("fr_fr")) {
                    var expected = java.util.Map.of("ONLINE", "En ligne", "OWNER", "Propriétaire", "ON", "Activé",
                            "OFF", "Désactivé", "SUCCESS", "Opération réussie", "DENIED", "Accès refusé",
                            "CRITICAL", "Critique", "ALL", "Tous");
                    expected.forEach((key, value) -> {
                        if (!fr.lkdm.homelink.dashboard.client.rendering.DashboardText.value(key).equals(value)) fail("French label missing: " + key);
                    });
                    if (!fr.lkdm.homelink.dashboard.client.rendering.DashboardText.value("third_party:custom").equals("third_party:custom")) fail("Unknown code fallback changed");
                    String hint = net.minecraft.network.chat.Component.translatable("screen.homelink_dashboard.action_hint").getString();
                    if (hint.contains("\\u") || !hint.contains("demande")) fail("French text has escaped characters or was not loaded");
                    LogUtils.getLogger().info("HOMELINK_FRENCH_LABELS_OK statuses=true roles=true toggles=true results=true fallback=true accents=true");
                }
                stage = 1;
                started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeLink Profile Verification", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homelink-profiles-" + System.currentTimeMillis(), settings,
                        new WorldOptions(0L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 240_000_000_000L) fail("Timeout at stage " + stage);
            if (serverFailure != null) throw new IllegalStateException("Server fixture failed", serverFailure);
            if (stage == 1 && client.player != null && client.level != null && client.screen == null && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) fail("Player missing");
                    var manager = DashboardAPI.networks(server);
                    var home = manager.createNetwork("My generic network", playerId);
                    DashboardAPI.devices(server).register(new Fixture());
                    manager.addDevice(home.id(), DEVICE);
                    BlockPos location = player.blockPosition().offset(1, 0, 0);
                    player.serverLevel().setBlock(location, DashboardRegistries.HOME_SERVER.get().defaultBlockState(), 3);
                    var point = (AccessPointBlockEntity) player.serverLevel().getBlockEntity(location);
                    if (point == null) fail("Access point missing");
                    point.initializeOwner(playerId);
                    point.setNetworkId(home.id());
                    network = home.id();
                    position = location;
                });
            } else if (stage == 2 && position != null && client.level != null && client.gameMode != null
                    && client.level.getBlockState(position).is(DashboardRegistries.HOME_SERVER.get())) {
                interact(client); stage = 3;
            } else if (stage == 3 && readyScreen(client)) {
                var screen = screen(client);
                preferences = screen.preferences();
                if (preferences.pending() || !preferences.result().equals("SUCCESS")) return;
                if (!preferences.profile().widgets().isEmpty() || !preferences.profile().favorites().isEmpty()) fail("Fresh profile must be empty");
                screen.home().selectDevice(DEVICE);
                screen.home().toggleFavorite();
                stage = 4;
            } else if (stage == 4 && acknowledged()) {
                if (!preferences.profile().favorites().contains(DEVICE)) fail("Favorite not stored after server acknowledgment");
                later(); stage = 5;
            } else if (stage == 5 && due()) {
                screen(client).home().setEditMode(true);
                screen(client).home().addSummary(); stage = 6;
            } else if (stage == 6 && acknowledged()) {
                if (preferences.profile().widgets().size() != 1) fail("Summary widget was not added");
                later(); stage = 7;
            } else if (stage == 7 && due()) {
                screen(client).home().addMetric(); stage = 8;
            } else if (stage == 8 && acknowledged()) {
                if (preferences.profile().widgets().size() != 2) fail("Metric widget was not added");
                beforeEdit = preferences.profile().widgets().stream().filter(widget -> widget.type() == DashboardWidget.Type.DEVICE_SUMMARY).findFirst().orElseThrow();
                if (preferences.profile().widgets().stream().noneMatch(widget -> widget.type() == DashboardWidget.Type.METRIC && !widget.metricId().isBlank())) fail("Metric selection missing");
                later(); stage = 9;
            } else if (stage == 9 && due()) {
                screen(client).home().selectWidget(beforeEdit.id());
                screen(client).home().moveSelected(0, 3); stage = 10;
            } else if (stage == 10 && acknowledged()) {
                var moved = widget();
                if (moved.y() == beforeEdit.y()) fail("Directional move did not persist");
                beforeEdit = moved;
                later(); stage = 11;
            } else if (stage == 11 && due()) {
                screen(client).home().resizeSelected(); stage = 12;
            } else if (stage == 12 && acknowledged()) {
                var resized = widget();
                if (resized.width() == beforeEdit.width() && resized.height() == beforeEdit.height()) fail("Resize did not persist");
                expected = preferences.profile();
                stage = 13;
            } else if (stage == 13 && ++visibleTicks >= 10) {
                screenshot(client, "homelink-editor-smoke.png");
                screen(client).home().setEditMode(false);
                visibleTicks = 0; stage = 14;
            } else if (stage == 14 && ++visibleTicks >= 10) {
                screenshot(client, "homelink-home-smoke.png");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 1280, 720);
                client.options.guiScale().set(2);
                client.resizeDisplay();
                visibleTicks = 0;
                stage = 140;
            } else if (stage == 140 && ++visibleTicks >= 10) {
                screenshot(client, "homelink-home-large.png");
                client.screen.onClose();
                later(); stage = 15;
            } else if (stage == 15 && client.screen == null && due()) {
                interact(client); stage = 16;
            } else if (stage == 16 && readyScreen(client)) {
                preferences = screen(client).preferences();
                if (preferences.pending() || !preferences.result().equals("SUCCESS")) return;
                if (!preferences.profile().equals(expected)) fail("Layout, widget IDs or favorites lost after reopen");
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    var stored = DashboardPreferencesSavedData.get(server);
                    if (!stored.profile(playerId, network).equals(expected)) fail("Server saved profile differs from acknowledged layout");
                    var encoded = stored.save(new CompoundTag(), server.registryAccess());
                    var restored = DashboardPreferencesSavedData.load(encoded, server.registryAccess());
                    if (!restored.profile(playerId, network).equals(expected)) fail("Profile did not survive real SavedData NBT round trip");
                    var manager = DashboardAPI.networks(server);
                    var restricted = manager.createNetwork("Viewer layout authorization", UUID.randomUUID());
                    manager.setMember(restricted.id(), playerId, NetworkRole.VIEWER);
                    manager.addDevice(restricted.id(), DEVICE);
                    var player = server.getPlayerList().getPlayer(playerId);
                    var point = (AccessPointBlockEntity) player.serverLevel().getBlockEntity(position);
                    point.setNetworkId(restricted.id());
                    network = restricted.id();
                    rebound = true;
                });
                later(); stage = 17;
            } else if (stage == 17 && rebound && client.screen == null && due()) {
                interact(client); stage = 18;
            } else if (stage == 18 && readyScreen(client) && screen(client).state().networkId().equals(network)) {
                preferences = screen(client).preferences();
                if (preferences.pending() || !preferences.result().equals("SUCCESS")) return;
                if (!preferences.profile().widgets().isEmpty() || !preferences.profile().favorites().isEmpty()) fail("Profiles leaked between networks");
                later(); stage = 19;
            } else if (stage == 19 && due()) {
                // Deliberately bypass the disabled editor to verify the server's CONFIGURE boundary.
                if (!preferences.saveLayout(expected.widgets())) fail("Unable to submit forged Viewer layout request");
                stage = 20;
            } else if (stage == 20 && !preferences.pending()) {
                if (!preferences.result().equals("DENIED")) fail("Viewer layout request was not denied by server: " + preferences.result());
                if (!preferences.profile().widgets().isEmpty()) fail("Denied mutation changed Viewer layout");
                stage = 99;
                LogUtils.getLogger().info("HOMELINK_PREFERENCES_SMOKE_OK favorite=true widgets=2 move=true resize=true reopen=true nbt=true network_isolation=true viewer_denied=true");
                shutdown(client);
            }
        } catch (Throwable failure) {
            int failed = stage; stage = 99;
            LogUtils.getLogger().error("HOMELINK_PREFERENCES_SMOKE_FAILED stage={}", failed, failure);
            shutdown(client);
        }
    }

    private static DashboardWidget widget() {
        return preferences.profile().widgets().stream().filter(widget -> widget.id().equals(beforeEdit.id())).findFirst().orElseThrow();
    }
    private static boolean acknowledged() {
        if (preferences.pending()) return false;
        if (!preferences.result().equals("SUCCESS")) fail("Preference mutation failed: " + preferences.result());
        return true;
    }
    private static boolean readyScreen(Minecraft client) {
        return client.screen instanceof DashboardScreen screen && screen.state() != null && !screen.state().loading()
                && screen.state().devices().size() == 1 && screen.preferences() != null && screen.home() != null;
    }
    private static DashboardScreen screen(Minecraft client) { return (DashboardScreen) client.screen; }
    private static void later() { notBefore = System.nanoTime() + 650_000_000L; }
    private static boolean due() { return System.nanoTime() >= notBefore; }
    private static void interact(Minecraft client) {
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        if (!result.consumesAction()) fail("Access point interaction failed");
    }
    private static void screenshot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LogUtils.getLogger().info("Profile screenshot: {}", message.getString()));
    }
    private static void serverTask(Minecraft client, Runnable task) {
        client.getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }
    private static void fail(String reason) { throw new IllegalStateException(reason); }
    private static void shutdown(Minecraft client) {
        try { if (client.level != null) client.level.disconnect(); client.disconnect(); }
        finally { LogUtils.getLogger().info("HOMELINK_PREFERENCES_SMOKE_SHUTDOWN_OK"); client.stop(); }
    }
    private static final class Fixture implements DashboardDevice {
        private final List<DeviceMetric<?>> metrics = List.of(
                DeviceMetric.builder(ResourceLocation.fromNamespaceAndPath("unknown_integration", "level"), Component.literal("Capacity"), MetricTypes.PERCENTAGE, new Percentage(73)).build(),
                DeviceMetric.builder(ResourceLocation.fromNamespaceAndPath("unknown_integration", "count"), Component.literal("Objects"), MetricTypes.INTEGER, 514).build());
        @Override public UUID id() { return DEVICE; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.fromNamespaceAndPath("unknown_integration", "generic_widget_source"); }
        @Override public Component displayName() { return Component.literal("Generic device"); }
        @Override public DeviceStatus status() { return DeviceStatus.of(DeviceStatus.State.ONLINE); }
        @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    }
}
