package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.MachineSystems;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
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
import org.lwjgl.glfw.GLFW;

/** Runs public HomeCore event delivery through a real connected Dashboard GUI. */
@EventBusSubscriber(modid = "homelink_dashboard_validation", value = Dist.CLIENT)
public final class AlertsClientSmoke {
    private static final UUID OWNER = UUID.fromString("ce55a3dc-5f5e-42c3-9776-781893097a16");
    private static final UUID DEVICE = UUID.fromString("9075c9c9-81ef-4307-b506-bf46a2398a8e");
    private static final UUID SURVIVOR = UUID.fromString("60c90d7e-ff5c-4dab-8644-b543968ecba5");
    private static final ResourceLocation EVENT = ResourceLocation.fromNamespaceAndPath("unknown_integration", "signal");
    private static volatile BlockPos position;
    private static volatile UUID network;
    private static volatile Throwable serverFailure;
    private static DashboardClientState state;
    private static long started;
    private static int visibleTicks;
    private static int stage;
    private static long acknowledgedAlertId;
    private static int originalWindowWidth, originalWindowHeight, originalGuiScale;

    private AlertsClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.alertsSmoke") || stage == 99) return;
        var client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = 1; started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeLink Alerts Verification", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homelink-alerts-" + System.currentTimeMillis(), settings,
                        new WorldOptions(0L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 240_000_000_000L) fail("Timeout at stage " + stage);
            if (serverFailure != null) throw new IllegalStateException("Server event fixture failed", serverFailure);
            if (stage == 1 && client.player != null && client.level != null && client.screen == null && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(playerId);
                    if (player == null) fail("Player missing");
                    var manager = DashboardAPI.networks(server);
                    var home = manager.createNetwork("Generic event network", OWNER);
                    manager.setMember(home.id(), playerId, NetworkRole.VIEWER);
                    DashboardAPI.devices(server).register(new Fixture(DEVICE, "Entrance telemetry"));
                    DashboardAPI.devices(server).register(new Fixture(SURVIVOR, "Secondary telemetry"));
                    manager.addDevice(home.id(), DEVICE);
                    manager.addDevice(home.id(), SURVIVOR);
                    BlockPos location = player.blockPosition().offset(1, 0, 0);
                    player.serverLevel().setBlock(location, DashboardRegistries.HOME_SERVER.get().defaultBlockState(), 3);
                    var point = (AccessPointBlockEntity) player.serverLevel().getBlockEntity(location);
                    if (point == null) fail("Access point missing");
                    point.initializeOwner(playerId);
                    point.setNetworkId(home.id());
                    network = home.id(); position = location;
                });
            } else if (stage == 2 && position != null && client.level != null && client.gameMode != null
                    && client.level.getBlockState(position).is(DashboardRegistries.HOME_SERVER.get())) {
                var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
                if (!result.consumesAction()) fail("Access point interaction failed");
                stage = 3;
            } else if (stage == 3 && client.screen instanceof DashboardScreen screen && screen.state() != null
                    && !screen.state().loading() && screen.state().devices().size() == 2) {
                state = screen.state();
                if (!state.ownerId().orElseThrow().equals(OWNER) || state.memberCount() != 2 || state.totalDeviceCount() != 2)
                    fail("Network owner/member/device metadata mismatch");
                if (!state.connectionStatus().equals("CONNECTED")) fail("Network should be connected");
                state.setAlertLimit(3);
                screen.showAlerts();
                serverTask(client, () -> {
                    publish(client, DEVICE, DeviceEvent.Severity.INFO, "First signal");
                    publish(client, SURVIVOR, DeviceEvent.Severity.WARNING, "Attention required");
                    publish(client, DEVICE, DeviceEvent.Severity.CRITICAL, "Urgent signal");
                });
                stage = 4;
            } else if (stage == 4 && state.alerts().size() == 3) {
                GLFW.glfwSetCursorPos(client.getWindow().getWindow(), 2, 2);
                var alerts = screen(client).alerts();
                for (String severity : new String[]{"INFO", "WARNING", "CRITICAL"}) {
                    alerts.setFilter(severity);
                    if (alerts.filteredAlerts().size() != 1 || !alerts.filteredAlerts().getFirst().severity().equals(severity))
                        fail("Severity filter failed: " + severity);
                }
                alerts.setFilter("ALL");
                if (alerts.filteredAlerts().size() != 3) fail("ALL filter lost alerts");
                if (state.alerts().stream().anyMatch(alert -> (!alert.sourceId().equals(DEVICE) && !alert.sourceId().equals(SURVIVOR)) || alert.message().isBlank()
                        || alert.timestamp() == null || !alert.type().equals(EVENT.toString()))) fail("Event metadata was not retained");
                if (state.activeAlertCount() != 3 || state.unreadAlertCount() != 3
                        || state.alerts().stream().anyMatch(alert -> alert.read() || alert.acknowledged()))
                    fail("New events should be active and unread");
                alerts.setSystemFilter(MachineSystems.Group.ENERGY);
                if (alerts.filteredAlerts().size() != 1 || !alerts.filteredAlerts().getFirst().sourceId().equals(SURVIVOR))
                    fail("Energy system filter did not retain hydro source");
                alerts.setFilter("WARNING");
                if (alerts.filteredAlerts().size() != 1) fail("Severity/system filters did not compose");
                alerts.setSystemFilter(MachineSystems.Group.OTHER);
                if (!alerts.filteredAlerts().isEmpty()) fail("System filter leaked another system's events");
                alerts.setFilter("ALL");
                if (alerts.filteredAlerts().size() != 2
                        || alerts.filteredAlerts().stream().anyMatch(alert -> alert.system() != MachineSystems.Group.OTHER))
                    fail("Third-party system classification failed");
                acknowledgedAlertId = alerts.filteredAlerts().getFirst().id();
                assertButtonGeometry(screen(client));
                clickButton(screen(client), "alert_mark_read");
                if (state.unreadAlertCount() != 2 || state.activeAlertCount() != 3) fail("Reading changed active count");
                alerts.setStatusFilter("READ");
                if (alerts.filteredAlerts().size() != 1 || alerts.filteredAlerts().getFirst().id() != acknowledgedAlertId)
                    fail("Read/system filters did not compose");
                clickButton(screen(client), "alert_mark_unread");
                if (!alerts.filteredAlerts().isEmpty() || state.unreadAlertCount() != 3)
                    fail("Mark unread did not refresh status filter");
                alerts.setStatusFilter("UNREAD");
                if (alerts.filteredAlerts().size() != 2) fail("Unread/system filters did not compose");
                clickButton(screen(client), "alert_acknowledge");
                if (state.activeAlertCount() != 2 || state.unreadAlertCount() != 2)
                    fail("Individual acknowledgement did not update counters");
                alerts.setStatusFilter("ACKNOWLEDGED");
                if (alerts.filteredAlerts().size() != 1 || alerts.filteredAlerts().getFirst().id() != acknowledgedAlertId
                        || !alerts.filteredAlerts().getFirst().read()) fail("Acknowledged filter failed");
                alerts.setSystemFilter(MachineSystems.Group.ENERGY);
                if (!alerts.filteredAlerts().isEmpty()) fail("Acknowledged/system filters did not compose");
                alerts.setStatusFilter("ACTIVE");
                if (alerts.filteredAlerts().size() != 1) fail("Active/system filters did not compose");
                alerts.setSystemFilter(null);
                alerts.setStatusFilter("ALL");
                visibleTicks = 0; stage = 40;
            } else if (stage == 40 && ++visibleTicks >= 10) {
                assertButtonGeometry(screen(client));
                screenshot(client, "homelink-alerts-active-smoke.png");
                int[] windowWidth = new int[1], windowHeight = new int[1];
                GLFW.glfwGetWindowSize(client.getWindow().getWindow(), windowWidth, windowHeight);
                originalWindowWidth = windowWidth[0]; originalWindowHeight = windowHeight[0];
                originalGuiScale = client.options.guiScale().get();
                resizeWindow(client, 854, 480, 2);
                visibleTicks = 0; stage = 41;
            } else if (stage == 41 && ++visibleTicks >= 10) {
                if (client.getWindow().getGuiScaledWidth() != 427 || client.getWindow().getGuiScaledHeight() != 240
                        || client.screen.width != 427 || client.screen.height != 240) fail("Small alert viewport mismatch");
                assertButtonGeometry(screen(client));
                clickButton(screen(client), "alert_mark_unread");
                if (state.unreadAlertCount() != 3) fail("Small viewport mark unread button failed");
                clickButton(screen(client), "alert_mark_read");
                if (state.unreadAlertCount() != 2) fail("Small viewport mark read button failed");
                if (!screen(client).keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0)) fail("Alert Page Down rejected");
                clickButton(screen(client), "alert_mark_read");
                if (state.unreadAlertCount() != 1 || !state.alerts().get(1).read()) fail("Page Down did not bind buttons to the next alert");
                clickButton(screen(client), "alert_mark_unread");
                if (!screen(client).keyPressed(GLFW.GLFW_KEY_END, 0, 0)) fail("Alert End rejected");
                clickButton(screen(client), "alert_mark_read");
                if (state.unreadAlertCount() != 1 || !state.alerts().getLast().read()) fail("End did not expose the last alert");
                clickButton(screen(client), "alert_mark_unread");
                if (!screen(client).keyPressed(GLFW.GLFW_KEY_HOME, 0, 0)
                        || !screen(client).keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0)) fail("Alert Home/Page Down rejected");
                visibleTicks = 0; stage = 43;
            } else if (stage == 43 && ++visibleTicks >= 10) {
                screenshot(client, "homelink-alerts-small-smoke.png");
                resizeWindow(client, originalWindowWidth, originalWindowHeight, originalGuiScale);
                visibleTicks = 0; stage = 42;
            } else if (stage == 42 && ++visibleTicks >= 10) {
                var alerts = screen(client).alerts();
                alerts.setSystemFilter(MachineSystems.Group.ENERGY);
                alerts.setStatusFilter("ACTIVE");
                clickButton(screen(client), "alert_acknowledge_all");
                if (state.activeAlertCount() != 0 || state.unreadAlertCount() != 0 || !alerts.filteredAlerts().isEmpty()
                        || state.alerts().stream().anyMatch(alert -> !alert.acknowledged() || !alert.read()))
                    fail("Acknowledge all did not cover alerts outside the current filters");
                alerts.setSystemFilter(null);
                alerts.setStatusFilter("ALL");
                if (alerts.filteredAlerts().size() != 3) fail("Resetting filters lost acknowledged history");
                visibleTicks = 0; stage = 5;
            } else if (stage == 5 && ++visibleTicks >= 10) {
                screenshot(client, "homelink-alerts-smoke.png");
                screen(client).alerts().markAlertRead(0, false);
                if (state.unreadAlertCount() != 1) fail("Acknowledged alert could not be marked unread");
                if (!screen(client).alerts().selectAlert(0)) fail("Live source navigation rejected");
                if (state.unreadAlertCount() != 0 || !state.alerts().getFirst().read()) fail("Source navigation did not mark alert read");
                if (screen(client).explorer().selectedDeviceId().filter(DEVICE::equals).isEmpty()) fail("Alert did not select source device");
                screen(client).showAlerts();
                serverTask(client, () -> publish(client, DEVICE, DeviceEvent.Severity.INFO, "Newest signal"));
                visibleTicks = 0; stage = 6;
            } else if (stage == 6 && state.alerts().stream().anyMatch(alert -> alert.message().contains("Newest signal"))) {
                if (state.alerts().size() != 3 || state.alerts().stream().anyMatch(alert -> alert.message().contains("First signal")))
                    fail("Bounded history did not evict oldest alert");
                if (state.activeAlertCount() != 1 || state.unreadAlertCount() != 1
                        || state.alerts().getFirst().acknowledged() || state.alerts().getFirst().read())
                    fail("New event inherited bulk acknowledgement or read status");
                screen(client).showNetwork();
                screen(client).network().showAdvanced(true);
                stage = 7;
            } else if (stage == 7 && ++visibleTicks >= 10) {
                screenshot(client, "homelink-network-smoke.png");
                screen(client).showAlerts();
                serverTask(client, () -> {
                    var server = client.getSingleplayerServer();
                    DashboardAPI.devices(server).unregister(DEVICE);
                    DashboardAPI.networks(server).removeDevice(network, DEVICE);
                });
                stage = 8;
            } else if (stage == 8 && !state.loading() && state.devices().size() == 1) {
                if (state.alerts().size() != 3 || screen(client).alerts().selectAlert(0)) fail("Removed source history was lost or remained actionable");
                var retained = state.alerts().stream().filter(alert -> alert.id() == acknowledgedAlertId).findFirst().orElseThrow();
                if (!retained.acknowledged() || !retained.read() || retained.system() != MachineSystems.Group.OTHER
                        || state.activeAlertCount() != 1 || state.unreadAlertCount() != 0)
                    fail("Source removal lost retained alert status or system");
                if (!state.error().isEmpty() || state.requestCount() != 1) fail("Source removal invalidated live event subscription");
                serverTask(client, () -> publish(client, SURVIVOR, DeviceEvent.Severity.WARNING, "Before revocation"));
                stage = 10;
            } else if (stage == 10 && state.alerts().stream().anyMatch(alert -> alert.message().contains("Before revocation"))) {
                if (state.alerts().getFirst().system() != MachineSystems.Group.ENERGY) fail("Hydro alert system classification failed");
                UUID playerId = client.player.getUUID();
                serverTask(client, () -> DashboardAPI.networks(client.getSingleplayerServer()).removeMember(network, playerId));
                stage = 11;
            } else if (stage == 11 && !(client.screen instanceof DashboardScreen)) {
                if (!state.isClosed() || !state.alerts().isEmpty() || !state.devices().isEmpty()
                        || state.activeAlertCount() != 0 || state.unreadAlertCount() != 0) fail("Revoked GUI retained private events or subscription");
                stage = 99;
                LogUtils.getLogger().info("HOMELINK_ALERTS_SMOKE_OK severities=3 filters=true read_unread=true acknowledgements=true system_filters=true active_counts=true actual_buttons=true keyboard_scroll=true viewport_427x240=true source_navigation=true history_bound=3 removed_source=true owner_members=true revoke_cleanup=true");
                shutdown(client);
            }
        } catch (Throwable failure) {
            int failed = stage; stage = 99;
            LogUtils.getLogger().error("HOMELINK_ALERTS_SMOKE_FAILED stage={}", failed, failure);
            shutdown(client);
        }
    }

    private static void publish(Minecraft client, UUID source, DeviceEvent.Severity severity, String message) {
        DashboardAPI.events(client.getSingleplayerServer()).publish(new DeviceEvent(EVENT, source, Instant.now(), severity,
                Map.of("message", message, "channel", "generic verification")));
    }
    private static DashboardScreen screen(Minecraft client) { return (DashboardScreen) client.screen; }
    private static void clickButton(DashboardScreen screen, String key) {
        String label = Component.translatable("screen.homelink_dashboard." + key).getString();
        var button = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(control -> control.visible && control.active && control.getMessage().getString().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing actionable button: " + key));
        double mouseX = button.getX() + button.getWidth() / 2.0, mouseY = button.getY() + button.getHeight() / 2.0;
        if (!screen.mouseClicked(mouseX, mouseY, 0)) fail("Button click rejected: " + key);
        screen.mouseReleased(mouseX, mouseY, 0);
    }
    private static void assertButtonGeometry(DashboardScreen screen) {
        int frameWidth = Math.min(520, screen.width - 16), frameHeight = Math.min(340, screen.height - 16);
        int left = (screen.width - frameWidth) / 2, top = (screen.height - frameHeight) / 2;
        var buttons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(control -> control.visible).toList();
        for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            if (button.getWidth() <= 0 || button.getHeight() <= 0 || button.getX() < left || button.getY() < top
                    || button.getX() + button.getWidth() > left + frameWidth
                    || button.getY() + button.getHeight() > top + frameHeight)
                fail("Visible button escaped dashboard frame: " + button.getMessage().getString());
            for (int other = index + 1; other < buttons.size(); other++) {
                var candidate = buttons.get(other);
                if (button.getX() < candidate.getX() + candidate.getWidth() && candidate.getX() < button.getX() + button.getWidth()
                        && button.getY() < candidate.getY() + candidate.getHeight() && candidate.getY() < button.getY() + button.getHeight())
                    fail("Visible buttons overlap: " + button.getMessage().getString() + " / " + candidate.getMessage().getString());
            }
        }
    }
    private static void resizeWindow(Minecraft client, int width, int height, int guiScale) {
        client.options.guiScale().set(guiScale);
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(), width, height);
        client.resizeDisplay();
    }
    private static void screenshot(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LogUtils.getLogger().info("Alerts screenshot: {}", message.getString()));
    }
    private static void serverTask(Minecraft client, Runnable task) {
        client.getSingleplayerServer().execute(() -> { try { task.run(); } catch (Throwable failure) { serverFailure = failure; } });
    }
    private static void fail(String reason) { throw new IllegalStateException(reason); }
    private static void shutdown(Minecraft client) {
        try { if (client.level != null) client.level.disconnect(); client.disconnect(); }
        finally { LogUtils.getLogger().info("HOMELINK_ALERTS_SMOKE_SHUTDOWN_OK"); client.stop(); }
    }
    private record Fixture(UUID id, String name) implements DashboardDevice {
        @Override public ResourceLocation deviceType() {
            return id.equals(SURVIVOR) ? ResourceLocation.fromNamespaceAndPath("homelink_energy", "hydro_turbine")
                    : ResourceLocation.fromNamespaceAndPath("unknown_integration", "event_source");
        }
        @Override public Component displayName() { return Component.literal(name); }
        @Override public DeviceStatus status() { return DeviceStatus.of(DeviceStatus.State.ONLINE); }
        @Override public Set<ResourceLocation> eventTypes() { return Set.of(EVENT); }
    }
}
