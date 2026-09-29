package fr.lkdm.homelink.dashboard.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.dashboard.widget.EnergyBalance;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.network.DisplaySummary;
import fr.lkdm.homelink.dashboard.network.PreferencesPayloads;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
import fr.lkdm.homelink.dashboard.server.DisplaySummaryService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Home widgets added in 1.5.0: action and energy widgets, the energy balance and shared displays. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeWidgetGameTests {
    private static final ResourceLocation BUTTON = ResourceLocation.parse("homelink_dashboard_validation:widget_button");
    private static final ResourceLocation SLIDER = ResourceLocation.parse("homelink_dashboard_validation:widget_slider");

    private HomeWidgetGameTests() { }

    @GameTest(template = "empty")
    public static void actionWidgetsAcceptOnlyOneClickActions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var owner = player(helper);
        var manager = DashboardAPI.networks(server);
        var network = manager.createNetwork("Action widget fixture", owner.getUUID());
        var device = new ActionDevice();
        DashboardAPI.devices(server).register(device);
        manager.addDevice(network.id(), device.id());
        owner.containerMenu = new DashboardMenu(11, owner.getInventory(), point(helper, owner, network.id()));
        try {
            helper.assertTrue(save(owner, network.id(), widget(DashboardWidget.Type.ACTION, device.id(), BUTTON.toString())) == ActionResult.Code.SUCCESS,
                    "A button action must be accepted as a widget");
            helper.assertTrue(save(owner, network.id(), widget(DashboardWidget.Type.ACTION, device.id(), SLIDER.toString())) == ActionResult.Code.INVALID_PARAMETER,
                    "An action needing a value must stay in the action editor");
            helper.assertTrue(save(owner, network.id(), widget(DashboardWidget.Type.ACTION, device.id(), "homelink_dashboard_validation:missing"))
                    == ActionResult.Code.INVALID_PARAMETER, "An unknown action must be rejected");
            helper.assertTrue(save(owner, network.id(), widget(DashboardWidget.Type.ENERGY_BALANCE, DashboardWidget.NETWORK, "")) == ActionResult.Code.SUCCESS,
                    "The network energy balance needs no device");
            boolean rejected = false;
            try { widget(DashboardWidget.Type.ENERGY_BALANCE, device.id(), ""); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "An energy balance must not reference a device");
        } finally {
            DashboardPreferencesSavedData.get(server).put(owner.getUUID(), network.id(), DashboardProfile.EMPTY);
            DashboardAPI.devices(server).unregister(device.id());
            manager.deleteNetwork(network.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void energyBalanceCountsEachCableNetworkOnce(GameTestHelper helper) {
        var result = EnergyBalance.of(List.of(
                source("homelink_energy:solar_panel", Map.of("homelink_energy:generation_rate", 2.5)),
                source("homelink_energy:wind_turbine", Map.of("homelink_energy:current_generation", 1.25)),
                battery(500, 1000, 3, 7), battery(250, 1000, 3, 7), battery(100, 500, 1, 2),
                source("homelink_farm:irrigation_pump", Map.of("homelink_energy:generation_rate", 99.0))));
        helper.assertTrue(result.production() == 3.75 && result.producers() == 2, "Production adds solar and wind output only");
        helper.assertTrue(result.consumption() == 4 && result.batteries() == 3,
                "Batteries of one cable network report it once; another cable network adds its own consumption");
        helper.assertTrue(result.stored() == 850 && result.capacity() == 2500 && result.net() == -0.25, "Storage and net flow follow the batteries");
        var identicalNetworks = EnergyBalance.of(List.of(battery(100, 500, 2, 1), battery(100, 500, 2, 1)));
        helper.assertTrue(identicalNetworks.consumption() == 4,
                "Two separate one-battery networks with identical measurements both contribute consumption");
        var identicalPairs = EnergyBalance.of(List.of(battery(100, 500, 2, 2), battery(100, 500, 2, 2),
                battery(100, 500, 2, 2), battery(100, 500, 2, 2)));
        helper.assertTrue(identicalPairs.consumption() == 4,
                "Two identical two-battery networks contribute once per cable network");
        helper.assertTrue(EnergyBalance.of(List.of()).charge() == -1, "Without battery there is no charge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sharedDisplayShowsTheOwnersHome(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        var owner = player(helper);
        var viewer = player(helper);
        var manager = DashboardAPI.networks(server);
        var network = manager.createNetwork("Shared display fixture", owner.getUUID());
        manager.setMember(network.id(), viewer.getUUID(), NetworkRole.VIEWER);
        var displayBlock = DashboardRegistries.DASHBOARD_DISPLAY.get();
        helper.getLevel().setBlock(pos, displayBlock.defaultBlockState(), 3);
        displayBlock.setPlacedBy(helper.getLevel(), pos, displayBlock.defaultBlockState(), owner, new ItemStack(displayBlock));
        var display = (DashboardDisplayBlockEntity) helper.getLevel().getBlockEntity(pos);
        var rootPos = pos.offset(3, 0, 0);
        var serverBlock = DashboardRegistries.HOME_SERVER.get();
        helper.getLevel().setBlock(rootPos, serverBlock.defaultBlockState(), 3);
        serverBlock.setPlacedBy(helper.getLevel(), rootPos, serverBlock.defaultBlockState(), owner, new ItemStack(serverBlock));
        ((AccessPointBlockEntity) helper.getLevel().getBlockEntity(rootPos)).setNetworkId(network.id());
        display.setNetworkId(network.id());
        var storage = DashboardPreferencesSavedData.get(server);
        storage.put(owner.getUUID(), network.id(), new DashboardProfile(List.of(widget(DashboardWidget.Type.ENERGY_BALANCE, DashboardWidget.NETWORK, "")), Set.of()));
        try {
            helper.assertTrue(DisplaySummaryService.describe(viewer, display).widgets().isEmpty(),
                    "A personal display shows each viewer their own Home");
            viewer.containerMenu = new DashboardMenu(12, viewer.getInventory(), display);
            helper.assertTrue(!viewer.containerMenu.clickMenuButton(viewer, DashboardMenu.SHARE_LAYOUT) && !display.sharedLayout(),
                    "Only the display owner may share it");
            owner.containerMenu = new DashboardMenu(13, owner.getInventory(), display);
            helper.assertTrue(owner.containerMenu.clickMenuButton(owner, DashboardMenu.SHARE_LAYOUT) && display.sharedLayout(),
                    "The owner shares the display from its menu");
            var shared = DisplaySummaryService.describe(viewer, display);
            helper.assertTrue(shared.mode() == DisplaySummary.Mode.LIVE && shared.widgets().size() == 1
                            && shared.widgets().getFirst().kind() == DisplaySummary.WidgetTile.Kind.ENERGY,
                    "A shared display shows the owner's Home to every viewer");
            manager.removeMember(network.id(), viewer.getUUID());
            helper.assertTrue(DisplaySummaryService.describe(viewer, display).mode() == DisplaySummary.Mode.RESTRICTED,
                    "Sharing never bypasses the viewer's own VIEW right");
            var tag = display.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(tag.getBoolean("SharedLayout"), "The sharing choice must be saved with the display");
        } finally {
            storage.put(owner.getUUID(), network.id(), DashboardProfile.EMPTY);
            manager.deleteNetwork(network.id());
            helper.getLevel().removeBlock(pos, false);
            helper.getLevel().removeBlock(rootPos, false);
        }
        helper.succeed();
    }

    private static ActionResult.Code save(ServerPlayer owner, UUID network, DashboardWidget widget) {
        var request = new PreferencesPayloads.Request(UUID.randomUUID(), owner.containerMenu.containerId, network,
                PreferencesPayloads.Operation.SAVE_LAYOUT, new DashboardProfile(List.of(widget), Set.of()).toTag());
        return PreferencesPayloads.handle(owner, request).result();
    }

    private static DashboardWidget widget(DashboardWidget.Type type, UUID device, String entry) {
        return new DashboardWidget(UUID.randomUUID(), type, device, entry, 0, 0, 6, 3);
    }

    private static EnergyBalance.Source source(String type, Map<String, Double> values) {
        return new EnergyBalance.Source() {
            @Override public String type() { return type; }
            @Override public double number(String metric) { return values.getOrDefault(metric, 0.0); }
        };
    }

    /** A battery whose cable network consumes {@code consumption} HE/t and holds {@code batteries} batteries. */
    private static EnergyBalance.Source battery(long stored, long capacity, double consumption, int batteries) {
        return source("homelink_energy:battery", Map.of("homelink_energy:stored_energy", (double) stored, "homelink_energy:capacity", (double) capacity,
                "homelink_energy:network_consumption", consumption, "homelink_energy:network_batteries", (double) batteries));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "WidgetTest"), ClientInformation.createDefault());
        var position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return player;
    }

    private static AccessPointBlockEntity point(GameTestHelper helper, ServerPlayer owner, UUID network) {
        var position = helper.absolutePos(new BlockPos(1, 1, 1));
        var block = DashboardRegistries.HOME_SERVER.get();
        helper.getLevel().setBlock(position, block.defaultBlockState(), 3);
        block.setPlacedBy(helper.getLevel(), position, block.defaultBlockState(), owner, new ItemStack(block));
        var point = (AccessPointBlockEntity) helper.getLevel().getBlockEntity(position);
        point.setNetworkId(network);
        return point;
    }

    private static final class ActionDevice implements DashboardDevice {
        private final UUID id = UUID.randomUUID();
        private final List<DeviceAction<?>> actions = List.of(
                DeviceAction.button(BUTTON, Component.literal("Start")).handler((context, value) -> ActionResult.success()).build(),
                DeviceAction.slider(SLIDER, Component.literal("Power"), 0, 100).step(5).handler((context, value) -> ActionResult.success()).build());
        @Override public UUID id() { return id; }
        @Override public ResourceLocation deviceType() { return ResourceLocation.parse("homelink_dashboard_validation:widget_device"); }
        @Override public Component displayName() { return Component.literal("Widget device"); }
        @Override public DeviceStatus status() { return DeviceStatus.of(DeviceStatus.State.ONLINE); }
        @Override public List<DeviceAction<?>> actions() { return actions; }
    }

}
