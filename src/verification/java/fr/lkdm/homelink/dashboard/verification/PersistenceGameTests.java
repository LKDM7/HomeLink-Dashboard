package fr.lkdm.homelink.dashboard.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Write and read run in two independent JVMs sharing one dedicated verification world. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceGameTests {
    private static final String NAME = "homelink-dashboard-restart-verification";
    private static final UUID OWNER = UUID.fromString("cf64c430-7998-4fc1-bfa2-d8e6d7b862da");
    private static final UUID DEVICE = UUID.fromString("7e2c1416-7b87-4df3-b7e8-1c53e4c0cf9c");
    private static final UUID WIDGET = UUID.fromString("a8da6a1c-5b6b-447a-8f38-1153da50c461");
    private static final BlockPos SERVER = new BlockPos(10_000, 80, 10_000);
    private static final BlockPos DISPLAY = SERVER.east();

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void networkBlocksAndLayoutSurviveRestart(GameTestHelper helper) {
        String pass = System.getProperty("homelink.persistencePass", "");
        if (pass.isEmpty()) { helper.succeed(); return; }
        var server = helper.getLevel().getServer();
        var level = server.overworld();
        var manager = DashboardAPI.networks(server);
        var saved = DashboardPreferencesSavedData.get(server);
        var expected = new DashboardProfile(List.of(new DashboardWidget(WIDGET, DashboardWidget.Type.METRIC,
                DEVICE, "unknown_integration:capacity", 3, 4, 6, 3)), Set.of(DEVICE));
        if (pass.equals("write")) {
            manager.getAll().stream().filter(network -> network.name().equals(NAME)).map(HomeNetwork::id).toList().forEach(manager::deleteNetwork);
            var home = manager.createNetwork(NAME, OWNER);
            manager.addDevice(home.id(), DEVICE);
            saved.put(OWNER, home.id(), expected);
            place(level, SERVER, DashboardRegistries.HOME_SERVER.get(), home.id(), true);
            place(level, DISPLAY, DashboardRegistries.DASHBOARD_DISPLAY.get(), home.id(), false);
            server.saveEverything(false, true, true);
            LogUtils.getLogger().info("HOMELINK_PERSISTENCE_WRITE_OK network={} blocks=2 widgets=1 favorites=1", home.id());
        } else if (pass.equals("read")) {
            var homes = manager.getAll().stream().filter(network -> network.name().equals(NAME)).toList();
            helper.assertTrue(homes.size() == 1, "Missing network written by previous server process");
            var home = homes.getFirst();
            helper.assertTrue(home.owner().equals(OWNER) && home.devices().contains(DEVICE), "HomeCore persistent owner/device identity changed");
            helper.assertTrue(saved.profile(OWNER, home.id()).equals(expected), "Layout geometry, widget identity, metric or favorite lost across process restart");
            checkPoint(helper, level, SERVER, DashboardRegistries.HOME_SERVER.get(), home.id(), true);
            checkPoint(helper, level, DISPLAY, DashboardRegistries.DASHBOARD_DISPLAY.get(), home.id(), false);
            LogUtils.getLogger().info("HOMELINK_PERSISTENCE_READ_OK network={} blocks=2 widgets=1 favorites=1", home.id());
        } else helper.fail("homelink.persistencePass must be write or read");
        helper.succeed();
    }

    private static void place(ServerLevel level, BlockPos location, Block block, UUID network, boolean active) {
        level.getChunkAt(location);
        level.setBlock(location, block.defaultBlockState(), 3);
        var point = (AccessPointBlockEntity) level.getBlockEntity(location);
        if (point == null) throw new IllegalStateException("Persistent access point missing");
        point.initializeOwner(OWNER); point.setNetworkId(network); point.setActive(active);
    }

    private static void checkPoint(GameTestHelper helper, ServerLevel level, BlockPos location, Block block, UUID network, boolean active) {
        level.getChunkAt(location);
        helper.assertTrue(level.getBlockState(location).is(block), "Persistent physical block missing after restart");
        var point = (AccessPointBlockEntity) level.getBlockEntity(location);
        helper.assertTrue(point != null && point.owner().filter(OWNER::equals).isPresent()
                && point.networkId().filter(network::equals).isPresent() && point.active() == active,
                "Access point association, owner or active state lost after restart");
    }
}
