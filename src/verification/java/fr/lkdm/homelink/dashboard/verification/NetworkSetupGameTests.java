package fr.lkdm.homelink.dashboard.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardNetworks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server service tests use authenticated player identities without opening fake connections. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkSetupGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void serverCreatesHomeCoreNetwork(GameTestHelper helper) {
        var owner = player(helper);
        var point = place(helper, DashboardRegistries.HOME_SERVER.get(), owner);
        var manager = DashboardAPI.networks(helper.getLevel().getServer());
        helper.assertTrue(DashboardNetworks.canSetup(owner, point), "Unbound physical owner must be able to configure");
        helper.assertTrue(DashboardNetworks.describe(owner, point, 0).canCreate(), "Server setup must offer network creation");
        var menu = new DashboardMenu(1, owner.getInventory(), point);
        helper.assertTrue(menu.stillValid(owner), "Initial unbound setup session must be valid");
        helper.assertTrue(DashboardNetworks.create(owner, point) == ActionResult.Code.SUCCESS, "Authorized creation must succeed");
        var network = manager.getNetwork(point.networkId().orElseThrow()).orElseThrow();
        helper.assertTrue(network.owner().equals(owner.getUUID()), "HomeCore must store the authenticated owner");
        helper.assertTrue(manager.getNetworksForPlayer(owner.getUUID()).stream().anyMatch(value -> value.id().equals(network.id())),
                "Created network must be present in HomeCore's server manager");
        helper.assertTrue(!menu.stillValid(owner), "Binding must invalidate the old unbound setup session");
        helper.assertTrue(!DashboardNetworks.canSetup(owner, point), "Already bound access point must reject setup");
        var bound = new DashboardMenu(2, owner.getInventory(), point);
        helper.assertTrue(bound.stillValid(owner) && bound.session().networkId().orElseThrow().equals(network.id()),
                "Replacement menu must capture the authoritative association");
        manager.deleteNetwork(network.id());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void displayBindsButCannotCreate(GameTestHelper helper) {
        var owner = player(helper);
        var point = place(helper, DashboardRegistries.DASHBOARD_DISPLAY.get(), owner);
        var manager = DashboardAPI.networks(helper.getLevel().getServer());
        var network = manager.createNetwork("Display binding", owner.getUUID());
        var description = DashboardNetworks.describe(owner, point, 0);
        helper.assertTrue(!description.canCreate(), "Displays must not offer server network creation");
        helper.assertTrue(description.choices().stream().anyMatch(choice -> choice.id().equals(network.id())),
                "CONFIGURE-authorized network must appear among binding choices");
        int networkCount = manager.getAll().size();
        helper.assertTrue(DashboardNetworks.create(owner, point) == ActionResult.Code.DENIED, "Forged display creation must be denied");
        helper.assertTrue(point.networkId().isEmpty() && manager.getAll().size() == networkCount,
                "Denied creation must not mutate association or create a network");
        helper.assertTrue(DashboardNetworks.bind(owner, point, network.id()) == ActionResult.Code.SUCCESS,
                "Display owner with network CONFIGURE must be able to bind");
        helper.assertTrue(point.networkId().orElseThrow().equals(network.id()), "Display must retain HomeCore network identity");
        manager.deleteNetwork(network.id());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void setupRejectsStrangersAndViewers(GameTestHelper helper) {
        var owner = player(helper);
        var stranger = player(helper);
        var point = place(helper, DashboardRegistries.HOME_SERVER.get(), owner);
        var manager = DashboardAPI.networks(helper.getLevel().getServer());
        var strangersNetwork = manager.createNetwork("Stranger-owned network", stranger.getUUID());
        helper.assertTrue(!DashboardNetworks.canSetup(stranger, point), "Network ownership must not bypass physical ownership");
        helper.assertTrue(DashboardNetworks.bind(stranger, point, strangersNetwork.id()) == ActionResult.Code.DENIED,
                "Stranger must not bind another player's block");
        helper.assertTrue(DashboardNetworks.create(stranger, point) == ActionResult.Code.DENIED,
                "Stranger must not create a network through another player's block");
        manager.setMember(strangersNetwork.id(), owner.getUUID(), NetworkRole.VIEWER);
        helper.assertTrue(DashboardNetworks.describe(owner, point, 0).choices().stream()
                .noneMatch(choice -> choice.id().equals(strangersNetwork.id())), "VIEWER-only networks must not be offered for binding");
        helper.assertTrue(DashboardNetworks.bind(owner, point, strangersNetwork.id()) == ActionResult.Code.DENIED,
                "Physical owner with VIEW but without CONFIGURE must be denied");
        helper.assertTrue(point.networkId().isEmpty(), "Unauthorized attempts must leave access point unbound");
        manager.deleteNetwork(strangersNetwork.id());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changedBindingInvalidatesSession(GameTestHelper helper) {
        var owner = player(helper);
        var point = place(helper, DashboardRegistries.HOME_SERVER.get(), owner);
        var manager = DashboardAPI.networks(helper.getLevel().getServer());
        var first = manager.createNetwork("First session", owner.getUUID());
        var second = manager.createNetwork("Replacement session", owner.getUUID());
        point.setNetworkId(first.id());
        var menu = new DashboardMenu(1, owner.getInventory(), point);
        helper.assertTrue(menu.stillValid(owner), "Current binding must produce a valid session");
        point.setNetworkId(second.id());
        helper.assertTrue(!menu.stillValid(owner), "Session must invalidate even when player can view both networks");
        helper.assertTrue(menu.session().networkId().orElseThrow().equals(first.id()), "Existing session must retain its immutable network identity");
        var replacement = new DashboardMenu(2, owner.getInventory(), point);
        helper.assertTrue(replacement.stillValid(owner) && replacement.session().networkId().orElseThrow().equals(second.id()),
                "New session must capture changed association");
        manager.deleteNetwork(first.id());
        manager.deleteNetwork(second.id());
        helper.succeed();
    }

    private static AccessPointBlockEntity place(GameTestHelper helper, Block block, ServerPlayer owner) {
        var position = helper.absolutePos(POSITION);
        helper.getLevel().setBlock(position, block.defaultBlockState(), 3);
        block.setPlacedBy(helper.getLevel(), position, block.defaultBlockState(), owner, new ItemStack(block));
        return (AccessPointBlockEntity) helper.getLevel().getBlockEntity(position);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "NetworkSetupTest"), ClientInformation.createDefault());
        var position = helper.absolutePos(POSITION);
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        return player;
    }
}
