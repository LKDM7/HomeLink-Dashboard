package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.ActionType;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.action.Unit;
import fr.lkdm.homecore.api.client.ClientDeviceCache;
import fr.lkdm.homecore.api.client.HomeCoreClient;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

/** Exercises real public HomeCore requests, permission enforcement and the Dashboard action UI. */
@EventBusSubscriber(modid = "homelink_dashboard_validation", value = Dist.CLIENT)
public final class ActionClientSmoke {
    private static final UUID DEVICE = UUID.fromString("5bbaf788-3188-4c67-95b1-2612f8ecfe2c");
    private static final String[] KINDS = {"button", "toggle", "integer", "double", "slider", "select", "text", "position"};
    private static final Object[] VALUES = {Unit.INSTANCE, true, 6, 2.5, 85.0, Choice.THIRD, "verified", new BlockPos(124, 64, -382)};
    private static volatile BlockPos position;
    private static volatile UUID network;
    private static volatile Throwable serverFailure;
    private static volatile boolean serverReady;
    private static Fixture fixture;
    private static DashboardClientState state;
    private static UUID request;
    private static final Set<UUID> burst = new HashSet<>();
    private static long started;
    private static long notBefore;
    private static long snapshots;
    private static int stage;
    private static int actionIndex;
    private static int visibleTicks;

    private ActionClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.actionSmoke") || stage == 99) return;
        var client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = 1;
                started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeLink Action Verification", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homelink-actions-" + System.currentTimeMillis(), settings,
                        new WorldOptions(0L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 240_000_000_000L) fail("Timeout at stage " + stage);
            if (serverFailure != null) throw new IllegalStateException("Server assertion failed", serverFailure);
            if (stage == 1 && client.player != null && client.level != null && client.screen == null && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) fail("Player missing");
                    var manager = DashboardAPI.networks(server);
                    var home = manager.createNetwork("Generic Action Verification", UUID.randomUUID());
                    manager.setMember(home.id(), playerId, NetworkRole.ADMIN);
                    fixture = new Fixture();
                    DashboardAPI.devices(server).register(fixture);
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
            } else if (stage == 2 && position != null && client.gameMode != null && client.level != null
                    && client.level.getBlockState(position).is(DashboardRegistries.HOME_SERVER.get())) {
                var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
                if (!result.consumesAction()) fail("Interaction failed");
                stage = 3;
            } else if (stage == 3 && client.screen instanceof DashboardScreen screen && screen.state() != null
                    && !screen.state().loading() && screen.state().devices().size() == 1) {
                state = screen.state();
                if (state.devices().getFirst().actions().size() != 8) fail("Action schema incomplete");
                screen.explorer().select(DEVICE);
                screen.openActions();
                stage = 4;
            } else if (stage == 4 && ++visibleTicks >= 10) {
                Screenshot.grab(client.gameDirectory, "homelink-action-smoke.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Action screenshot: {}", message.getString()));
                snapshots = state.snapshotCount();
                stage = 5;
            } else if (stage == 5 && System.nanoTime() >= notBefore) {
                if (actionIndex == 5) {
                    var screen = (DashboardScreen) client.screen;
                    if (!screen.actions().selectAction(id("select").toString())) fail("SELECT action missing from actual panel");
                    var dropdown = dropdown(screen);
                    if (!screen.mouseClicked(dropdown.getX() + 5, dropdown.getY() + 8, 0) || dropdown.getHeight() != 56)
                        fail("SELECT popup did not display its three choices");
                    visibleTicks = 0; stage = 50; return;
                }
                var action = state.devices().getFirst().actions().stream()
                        .filter(value -> value.id().equals(id(KINDS[actionIndex]).toString())).findFirst().orElseThrow();
                if (!state.executeAction(DEVICE, action, VALUES[actionIndex])) fail("UI rejected valid " + KINDS[actionIndex]);
                stage = 6;
            } else if (stage == 50 && ++visibleTicks >= 10) {
                Screenshot.grab(client.gameDirectory, "homelink-select-popup-smoke.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("SELECT screenshot: {}", message.getString()));
                var screen = (DashboardScreen) client.screen;
                var dropdown = dropdown(screen);
                if (!screen.mouseClicked(dropdown.getX() + 5, dropdown.getY() + 20 + 24 + 5, 0)
                        || !dropdown.getMessage().getString().equals("THIRD") || dropdown.getHeight() != 20)
                    fail("Actual SELECT popup did not choose THIRD");
                stage = 51;
            } else if (stage == 51) {
                var screen = (DashboardScreen) client.screen;
                var execute = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                        .filter(widget -> widget.getMessage().getContents() instanceof TranslatableContents text
                                && text.getKey().equals("screen.homelink_dashboard.execute_action")).findFirst().orElseThrow();
                if (!execute.active || !execute.visible || !screen.mouseClicked(execute.getX() + 4, execute.getY() + 4, 0))
                    fail("Actual SELECT submit button failed");
                stage = 6;
            } else if (stage == 6 && !state.actionPending()) {
                if (!state.lastActionCode().equals("SUCCESS")) fail("Action result " + state.lastActionCode());
                if (!invocationCount(actionIndex + 1)) return;
                if (state.snapshotCount() != snapshots || state.deltaCount() < actionIndex + 1) fail("Action metric failed delta-only synchronization");
                actionIndex++;
                notBefore = System.nanoTime() + 200_000_000L;
                if (actionIndex < KINDS.length) stage = 5;
                else {
                    serverTask(client, () -> {
                        for (int index = 0; index < KINDS.length; index++) {
                            if (!VALUES[index].equals(fixture.received.get(KINDS[index]))) fail("Server handler value mismatch: " + KINDS[index]);
                        }
                    });
                    stage = 7;
                }
            } else if (stage == 7 && System.nanoTime() >= notBefore) {
                request = HomeCoreClient.executeAction(network, DEVICE, id("integer"), 1000);
                stage = 8;
            } else if (stage == 8 && received(ActionResult.Code.INVALID_PARAMETER)) {
                later(); stage = 9;
            } else if (stage == 9 && System.nanoTime() >= notBefore) {
                request = HomeCoreClient.executeAction(network, DEVICE, id("removed_action"), Unit.INSTANCE);
                stage = 10;
            } else if (stage == 10 && received(ActionResult.Code.INVALID_PARAMETER)) {
                serverTask(client, () -> fixture.status = DeviceStatus.State.OFFLINE);
                later(); stage = 11;
            } else if (stage == 11 && System.nanoTime() >= notBefore && state.devices().getFirst().status().equals("OFFLINE")) {
                request = HomeCoreClient.executeAction(network, DEVICE, id("button"), Unit.INSTANCE);
                stage = 12;
            } else if (stage == 12 && received(ActionResult.Code.DEVICE_OFFLINE)) {
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> {
                    fixture.status = DeviceStatus.State.ONLINE;
                    DashboardAPI.networks(client.getSingleplayerServer()).setMember(network, playerId, NetworkRole.VIEWER);
                });
                later(); stage = 13;
            } else if (stage == 13 && System.nanoTime() >= notBefore && state.devices().getFirst().status().equals("ONLINE")) {
                request = HomeCoreClient.executeAction(network, DEVICE, id("button"), Unit.INSTANCE);
                stage = 14;
            } else if (stage == 14 && received(ActionResult.Code.DENIED)) {
                serverTask(client, () -> {
                    if (fixture.received.size() != 8 || fixture.invocations != 8) fail("Rejected actions invoked device handlers");
                    serverReady = true;
                });
                later(); stage = 15;
            } else if (stage == 15 && serverReady && System.nanoTime() >= notBefore) {
                for (int index = 0; index < 20; index++) burst.add(HomeCoreClient.executeAction(network, DEVICE, id("button"), Unit.INSTANCE));
                stage = 16;
            } else if (stage == 16 && ClientDeviceCache.INSTANCE.recentMessages().stream()
                    .filter(HomeCorePayloads.ActionResultResponse.class::isInstance).map(HomeCorePayloads.ActionResultResponse.class::cast)
                    .anyMatch(result -> burst.contains(result.requestId()) && result.result().code() == ActionResult.Code.RATE_LIMITED)) {
                stage = 99;
                LogUtils.getLogger().info("HOMELINK_ACTION_SMOKE_OK kinds=8 handlers=true select_popup_click=true delta=true invalid_range=true unknown_action=true offline=true viewer_denied=true rate_limited=true");
                shutdown(client);
            }
        } catch (Throwable failure) {
            int failed = stage;
            stage = 99;
            LogUtils.getLogger().error("HOMELINK_ACTION_SMOKE_FAILED stage={}", failed, failure);
            shutdown(client);
        }
    }

    private static void later() { notBefore = System.nanoTime() + 200_000_000L; }
    private static AbstractWidget dropdown(DashboardScreen screen) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.getClass().getSimpleName().equals("ActionDropdown") && widget.visible).findFirst().orElseThrow();
    }
    private static boolean invocationCount(int count) {
        return state.devices().getFirst().metrics().stream().anyMatch(metric -> metric.id().equals(id("invocations").toString())
                && metric.value() != null && metric.value().value().equals(count));
    }
    private static boolean received(ActionResult.Code expected) {
        var result = ClientDeviceCache.INSTANCE.recentMessages().stream().filter(HomeCorePayloads.ActionResultResponse.class::isInstance)
                .map(HomeCorePayloads.ActionResultResponse.class::cast).filter(response -> response.requestId().equals(request)).findFirst();
        if (result.isEmpty()) return false;
        if (result.get().result().code() != expected) fail("Expected " + expected + " but received " + result.get().result().code());
        return true;
    }
    private static void serverTask(Minecraft client, Runnable task) {
        client.getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }
    private static void fail(String reason) { throw new IllegalStateException(reason); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("unknown_integration", path); }
    private static void shutdown(Minecraft client) {
        try { if (client.level != null) client.level.disconnect(); client.disconnect(); }
        finally { LogUtils.getLogger().info("HOMELINK_ACTION_SMOKE_SHUTDOWN_OK"); client.stop(); }
    }
    private enum Choice { FIRST, SECOND, THIRD }

    private static final class Fixture implements DashboardDevice {
        private final Map<String, Object> received = new HashMap<>();
        private final List<DeviceAction<?>> actions = new ArrayList<>();
        private final DeviceMetric<Integer> count = DeviceMetric.builder(ActionClientSmoke.id("invocations"), Component.literal("Completed actions"), MetricTypes.INTEGER, 0)
                .updatePolicy(UpdatePolicy.ON_CHANGE).build();
        private DeviceStatus.State status = DeviceStatus.State.ONLINE;
        private int invocations;

        private Fixture() {
            add("button", DeviceAction.button(ActionClientSmoke.id("button"), Component.literal("Run")));
            add("toggle", DeviceAction.toggle(ActionClientSmoke.id("toggle"), Component.literal("Enabled")));
            add("integer", DeviceAction.builder(ActionClientSmoke.id("integer"), Component.literal("Count"), ActionType.INTEGER, Integer.class).range(0, 10).step(2));
            add("double", DeviceAction.builder(ActionClientSmoke.id("double"), Component.literal("Precision"), ActionType.DOUBLE, Double.class).range(0, 10).step(0.5));
            add("slider", DeviceAction.slider(ActionClientSmoke.id("slider"), Component.literal("Power"), 0, 100).step(5));
            add("select", DeviceAction.builder(ActionClientSmoke.id("select"), Component.literal("Mode"), ActionType.SELECT, Choice.class).options(List.of(Choice.FIRST, Choice.SECOND, Choice.THIRD)));
            add("text", DeviceAction.builder(ActionClientSmoke.id("text"), Component.literal("Label"), ActionType.TEXT, String.class).maxLength(20));
            add("position", DeviceAction.builder(ActionClientSmoke.id("position"), Component.literal("Destination"), ActionType.POSITION, BlockPos.class));
        }
        private <T> void add(String name, DeviceAction.Builder<T> builder) {
            actions.add(builder.handler((context, value) -> {
                received.put(name, value); count.setValue(++invocations); return ActionResult.success();
            }).build());
        }
        @Override public UUID id() { return DEVICE; }
        @Override public ResourceLocation deviceType() { return ActionClientSmoke.id("action_fixture"); }
        @Override public Component displayName() { return Component.literal("Unknown integration controls"); }
        @Override public DeviceStatus status() { return DeviceStatus.of(status); }
        @Override public List<DeviceMetric<?>> metrics() { return List.of(count); }
        @Override public List<DeviceAction<?>> actions() { return List.copyOf(actions); }
    }
}
