package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homelink.dashboard.block.AccessPointBlock;
import fr.lkdm.homelink.dashboard.block.AccessPointStatus;
import fr.lkdm.homelink.dashboard.block.HomeServerBlock;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TallServerGameTests {
    @GameTest(template = "empty")
    public static void itemPlacementAndStateSync(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        var block = DashboardRegistries.HOME_SERVER.get();
        var player = player(h, pos);
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        var stack = new ItemStack(block, 2);
        var context = context(h, player, pos, stack);
        h.assertTrue(block.asItem().useOn(context).consumesAction(), "Real item placement must succeed");
        h.assertTrue(stack.getCount() == 1, "Two halves consume only one item");
        h.assertTrue(h.getLevel().getBlockState(pos.above()).getValue(HomeServerBlock.HALF) == DoubleBlockHalf.UPPER, "Upper half missing");
        h.assertTrue(h.getLevel().getBlockEntity(pos.above()) == null, "Upper half must not duplicate persistent data");
        var point = (AccessPointBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(point.owner().orElseThrow().equals(player.getUUID()), "Owner missing");
        point.setStatus(AccessPointStatus.ERROR);
        h.assertTrue(h.getLevel().getBlockState(pos.above()).getValue(AccessPointBlock.STATUS) == AccessPointStatus.ERROR, "Upper status must follow lower");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedHeadroomAndHeightLimit(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        var player = player(h, pos);
        var block = DashboardRegistries.HOME_SERVER.get();
        var stack = new ItemStack(block);
        h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        h.getLevel().setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 3);
        h.assertTrue(block.getStateForPlacement(context(h, player, pos, stack)) == null, "Occupied headroom must reject placement");
        h.assertTrue(h.getLevel().getBlockState(pos).isAir() && stack.getCount() == 1, "Rejected placement must not mutate world/item");
        var ceiling = new BlockPos(pos.getX(), h.getLevel().getMaxBuildHeight() - 1, pos.getZ());
        h.assertTrue(block.getStateForPlacement(context(h, player, ceiling, stack)) == null, "Cannot place beyond build height");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void breakEitherHalfDropsOneServer(GameTestHelper h) {
        for (int index = 0; index < 2; index++) {
            var pos = h.absolutePos(new BlockPos(1 + index * 3, 1, 1));
            place(h, pos);
            h.getLevel().destroyBlock(index == 0 ? pos : pos.above(), true);
            h.assertTrue(h.getLevel().getBlockState(pos).isAir() && h.getLevel().getBlockState(pos.above()).isAir(), "Both halves must disappear");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
            h.assertTrue(drops.stream().mapToInt(e -> e.getItem().is(DashboardRegistries.HOME_SERVER.get().asItem()) ? e.getItem().getCount() : 0).sum() == 1,
                    "Breaking either half must drop exactly one server");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeUpperBreakDoesNotDrop(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 1, 1));
        place(h, pos);
        var player = h.makeMockPlayer(GameType.CREATIVE);
        var block = DashboardRegistries.HOME_SERVER.get();
        block.playerWillDestroy(h.getLevel(), pos.above(), h.getLevel().getBlockState(pos.above()), player);
        h.getLevel().removeBlock(pos.above(), false);
        h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "Creative break must remove lower");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(), "Creative break must not duplicate items");
        h.succeed();
    }

    private static void place(GameTestHelper h, BlockPos pos) {
        var block = DashboardRegistries.HOME_SERVER.get();
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        block.setPlacedBy(h.getLevel(), pos, block.defaultBlockState(), null, new ItemStack(block));
    }
    private static Player player(GameTestHelper h, BlockPos pos) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }
    private static BlockPlaceContext context(GameTestHelper h, Player player, BlockPos pos, ItemStack stack) {
        return new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
}
