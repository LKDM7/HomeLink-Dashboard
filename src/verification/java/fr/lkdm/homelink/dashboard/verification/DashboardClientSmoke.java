package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.dashboard.block.DashboardDisplayBlock;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.client.state.DisplaySummaryClient;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.network.DisplaySummary;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Explicitly enabled integrated-client verification; never packaged in the release mod. */
@EventBusSubscriber(modid = "homelink_dashboard_validation", value = Dist.CLIENT)
public final class DashboardClientSmoke {
    private static volatile BlockPos serverPosition;
    private static volatile BlockPos displayPosition;
    private static volatile BlockPos repeaterPosition;
    private static volatile BlockPos sizedDisplayPosition;
    private static volatile BlockPos singleFacadePosition;
    private static final List<FacadeFixture> FACADE_FIXTURES = new java.util.ArrayList<>();
    private static volatile Throwable serverFailure;
    private static int stage;
    private static int visibleTicks;
    private static UUID createdNetwork;
    private static volatile UUID oldNetwork;
    private static volatile boolean wrongBindingReady;
    private static boolean repairedDisplay;
    private static volatile boolean namesReady;
    private static long started;

    private DashboardClientSmoke() { }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("homelink.clientSmoke") || stage == 7) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (stage == 0) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                    return;
                }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = 1;
                started = System.nanoTime();
                var rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
                var settings = new LevelSettings("HomeLink Dashboard Verification", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("homelink-smoke-" + System.currentTimeMillis(),
                        settings, new WorldOptions(0L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
                return;
            }
            if (System.nanoTime() - started > 240_000_000_000L) {
                throw new IllegalStateException("Client verification timed out at stage " + stage);
            }
            if (serverFailure != null) throw new IllegalStateException("Fixture creation failed", serverFailure);
            if (stage == 1 && client.player != null && client.level != null
                    && client.screen == null && client.getSingleplayerServer() != null) {
                stage = 2;
                UUID owner = client.player.getUUID();
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(owner);
                        if (player == null) throw new IllegalStateException("Connected player missing");
                        for (int i = 0; i < 4; i++) oldNetwork = fr.lkdm.homecore.api.DashboardAPI.networks(server)
                                .createNetwork("Atelier principal", owner).id();
                        BlockPos first = player.blockPosition().offset(1, 0, 0);
                        BlockPos second = player.blockPosition().offset(0, 0, 1);
                        place(player.serverLevel(), first, DashboardRegistries.HOME_SERVER.get(), owner);
                        place(player.serverLevel(), second, DashboardRegistries.DASHBOARD_DISPLAY.get(), owner);
                        serverPosition = first;
                        displayPosition = second;
                    } catch (Throwable failure) { serverFailure = failure; }
                });
            } else if (stage == 2 && displayPosition != null && ready(client, serverPosition, DashboardRegistries.HOME_SERVER.get())
                    && ready(client, displayPosition, DashboardRegistries.DASHBOARD_DISPLAY.get())) {
                verifyModels(client, DashboardRegistries.HOME_SERVER.get());
                verifyModels(client, DashboardRegistries.DASHBOARD_DISPLAY.get());
                verifyModels(client, DashboardRegistries.SIGNAL_REPEATER.get());
                for (String name : new String[] { "nas_activity", "nas_power", "nas_network", "nas_error" }) {
                    var sprite = client.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                            .apply(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("homelink_dashboard", "block/" + name)).contents();
                    try (var ticker = sprite.createTicker()) {
                        if (sprite.getUniqueFrames().count() != 8 || ticker == null) {
                            throw new IllegalStateException("Missing NAS animated texture: " + name);
                        }
                    }
                    int first = sprite.getOriginalImage().getPixelRGBA(8, 8);
                    boolean changes = false;
                    for (int frame = 1; frame < 8; frame++) changes |= first != sprite.getOriginalImage().getPixelRGBA(8, 8 + frame * 16);
                    if (!changes) throw new IllegalStateException("NAS animation is static: " + name);
                }
                interact(client, serverPosition.above());
                stage = 3;
            } else if (stage == 3 && isExpectedScreen(client, serverPosition)) {
                if (++visibleTicks < 10) return;
                var screen = (DashboardScreen) client.screen;
                if (!screen.getMenu().session().canCreate()) throw new IllegalStateException("Owner cannot configure server");
                // Type the name key by key: "e" is the inventory key and used to close the screen.
                var input = nameInput(screen);
                if (screen.getFocused() != input) throw new IllegalStateException("The network name field must be focused on open");
                input.setValue("");
                for (char character : "Atelier principal".toCharArray()) {
                    int key = Character.isLetter(character) ? org.lwjgl.glfw.GLFW.GLFW_KEY_A + Character.toUpperCase(character) - 'A'
                            : org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
                    screen.keyPressed(key, 0, 0);
                    screen.charTyped(character, 0);
                }
                if (client.screen != screen || !input.getValue().equals("Atelier principal"))
                    throw new IllegalStateException("Typing a network name closed the screen or lost keys: " + input.getValue());
                press(screen, "create");
                stage = 30;
            } else if (stage == 30 && isExpectedScreen(client, serverPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                createdNetwork = ((DashboardScreen) client.screen).state().networkId();
                if (!((DashboardScreen) client.screen).state().role().equals("OWNER")) throw new IllegalStateException("Created network owner missing");
                var screen = (DashboardScreen) client.screen;
                if (!screen.state().networkName().equals("Atelier principal")) throw new IllegalStateException("Creation ignored the entered network name");
                screen.showNetwork();
                press(screen, "rename");
                nameInput(screen).setValue("Entrepôt principal");
                screen.network().tick();
                press(screen, "save_name");
                stage = 31; visibleTicks = 0;
            } else if (stage == 31 && client.screen instanceof DashboardScreen screen
                    && screen.state().networkName().equals("Entrepôt principal")) {
                if (++visibleTicks < 10) return;
                verifyUiControls(screen);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-network-rename.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Network rename screenshot: {}", message.getString()));
                LogUtils.getLogger().info("HOMELINK_NETWORK_NAMES_OK create_field=true rename_form=true live_update=true unicode=true");
                // A real HomeLink Energy solar panel, placed like a player would, outside any network.
                var server = client.getSingleplayerServer();
                UUID owner = client.player.getUUID();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(owner);
                        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.ResourceLocation.parse("homelink_energy:solar_panel_1"));
                        var ground = serverPosition.offset(4, -1, -5);
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(item));
                        var placed = player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player,
                                net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                                        net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0), net.minecraft.core.Direction.UP, ground, false)));
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                        if (!placed.consumesAction()) throw new IllegalStateException("Solar panel fixture could not be placed: " + placed);
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                visibleTicks = 0;
                stage = 33;
            } else if (stage == 33 && client.screen instanceof DashboardScreen screen) {
                // Give HomeLink Energy a moment to register the panel with HomeCore, then list the radio area.
                if (++visibleTicks == 20) screen.showDiscovery();
                if (visibleTicks < 20 || screen.discovery() == null || screen.discovery().pending()) return;
                var panel = screen.discovery().entries().stream()
                        .filter(entry -> entry.type().equals("homelink_energy:solar_panel") && entry.canAdd()).findFirst();
                if (panel.isEmpty()) {
                    if (visibleTicks > 200) throw new IllegalStateException("Add tab did not list the nearby solar panel: " + screen.discovery().entries());
                    if (visibleTicks % 20 == 0) screen.discovery().refresh();
                    return;
                }
                verifyUiControls(screen);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-discovery.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Discovery screenshot: {}", message.getString()));
                screen.discovery().add(panel.orElseThrow().id());
                visibleTicks = 0;
                stage = 34;
            } else if (stage == 34 && client.screen instanceof DashboardScreen screen && !screen.discovery().pending()) {
                if (screen.state().devices().stream().noneMatch(device -> device.type().equals("homelink_energy:solar_panel"))) {
                    if (++visibleTicks > 200) throw new IllegalStateException("Added solar panel never reached the Dashboard");
                    return;
                }
                LogUtils.getLogger().info("HOMELINK_DISCOVERY_OK listed=true added=true");
                screen.showMachines();
                visibleTicks = 0;
                stage = 32;
            } else if (stage == 32 && client.screen instanceof DashboardScreen screen) {
                if (++visibleTicks < 10) return;
                screen.machines().tick();
                var energy = screen.machines().summaries().get(fr.lkdm.homelink.dashboard.client.state.MachineSystems.Group.ENERGY);
                if (screen.machines().summaries().size() != 3 || energy == null || energy.total() != 1)
                    throw new IllegalStateException("Machines must show the added panel under Energy and hide empty storage");
                screen.machines().select(fr.lkdm.homelink.dashboard.client.state.MachineSystems.Group.ENERGY);
                verifyUiControls(screen);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-machines.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Machines screenshot: {}", message.getString()));
                LogUtils.getLogger().info("HOMELINK_MACHINES_OK energy=1");
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var manager = fr.lkdm.homecore.api.DashboardAPI.networks(server);
                        for (var network : manager.getAll()) manager.renameNetwork(network.id(), "Entrepôt principal");
                        for (int index = 0; index < 4; index++) {
                            var fixture = new FacadeFixture(index);
                            FACADE_FIXTURES.add(fixture);
                            DashboardAPI.devices(server).register(fixture);
                            manager.addDevice(createdNetwork, fixture.id());
                        }
                        // Widgets make the displays mirror this Home layout: two half tiles above a full one.
                        var widget = fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.DEVICE_SUMMARY;
                        var layout = List.of(
                                new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(), widget,
                                        FACADE_FIXTURES.get(0).id(), "", 0, 0, 6, 3),
                                new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(),
                                        fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.METRIC,
                                        FACADE_FIXTURES.get(1).id(), "homelink_dashboard_validation:level", 6, 0, 6, 3),
                                new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(),
                                        fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.ENERGY_BALANCE,
                                        fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.NETWORK, "", 0, 3, 3, 2),
                                new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(),
                                        fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.Type.METRIC,
                                        FACADE_FIXTURES.get(3).id(), "homelink_dashboard_validation:level", 3, 3, 3, 2),
                                new fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget(UUID.randomUUID(), widget,
                                        FACADE_FIXTURES.get(2).id(), "", 6, 3, 6, 3));
                        DashboardPreferencesSavedData.get(server).put(client.player.getUUID(), createdNetwork,
                                new DashboardProfile(layout, FACADE_FIXTURES.stream().map(FacadeFixture::id)
                                        .collect(java.util.stream.Collectors.toSet())));
                        namesReady = true;
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                client.screen.onClose();
                visibleTicks = 0;
                stage = 4;
            } else if (stage == 4 && client.screen == null && namesReady) {
                interact(client, displayPosition);
                stage = 5;
            } else if (stage == 5 && isExpectedScreen(client, displayPosition)) {
                if (++visibleTicks < 10) return;
                var screen = (DashboardScreen) client.screen;
                int index = -1;
                if (!screen.getMenu().session().choices().getFirst().id().equals(createdNetwork)
                        || !screen.getMenu().session().choices().getFirst().inRange())
                    throw new IllegalStateException("Setup did not prefer the nearby server among five identically named networks");
                for (int i = 0; i < screen.getMenu().session().choices().size(); i++) {
                    if (screen.getMenu().session().choices().get(i).id().equals(createdNetwork)) index = i;
                }
                if (index < 0) throw new IllegalStateException("Created network absent from display binding choices");
                verifyUiControls(screen);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-network-selection.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Network selection screenshot: {}", message.getString()));
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DashboardMenu.BIND_NETWORK_BASE + index);
                stage = 50;
            } else if (stage == 50 && isExpectedScreen(client, displayPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                if (!((DashboardScreen) client.screen).state().networkId().equals(createdNetwork)) throw new IllegalStateException("Display bound wrong network");
                client.screen.onClose();
                if (!repairedDisplay) {
                    repairedDisplay = true; visibleTicks = 0; stage = 51;
                    client.getSingleplayerServer().execute(() -> {
                        try {
                            ((AccessPointBlockEntity) client.getSingleplayerServer().overworld().getBlockEntity(displayPosition)).setNetworkId(oldNetwork);
                            wrongBindingReady = true;
                        } catch (Throwable failure) { serverFailure = failure; }
                    });
                } else {
                    LogUtils.getLogger().info("HOMELINK_NETWORK_REPAIR_OK duplicate_names=5 reachable_first=true sneak_rebind=true");
                    stage = 6;
                }
            } else if (stage == 51 && wrongBindingReady && client.screen == null && ++visibleTicks >= 10) {
                client.player.setShiftKeyDown(true);
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(client.player,
                        net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                interact(client, displayPosition);
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(client.player,
                        net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                client.player.setShiftKeyDown(false);
                visibleTicks = 0;
                stage = 5;
            } else if (stage == 6 && client.screen == null) {
                if (client.player.containerMenu != client.player.inventoryMenu) {
                    throw new IllegalStateException("Closing the dashboard did not restore the inventory menu");
                }
                stage = 60;
                visibleTicks = 0;
                client.options.hideGui = true;
                client.options.fov().set(35);
                UUID owner = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(owner);
                    if (player != null) {
                        player.serverLevel().setDayTime(6000);
                        player.serverLevel().removeBlock(displayPosition, false);
                        player.connection.teleport(serverPosition.getX() + 3.5, serverPosition.getY() + 0.5,
                                serverPosition.getZ() - 5.5, 26.565f, 9.0f);
                    }
                });
            } else if (stage == 60 && ++visibleTicks >= 30) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-server-model.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Server model screenshot: {}", message.getString()));
                stage = 61;
                visibleTicks = 0;
                UUID owner = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(owner);
                    if (player != null) {
                        var pos = serverPosition.offset(3, 1, 0);
                        player.serverLevel().setBlock(pos.south(), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        place(player.serverLevel(), pos, DashboardRegistries.DASHBOARD_DISPLAY.get(), owner);
                        ((AccessPointBlockEntity) player.serverLevel().getBlockEntity(pos)).setNetworkId(createdNetwork);
                        singleFacadePosition = pos;
                        player.connection.teleport(pos.getX() + 1.5, pos.getY() - 1, pos.getZ() - 3.5, 12.53f, 1.5f);
                    }
                });
            } else if (stage == 61 && facadeReady(client, singleFacadePosition, 73) && ++visibleTicks >= 30) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-display-model.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Display model screenshot: {}", message.getString()));
                stage = 610; visibleTicks = 0;
                showDisplay(client, DashboardDisplayBlock.Size.WIDE);
            } else if (stage == 610 && sizedDisplayReady(client, DashboardDisplayBlock.Size.WIDE)
                    && facadeReady(client, sizedDisplayPosition, 73) && ++visibleTicks >= 30) {
                verifyDisplayCamera(client, DashboardDisplayBlock.Size.WIDE);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-display-wide-model.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Wide display model screenshot: {}", message.getString()));
                client.options.hideGui = false;
                interact(client, sizedDisplayPosition.west());
                stage = 6101;
            } else if (stage == 6101 && isExpectedScreen(client, sizedDisplayPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                if (!((DashboardScreen) client.screen).state().networkId().equals(createdNetwork))
                    throw new IllegalStateException("Wide satellite opened the wrong network");
                client.screen.onClose(); client.options.hideGui = true;
                stage = 611; visibleTicks = 0;
                showDisplay(client, DashboardDisplayBlock.Size.LARGE);
            } else if (stage == 611 && sizedDisplayReady(client, DashboardDisplayBlock.Size.LARGE)
                    && facadeReady(client, sizedDisplayPosition, 73) && ++visibleTicks >= 30) {
                if (client.screen != null || client.player.containerMenu != client.player.inventoryMenu)
                    throw new IllegalStateException("Facade must update with no menu open");
                client.getSingleplayerServer().execute(() -> FACADE_FIXTURES.getFirst().charge.setValue(new Percentage(81)));
                stage = 6110; visibleTicks = 0;
            } else if (stage == 6110 && facadeReady(client, sizedDisplayPosition, 81) && ++visibleTicks >= 10) {
                if (client.screen != null) throw new IllegalStateException("Live facade unexpectedly opened a menu");
                LogUtils.getLogger().info("HOMELINK_DISPLAY_FAVORITES_OK favorites=4 metrics_each=2 live_value=73_to_81 menu_open=false");
                verifyDisplayCamera(client, DashboardDisplayBlock.Size.LARGE);
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-display-large-model.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Large display model screenshot: {}", message.getString()));
                LogUtils.getLogger().info("HOMELINK_DISPLAY_SIZES_OK sizes=3 screenshots=3 facing_right=counterclockwise");
                client.options.hideGui = false;
                interact(client, sizedDisplayPosition.west().above());
                stage = 612;
            } else if (stage == 612 && isExpectedScreen(client, sizedDisplayPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                if (!((DashboardScreen) client.screen).state().networkId().equals(createdNetwork))
                    throw new IllegalStateException("Large satellite opened the wrong network");
                client.screen.onClose();
                stage = 613; visibleTicks = 0;
            } else if (stage == 613 && client.screen == null && ++visibleTicks >= 10) {
                if (!client.player.getMainHandItem().isEmpty())
                    throw new IllegalStateException("Satellite reassociation requires an empty hand");
                client.player.setShiftKeyDown(true);
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(client.player,
                        net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                interact(client, sizedDisplayPosition.west().above());
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(client.player,
                        net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                client.player.setShiftKeyDown(false);
                stage = 614;
            } else if (stage == 614 && isExpectedScreen(client, sizedDisplayPosition)) {
                var screen = (DashboardScreen) client.screen;
                if (screen.state() != null)
                    throw new IllegalStateException("Sneaking on a satellite did not reset the master's binding");
                int index = -1;
                for (int i = 0; i < screen.getMenu().session().choices().size(); i++) {
                    if (screen.getMenu().session().choices().get(i).id().equals(createdNetwork)) index = i;
                }
                if (index < 0) throw new IllegalStateException("Network missing from satellite reassociation");
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DashboardMenu.BIND_NETWORK_BASE + index);
                stage = 615;
            } else if (stage == 615 && isExpectedScreen(client, sizedDisplayPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                if (!((DashboardScreen) client.screen).state().networkId().equals(createdNetwork))
                    throw new IllegalStateException("Satellite reassociated the wrong network");
                LogUtils.getLogger().info("HOMELINK_DISPLAY_INTERACTION_OK wide=bottom_right large=top_right menu=master sneak_rebind=true");
                client.screen.onClose(); client.options.hideGui = true;
                stage = 62; visibleTicks = 0;
                UUID owner = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(owner);
                    try {
                        var pos = serverPosition.offset(6, 0, 0);
                        place(player.serverLevel(), pos, DashboardRegistries.SIGNAL_REPEATER.get(), owner);
                        repeaterPosition = pos;
                        player.connection.teleport(pos.getX() + 1.5, pos.getY(), pos.getZ() - 3.5, 12.53f, 15.0f);
                    } catch (Throwable failure) { serverFailure = failure; }
                });
            } else if (stage == 62 && repeaterPosition != null && ++visibleTicks >= 30
                    && ready(client, repeaterPosition, DashboardRegistries.SIGNAL_REPEATER.get())) {
                client.options.hideGui = false;
                interact(client, repeaterPosition); stage = 63;
            } else if (stage == 63 && isExpectedScreen(client, repeaterPosition)) {
                var screen = (DashboardScreen) client.screen;
                if (screen.getMenu().session().canCreate()) throw new IllegalStateException("Repeater must not create networks");
                if (!screen.getMenu().session().choices().getFirst().id().equals(createdNetwork)
                        || !screen.getMenu().session().choices().getFirst().inRange())
                    throw new IllegalStateException("Repeater did not prefer the nearby server network");
                int index = -1;
                for (int i = 0; i < screen.getMenu().session().choices().size(); i++) {
                    if (screen.getMenu().session().choices().get(i).id().equals(createdNetwork)) index = i;
                }
                if (index < 0) throw new IllegalStateException("Network missing from repeater setup");
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DashboardMenu.BIND_NETWORK_BASE + index);
                stage = 64;
            } else if (stage == 64 && isExpectedScreen(client, repeaterPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                if (!((DashboardScreen) client.screen).state().networkId().equals(createdNetwork)) throw new IllegalStateException("Repeater bound wrong network");
                client.screen.onClose(); client.options.hideGui = true;
                stage = 65; visibleTicks = 0;
            } else if (stage == 65 && ++visibleTicks >= 30) {
                var state = client.level.getBlockState(repeaterPosition);
                if (state.getValue(fr.lkdm.homelink.dashboard.block.AccessPointBlock.STATUS)
                        != fr.lkdm.homelink.dashboard.block.AccessPointStatus.ONLINE) throw new IllegalStateException("Repeater is not connected");
                var model = client.getBlockRenderer().getBlockModel(state);
                boolean animatedLed = model.getQuads(state, null, net.minecraft.util.RandomSource.create(1)).stream()
                        .anyMatch(quad -> quad.getSprite().contents().name().toString().equals("homelink_dashboard:block/nas_activity")
                                && quad.getSprite().contents().getUniqueFrames().count() == 8);
                if (!animatedLed) throw new IllegalStateException("Connected repeater model does not use animated green LED");
                var offline = state.setValue(fr.lkdm.homelink.dashboard.block.AccessPointBlock.STATUS,
                        fr.lkdm.homelink.dashboard.block.AccessPointStatus.OFFLINE);
                if (client.getBlockRenderer().getBlockModel(offline).getQuads(offline, null, net.minecraft.util.RandomSource.create(1)).stream()
                        .anyMatch(quad -> quad.getSprite().contents().name().toString().equals("homelink_dashboard:block/nas_activity")))
                    throw new IllegalStateException("Disconnected repeater must not blink green");
                LogUtils.getLogger().info("HOMELINK_REPEATER_LED_OK connected_animated=true offline_static=true");
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-repeater-model.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Repeater model screenshot: {}", message.getString()));
                stage = 7;
                LogUtils.getLogger().info("HOMELINK_CLIENT_SMOKE_OK blocks=3 interaction_packets=3 screens=3 models=all_states create_network=true bind_display=true bind_repeater=true close=true");
                shutdown(client);
            }
        } catch (Throwable failure) {
            stage = 7;
            LogUtils.getLogger().error("HOMELINK_CLIENT_SMOKE_FAILED", failure);
            shutdown(client);
        }
    }

    private static void verifyUiControls(DashboardScreen screen) {
        var widgets = screen.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                .filter(widget -> widget.visible && widget.active).toList();
        var previous = screen.getFocused();
        for (var widget : widgets) {
            if (widget.getX() < 0 || widget.getY() < 0 || widget.getX() + widget.getWidth() > screen.width
                    || widget.getY() + widget.getHeight() > screen.height)
                throw new IllegalStateException("Dashboard control outside viewport: " + widget.getMessage().getString());
        }
        var visited = new java.util.HashSet<net.minecraft.client.gui.components.AbstractWidget>();
        screen.setFocused(null);
        for (var widget : widgets) widget.setFocused(false);
        for (int index = 0; index < widgets.size() * 3 && visited.size() < widgets.size(); index++) {
            screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0);
            if (!(screen.getFocused() instanceof net.minecraft.client.gui.components.AbstractWidget focused)
                    || !focused.isFocused() || !focused.active || !focused.visible)
                throw new IllegalStateException("Dashboard native Tab focus failed");
            visited.add(focused);
        }
        if (!visited.containsAll(widgets)) throw new IllegalStateException("Dashboard Tab did not reach every control");
        screen.setFocused(previous);
        if (previous != null) previous.setFocused(true);
        LogUtils.getLogger().info("HOMELINK_UI_CONTROLS_OK width={} height={} controls={} keyboard=true",
                screen.width, screen.height, widgets.size());
    }

    private static net.minecraft.client.gui.components.EditBox nameInput(DashboardScreen screen) {
        return screen.children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void press(DashboardScreen screen, String key) {
        String label = net.minecraft.network.chat.Component.translatable("screen.homelink_dashboard." + key).getString();
        var button = screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast).filter(candidate -> candidate.getMessage().getString().equals(label))
                .findFirst().orElseThrow();
        if (!button.active) throw new IllegalStateException("Disabled control: " + key);
        button.onPress();
    }
    private static void place(net.minecraft.server.level.ServerLevel level, BlockPos position, Block block, UUID owner) {
        if (!level.setBlock(position, block.defaultBlockState(), 3)) throw new IllegalStateException("Block placement failed");
        if (!(level.getBlockEntity(position) instanceof AccessPointBlockEntity point)) {
            throw new IllegalStateException("Block entity missing at " + position);
        }
        point.initializeOwner(owner);
        block.setPlacedBy(level, position, block.defaultBlockState(), null, new net.minecraft.world.item.ItemStack(block));
    }

    private static boolean ready(Minecraft client, BlockPos position, Block block) {
        return client.level != null && client.player != null && client.gameMode != null
                && client.level.getBlockState(position).is(block);
    }

    private static void showDisplay(Minecraft client, DashboardDisplayBlock.Size size) {
        UUID owner = client.player.getUUID();
        client.player.getAbilities().flying = true;
        client.player.setNoGravity(true);
        client.player.setDeltaMovement(Vec3.ZERO);
        sizedDisplayPosition = null;
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(owner);
                if (player == null) throw new IllegalStateException("Connected player missing");
                var level = player.serverLevel();
                var pos = serverPosition.offset(size == DashboardDisplayBlock.Size.WIDE ? 10 : 15, 3, 0);
                var block = DashboardRegistries.DASHBOARD_DISPLAY.get();
                var state = block.defaultBlockState().setValue(DashboardDisplayBlock.SIZE, size)
                        .setValue(DashboardDisplayBlock.PART, DashboardDisplayBlock.Part.BOTTOM_LEFT)
                        .setValue(DashboardDisplayBlock.FACING, Direction.NORTH);
                for (int column = 0; column < size.width(); column++) {
                    for (int row = 0; row < size.height(); row++) {
                        level.setBlock(pos.west(column).above(row).south(),
                                net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) throw new IllegalStateException("Display placement failed");
                block.setPlacedBy(level, pos, state, player, new net.minecraft.world.item.ItemStack(block));
                if (!(level.getBlockEntity(pos) instanceof AccessPointBlockEntity point))
                    throw new IllegalStateException("Display master missing");
                point.setNetworkId(createdNetwork);
                sizedDisplayPosition = pos;
                // North-facing panels extend west: face them head-on to expose frame seams.
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.teleport(pos.getX() + 1.0 - size.width() / 2.0,
                        pos.getY() + size.height() / 2.0 - player.getEyeHeight(), pos.getZ() - 4.5, 0, 0);
            } catch (Throwable failure) { serverFailure = failure; }
        });
    }

    private static void verifyDisplayCamera(Minecraft client, DashboardDisplayBlock.Size size) {
        var pos = sizedDisplayPosition;
        var expectedEye = new Vec3(pos.getX() + 1.0 - size.width() / 2.0,
                pos.getY() + size.height() / 2.0, pos.getZ() - 4.5);
        if (client.player.getEyePosition().distanceTo(expectedEye) > 0.1
                || Math.abs(client.player.getXRot()) > 0.1 || Math.abs(client.player.getYRot()) > 0.1)
            throw new IllegalStateException("Display camera moved before capture: " + client.player.getEyePosition());
    }

    private static boolean sizedDisplayReady(Minecraft client, DashboardDisplayBlock.Size size) {
        var pos = sizedDisplayPosition;
        if (pos == null || client.level == null) return false;
        for (var part : DashboardDisplayBlock.Part.values()) {
            if (part.column() >= size.width() || part.row() >= size.height()) continue;
            var cell = pos.west(part.column()).above(part.row());
            var state = client.level.getBlockState(cell);
            if (!state.is(DashboardRegistries.DASHBOARD_DISPLAY.get())
                    || state.getValue(DashboardDisplayBlock.SIZE) != size
                    || state.getValue(DashboardDisplayBlock.PART) != part) return false;
            if ((client.level.getBlockEntity(cell) != null) != (part == DashboardDisplayBlock.Part.BOTTOM_LEFT))
                throw new IllegalStateException("Display block entity on wrong part: " + part);
        }
        return true;
    }

    private static boolean facadeReady(Minecraft client, BlockPos pos, int charge) {
        if (pos == null || !(client.level.getBlockEntity(pos) instanceof DashboardDisplayBlockEntity display)) return false;
        var summary = DisplaySummaryClient.get(display);
        if (summary == null || summary.mode() != DisplaySummary.Mode.LIVE) return false;
        if (summary.total() != 4 || summary.devices().size() != 4
                || summary.devices().stream().anyMatch(device -> device.metrics().size() != 2))
            throw new IllegalStateException("Facade must receive only the four personal favorites and their two metrics");
        return summary.devices().getFirst().metrics().getFirst().value().value() instanceof Percentage percent
                && percent.value() == charge;
    }

    private static final class FacadeFixture implements DashboardDevice {
        private final UUID id;
        private final int index;
        private final DeviceMetric<Percentage> charge;
        private final List<DeviceMetric<?>> metrics;
        FacadeFixture(int index) {
            this.index = index;
            id = UUID.nameUUIDFromBytes(("facade-fixture-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            charge = DeviceMetric.builder(ResourceLocation.parse("homelink_dashboard_validation:level"),
                    Component.literal(new String[] {"Charge", "Humidite", "Progression", "Remplissage"}[index]),
                    MetricTypes.PERCENTAGE, new Percentage(new int[] {73, 82, 42, 65}[index])).build();
            metrics = List.of(charge, DeviceMetric.builder(ResourceLocation.parse("homelink_dashboard_validation:quantity"),
                    Component.literal(new String[] {"Energie HE", "Cultures", "Blocs extraits", "Volume mB"}[index]),
                    MetricTypes.INTEGER, new int[] {18000, 240, 1536, 12000}[index]).build());
        }
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.parse("homelink_dashboard_validation:facade_fixture"); }
        @Override public Component displayName() {
            return Component.literal(new String[] {"Batterie", "Culture", "Excavatrice", "Reservoir"}[index]);
        }
        @Override public DeviceStatus status() { return index == 2 ? DeviceStatus.WARNING : DeviceStatus.ONLINE; }
        @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    }

    private static void verifyModels(Minecraft client, Block block) {
        var missing = client.getModelManager().getMissingModel();
        int checked = 0;
        for (var state : block.getStateDefinition().getPossibleStates()) {
            if (block instanceof DashboardDisplayBlock) {
                var size = state.getValue(DashboardDisplayBlock.SIZE);
                var part = state.getValue(DashboardDisplayBlock.PART);
                if (part.column() >= size.width() || part.row() >= size.height()) continue;
            }
            var model = client.getBlockRenderer().getBlockModel(state);
            if (model == missing) throw new IllegalStateException("Missing baked model: " + state);
            if (model.getParticleIcon().contents().name().getPath().equals("missingno")) {
                throw new IllegalStateException("Missing particle texture: " + state);
            }
            for (var quad : model.getQuads(state, null, net.minecraft.util.RandomSource.create(1))) {
                if (quad.getSprite().contents().name().getPath().equals("missingno"))
                    throw new IllegalStateException("Missing face texture: " + state);
            }
            checked++;
        }
        if (block instanceof DashboardDisplayBlock) {
            if (checked != 84) throw new IllegalStateException("Expected 84 valid display models, checked " + checked);
            LogUtils.getLogger().info("HOMELINK_DISPLAY_MODELS_OK states={}", checked);
        }
    }

    private static void interact(Minecraft client, BlockPos position) {
        var result = client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        if (!result.consumesAction()) throw new IllegalStateException("Block did not consume interaction: " + position);
    }

    private static boolean isExpectedScreen(Minecraft client, BlockPos position) {
        if (!(client.screen instanceof DashboardScreen screen)) return false;
        if (!screen.getMenu().position().equals(position)) throw new IllegalStateException("Menu received the wrong access point");
        return true;
    }

    private static void shutdown(Minecraft client) {
        try {
            if (client.level != null) client.level.disconnect();
            client.disconnect();
            if (DisplaySummaryClient.size() != 0) throw new IllegalStateException("Facade cache survived disconnect");
            LogUtils.getLogger().info("HOMELINK_DISPLAY_CACHE_CLEARED_OK");
        } finally {
            LogUtils.getLogger().info("HOMELINK_CLIENT_SMOKE_SHUTDOWN_OK");
            client.stop();
        }
    }
}
