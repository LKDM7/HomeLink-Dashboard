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
    private static volatile Throwable serverFailure;
    private static int stage;
    private static int visibleTicks;
    private static UUID createdNetwork;
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
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DashboardMenu.CREATE_NETWORK);
                stage = 30;
            } else if (stage == 30 && isExpectedScreen(client, serverPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                createdNetwork = ((DashboardScreen) client.screen).state().networkId();
                if (!((DashboardScreen) client.screen).state().role().equals("OWNER")) throw new IllegalStateException("Created network owner missing");
                client.screen.onClose();
                visibleTicks = 0;
                stage = 4;
            } else if (stage == 4 && client.screen == null) {
                interact(client, displayPosition);
                stage = 5;
            } else if (stage == 5 && isExpectedScreen(client, displayPosition)) {
                if (++visibleTicks < 10) return;
                var screen = (DashboardScreen) client.screen;
                int index = -1;
                for (int i = 0; i < screen.getMenu().session().choices().size(); i++) {
                    if (screen.getMenu().session().choices().get(i).id().equals(createdNetwork)) index = i;
                }
                if (index < 0) throw new IllegalStateException("Created network absent from display binding choices");
                client.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, DashboardMenu.BIND_NETWORK_BASE + index);
                stage = 50;
            } else if (stage == 50 && isExpectedScreen(client, displayPosition)
                    && ((DashboardScreen) client.screen).state() != null
                    && !((DashboardScreen) client.screen).state().loading()) {
                if (!((DashboardScreen) client.screen).state().networkId().equals(createdNetwork)) throw new IllegalStateException("Display bound wrong network");
                client.screen.onClose();
                stage = 6;
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
                stage = 7;
                LogUtils.getLogger().info("HOMELINK_CLIENT_SMOKE_OK blocks=2 interaction_packets=2 screens=2 models=all_states create_network=true bind_display=true close=true");
                shutdown(client);
            }
        } catch (Throwable failure) {
            stage = 7;
            LogUtils.getLogger().error("HOMELINK_CLIENT_SMOKE_FAILED", failure);
            shutdown(client);
        }
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
