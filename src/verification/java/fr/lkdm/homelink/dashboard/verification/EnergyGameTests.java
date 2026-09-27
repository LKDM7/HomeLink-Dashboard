package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.energy.EnergyRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.config.DashboardEnergyConfig;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.RadioNetworkService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dashboard blocks run on HomeLink Energy: an unpowered Home Server emits no signal. The other
 * checks run with free blocks ({@link #free()}); this batch restores the default costs.
 */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnergyGameTests {
    private static final String BATCH = "energy";
    private static final List<ModConfigSpec.IntValue> COSTS = List.of(DashboardEnergyConfig.SERVER_ENERGY,
            DashboardEnergyConfig.REPEATER_ENERGY, DashboardEnergyConfig.DISPLAY_ENERGY);

    private EnergyGameTests() {
    }

    /** Checks written before the blocks needed HE build networks without any power source. */
    static void free() {
        for (ModConfigSpec.IntValue cost : COSTS) cost.set(0);
    }

    @BeforeBatch(batch = BATCH)
    public static void restoreCosts(ServerLevel level) {
        for (ModConfigSpec.IntValue cost : COSTS) cost.set(cost.getDefault());
    }

    @AfterBatch(batch = BATCH)
    public static void freeAgain(ServerLevel level) {
        free();
    }

    private static AccessPointBlockEntity place(GameTestHelper helper, BlockPos relative, Block block, UUID network) {
        BlockPos position = helper.absolutePos(relative);
        helper.getLevel().setBlock(position, block.defaultBlockState(), 3);
        var point = (AccessPointBlockEntity) helper.getLevel().getBlockEntity(position);
        point.setNetworkId(network);
        return point;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void everyBlockTakesEnergy(GameTestHelper helper) {
        UUID network = DashboardAPI.networks(helper.getLevel().getServer()).createNetwork("Energy ports", UUID.randomUUID()).id();
        var blocks = List.of(DashboardRegistries.HOME_SERVER.get(), DashboardRegistries.SIGNAL_REPEATER.get(), DashboardRegistries.DASHBOARD_DISPLAY.get());
        for (int i = 0; i < blocks.size(); i++) {
            var point = place(helper, new BlockPos(1 + 2 * i, 1, 1), blocks.get(i), network);
            EnergyPort port = helper.getLevel().getCapability(EnergyApi.BLOCK, point.getBlockPos(), Direction.NORTH);
            helper.assertTrue(port != null && port.role() == EnergyRole.CONSUMER && !port.type().canSend(), "No HE input on " + blocks.get(i));
            helper.assertTrue(!point.powered() && !point.working(), blocks.get(i) + " works without energy");
        }
        DashboardAPI.networks(helper.getLevel().getServer()).deleteNetwork(network);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 200)
    public static void unpoweredServerEmitsNoSignal(GameTestHelper helper) {
        UUID network = DashboardAPI.networks(helper.getLevel().getServer()).createNetwork("Energy signal", UUID.randomUUID()).id();
        var server = place(helper, new BlockPos(1, 1, 1), DashboardRegistries.HOME_SERVER.get(), network);
        var display = place(helper, new BlockPos(5, 1, 1), DashboardRegistries.DASHBOARD_DISPLAY.get(), network);
        helper.startSequence()
                .thenExecuteAfter(2, () -> helper.assertTrue(!RadioNetworkService.canReceive(display, network), "Signal without energy"))
                .thenExecute(() -> {
                    server.energyPort().insert(Long.MAX_VALUE, false);
                    server.drawEnergy();
                })
                .thenWaitUntil(() -> helper.assertTrue(server.working() && RadioNetworkService.canReceive(display, network), "No signal once powered"))
                .thenExecute(() -> DashboardAPI.networks(helper.getLevel().getServer()).deleteNetwork(network))
                .thenSucceed();
    }
}
