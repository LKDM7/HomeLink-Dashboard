package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import java.util.UUID;
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
                nameInput(screen).setValue("Atelier principal");
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
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-network-rename.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Network rename screenshot: {}", message.getString()));
                LogUtils.getLogger().info("HOMELINK_NETWORK_NAMES_OK create_field=true rename_form=true live_update=true unicode=true");
                var server = client.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        var manager = fr.lkdm.homecore.api.DashboardAPI.networks(server);
                        for (var network : manager.getAll()) manager.renameNetwork(network.id(), "Entrepôt principal");
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
                        player.connection.teleport(pos.getX() + 1.5, pos.getY() - 1, pos.getZ() - 3.5, 12.53f, 1.5f);
                    }
                });
            } else if (stage == 61 && ++visibleTicks >= 30) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, "homelink-display-model.png", client.getMainRenderTarget(),
                        message -> LogUtils.getLogger().info("Display model screenshot: {}", message.getString()));
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

    private static void verifyModels(Minecraft client, Block block) {
        var missing = client.getModelManager().getMissingModel();
        for (var state : block.getStateDefinition().getPossibleStates()) {
            var model = client.getBlockRenderer().getBlockModel(state);
            if (model == missing) throw new IllegalStateException("Missing baked model: " + state);
            if (model.getParticleIcon().contents().name().getPath().equals("missingno")) {
                throw new IllegalStateException("Missing particle texture: " + state);
            }
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
        } finally {
            LogUtils.getLogger().info("HOMELINK_CLIENT_SMOKE_SHUTDOWN_OK");
            client.stop();
        }
    }
}
