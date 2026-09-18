package fr.lkdm.homelink.dashboard.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.block.AccessPointBlock;
import fr.lkdm.homelink.dashboard.block.AccessPointStatus;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardAccess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void serverPlacementAndPersistence(GameTestHelper helper) {
        verifyPersistence(helper, DashboardRegistries.HOME_SERVER.get());
    }

    @GameTest(template = "empty")
    public static void displayPlacementAndPersistence(GameTestHelper helper) {
        verifyPersistence(helper, DashboardRegistries.DASHBOARD_DISPLAY.get());
    }

    private static void verifyPersistence(GameTestHelper helper, Block block) {
        var player = player(helper);
        var point = place(helper, block, player);
        helper.assertTrue(point.owner().orElseThrow().equals(player.getUUID()), "Placement must persist the authenticated owner");
        var network = DashboardAPI.networks(helper.getLevel().getServer()).createNetwork("Persistence fixture", player.getUUID());
        point.setNetworkId(network.id());
        helper.assertTrue(point.getBlockState().getValue(AccessPointBlock.STATUS) == AccessPointStatus.ONLINE, "Existing network must be online");
        point.setActive(false);
        helper.assertTrue(point.getBlockState().getValue(AccessPointBlock.STATUS) == AccessPointStatus.OFFLINE, "Inactive point must be offline");
        var saved = point.saveWithFullMetadata(helper.getLevel().registryAccess());
        // Replace the entity, then load the actual Minecraft NBT serialization.
        var absolute = helper.absolutePos(POSITION);
        helper.getLevel().removeBlock(absolute, false);
        helper.getLevel().setBlock(absolute, block.defaultBlockState(), 3);
        var restored = (AccessPointBlockEntity) helper.getLevel().getBlockEntity(absolute);
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.owner().equals(point.owner()), "Owner lost during NBT round trip");
        helper.assertTrue(restored.networkId().equals(point.networkId()), "Network association lost during NBT round trip");
        helper.assertTrue(!restored.active(), "Active flag lost during NBT round trip");
        restored.loadWithComponents(new CompoundTag(), helper.getLevel().registryAccess());
        helper.assertTrue(restored.owner().isEmpty() && restored.networkId().isEmpty() && restored.active(), "Missing NBT must reset to safe defaults");
        DashboardAPI.networks(helper.getLevel().getServer()).deleteNetwork(network.id());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void permissionsAndRevocation(GameTestHelper helper) {
        var owner = player(helper);
        var point = place(helper, DashboardRegistries.HOME_SERVER.get(), owner);
        var viewer = player(helper);
        helper.assertTrue(DashboardAccess.canView(owner, point), "Owner must open unbound shell");
        helper.assertTrue(!DashboardAccess.canView(viewer, point), "Stranger must not open unbound shell");
        var manager = DashboardAPI.networks(helper.getLevel().getServer());
        var network = manager.createNetwork("Access fixture", owner.getUUID());
        point.setNetworkId(network.id());
        helper.assertTrue(!DashboardAccess.canView(viewer, point), "Network stranger must be denied");
        manager.setMember(network.id(), viewer.getUUID(), NetworkRole.VIEWER);
        var menu = new DashboardMenu(1, viewer.getInventory(), point);
        helper.assertTrue(menu.stillValid(viewer), "VIEWER must be able to consult");
        manager.removeMember(network.id(), viewer.getUUID());
        helper.assertTrue(!menu.stillValid(viewer), "Revoked membership must invalidate the existing session");
        manager.deleteNetwork(network.id());
        DashboardAccess.refreshStatus(point);
        helper.assertTrue(!DashboardAccess.canView(owner, point), "Block owner must not bypass missing HomeCore network");
        helper.assertTrue(point.getBlockState().getValue(AccessPointBlock.STATUS) == AccessPointStatus.ERROR, "Deleted network must show error");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sessionLifetime(GameTestHelper helper) {
        var owner = player(helper);
        var point = place(helper, DashboardRegistries.DASHBOARD_DISPLAY.get(), owner);
        var menu = new DashboardMenu(1, owner.getInventory(), point);
        helper.assertTrue(menu.stillValid(owner), "Nearby owner must have a valid menu");
        owner.setPos(owner.getX() + 12, owner.getY(), owner.getZ());
        helper.assertTrue(!menu.stillValid(owner), "Walking away must invalidate session");
        owner.setPos(point.getBlockPos().getX() + 0.5, point.getBlockPos().getY(), point.getBlockPos().getZ() + 0.5);
        point.setActive(false);
        helper.assertTrue(!menu.stillValid(owner), "Deactivated point must invalidate session");
        point.setActive(true);
        helper.getLevel().removeBlock(point.getBlockPos(), false);
        helper.assertTrue(!menu.stillValid(owner), "Removed block must invalidate session");
        helper.assertTrue(menu.quickMoveStack(owner, 999).isEmpty(), "No inventory transfer is permitted");
        helper.succeed();
    }

    private static AccessPointBlockEntity place(GameTestHelper helper, Block block, ServerPlayer player) {
        var pos = helper.absolutePos(POSITION);
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        block.setPlacedBy(helper.getLevel(), pos, block.defaultBlockState(), player, new ItemStack(block));
        return (AccessPointBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "FoundationTest"), ClientInformation.createDefault());
        var pos = helper.absolutePos(POSITION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }
}
