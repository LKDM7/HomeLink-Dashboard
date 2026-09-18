package fr.lkdm.homelink.dashboard.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homelink.dashboard.network.PreferencesPayloads;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardPreferencesSavedData;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PreferenceGameTests {
    @GameTest(template = "empty")
    public static void personalPersistenceAndGridValidation(GameTestHelper helper) {
        UUID player = UUID.randomUUID(), network = UUID.randomUUID(), device = UUID.randomUUID();
        DashboardWidget summary = widget(device, 0, 0);
        DashboardWidget metric = new DashboardWidget(UUID.randomUUID(), DashboardWidget.Type.METRIC, device, "fixture:count", 6, 0, 6, 3);
        DashboardProfile profile = new DashboardProfile(List.of(summary, metric), Set.of(device));
        var data = new DashboardPreferencesSavedData();
        data.put(player, network, profile);
        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        saved.getList("profiles", Tag.TAG_COMPOUND).add(new CompoundTag());
        var restored = DashboardPreferencesSavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored.profile(player, network).equals(profile), "Valid player/network profile must survive NBT round trip despite a corrupt unrelated entry");
        helper.assertTrue(restored.profile(UUID.randomUUID(), network).equals(DashboardProfile.EMPTY), "Profiles must never be shared between players");
        helper.assertTrue(restored.profile(player, UUID.randomUUID()).equals(DashboardProfile.EMPTY), "Profiles must never be shared between networks");
        rejects(helper, () -> new DashboardProfile(List.of(summary, widget(device, 1, 1)), Set.of()), "Overlapping widgets must be rejected");
        rejects(helper, () -> widget(device, 7, 0), "Out-of-bounds widgets must be rejected");
        rejects(helper, () -> new DashboardWidget(UUID.randomUUID(), DashboardWidget.Type.METRIC, device, "not a resource", 0, 0, 6, 3),
                "Invalid metric identifiers must be rejected");
        var future = saved.copy();
        future.putInt("formatVersion", 99);
        rejects(helper, () -> DashboardPreferencesSavedData.load(future, helper.getLevel().registryAccess()), "Unknown persistence versions must not be silently replaced");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preferenceCodecBounds(GameTestHelper helper) {
        UUID device = UUID.randomUUID();
        DashboardProfile profile = new DashboardProfile(List.of(widget(device, 0, 0)), Set.of(device));
        var request = new PreferencesPayloads.Request(UUID.randomUUID(), 5, UUID.randomUUID(), PreferencesPayloads.Operation.SAVE_LAYOUT, profile.toTag());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            PreferencesPayloads.Request.STREAM_CODEC.encode(buffer, request);
            var decoded = PreferencesPayloads.Request.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.equals(request), "Preference request must round-trip through the bounded wire codec");
            var response = new PreferencesPayloads.Response(request.requestId(), request.containerId(), request.networkId(), ActionResult.Code.SUCCESS, profile);
            PreferencesPayloads.Response.STREAM_CODEC.encode(buffer, response);
            helper.assertTrue(PreferencesPayloads.Response.STREAM_CODEC.decode(buffer).equals(response), "Preference snapshot must round-trip through the bounded wire codec");
        } finally { buffer.release(); }
        CompoundTag oversized = new CompoundTag();
        oversized.putString("oversized", "x".repeat(PreferencesPayloads.MAX_BYTES));
        rejects(helper, () -> new PreferencesPayloads.Request(UUID.randomUUID(), 1, UUID.randomUUID(), PreferencesPayloads.Operation.LOAD, oversized),
                "Oversized preference payloads must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void authoritativePreferencePermissions(GameTestHelper helper) {
        var owner = player(helper);
        var viewer = player(helper);
        var server = helper.getLevel().getServer();
        var manager = DashboardAPI.networks(server);
        var network = manager.createNetwork("Preference permissions", owner.getUUID());
        UUID device = UUID.randomUUID();
        manager.addDevice(network.id(), device);
        manager.setMember(network.id(), viewer.getUUID(), NetworkRole.VIEWER);
        var point = point(helper, owner, network.id());
        owner.containerMenu = new DashboardMenu(3, owner.getInventory(), point);
        viewer.containerMenu = new DashboardMenu(4, viewer.getInventory(), point);
        var layout = new DashboardProfile(List.of(widget(device, 0, 0)), Set.of());
        try {
            var saved = PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT, layout.toTag()));
            helper.assertTrue(saved.result() == ActionResult.Code.SUCCESS && saved.profile().widgets().size() == 1, "Owner with CONFIGURE must save layout");
            var denied = PreferencesPayloads.handle(viewer, request(viewer, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT, layout.toTag()));
            helper.assertTrue(denied.result() == ActionResult.Code.DENIED, "Forged viewer layout packet must be denied server-side");
            CompoundTag favorite = new CompoundTag(); favorite.putUUID("device", device);
            var favored = PreferencesPayloads.handle(viewer, request(viewer, network.id(), PreferencesPayloads.Operation.TOGGLE_FAVORITE, favorite));
            helper.assertTrue(favored.result() == ActionResult.Code.SUCCESS && favored.profile().favorites().contains(device)
                    && favored.profile().widgets().isEmpty(), "Viewer can keep personal favorites without receiving owner's layout");
            var unrelated = PreferencesPayloads.handle(owner, request(owner, UUID.randomUUID(), PreferencesPayloads.Operation.LOAD, new CompoundTag()));
            helper.assertTrue(unrelated.result() == ActionResult.Code.DENIED && unrelated.profile().equals(DashboardProfile.EMPTY),
                    "Network mismatch must not leak stored preferences");
            var wrongContainer = new PreferencesPayloads.Request(UUID.randomUUID(), 99, network.id(), PreferencesPayloads.Operation.LOAD, new CompoundTag());
            helper.assertTrue(PreferencesPayloads.handle(owner, wrongContainer).result() == ActionResult.Code.DENIED, "Container mismatch must be denied");
            UUID alien = UUID.randomUUID();
            var foreignLayout = new DashboardProfile(List.of(widget(alien, 0, 0)), Set.of());
            helper.assertTrue(PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT, foreignLayout.toTag())).result()
                    == ActionResult.Code.INVALID_PARAMETER, "Foreign devices must not be accepted in a personal widget");
            manager.removeMember(network.id(), viewer.getUUID());
            helper.assertTrue(PreferencesPayloads.handle(viewer, request(viewer, network.id(), PreferencesPayloads.Operation.LOAD, new CompoundTag())).result()
                    == ActionResult.Code.DENIED, "Membership revocation must invalidate preference access");
        } finally {
            var storage = DashboardPreferencesSavedData.get(server);
            storage.put(owner.getUUID(), network.id(), DashboardProfile.EMPTY);
            storage.put(viewer.getUUID(), network.id(), DashboardProfile.EMPTY);
            manager.deleteNetwork(network.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void preferenceRequestRateLimit(GameTestHelper helper) {
        var owner = player(helper);
        var manager = DashboardAPI.networks(helper.getLevel().getServer());
        var network = manager.createNetwork("Preference rate fixture", owner.getUUID());
        owner.containerMenu = new DashboardMenu(7, owner.getInventory(), point(helper, owner, network.id()));
        try {
            for (int index = 0; index < 4; index++) helper.assertTrue(PreferencesPayloads.handle(owner,
                    request(owner, network.id(), PreferencesPayloads.Operation.LOAD, new CompoundTag())).result() == ActionResult.Code.SUCCESS,
                    "First four bounded preference requests should succeed");
            helper.assertTrue(PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.LOAD, new CompoundTag())).result()
                    == ActionResult.Code.RATE_LIMITED, "Preference request burst must be rate limited server-side");
        } finally { manager.deleteNetwork(network.id()); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedDevicesDoNotLockLayout(GameTestHelper helper) {
        var owner = player(helper);
        var server = helper.getLevel().getServer();
        var manager = DashboardAPI.networks(server);
        var network = manager.createNetwork("Stale widget fixture", owner.getUUID());
        var first = widget(UUID.randomUUID(), 0, 0);
        var second = widget(UUID.randomUUID(), 6, 0);
        manager.addDevice(network.id(), first.deviceId());
        manager.addDevice(network.id(), second.deviceId());
        owner.containerMenu = new DashboardMenu(9, owner.getInventory(), point(helper, owner, network.id()));
        try {
            var profile = new DashboardProfile(List.of(first, second), Set.of());
            helper.assertTrue(PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT,
                    profile.toTag())).result() == ActionResult.Code.SUCCESS, "Initial authorized references must save");
            manager.removeDevice(network.id(), first.deviceId());
            manager.removeDevice(network.id(), second.deviceId());
            var moved = new DashboardWidget(first.id(), first.type(), first.deviceId(), "", 0, 3, 6, 3);
            helper.assertTrue(PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT,
                    new DashboardProfile(List.of(moved, second), Set.of()).toTag())).result() == ActionResult.Code.SUCCESS,
                    "Moving a previously authorized unavailable reference must not lock the layout");
            var forged = new DashboardWidget(first.id(), first.type(), UUID.randomUUID(), "", 0, 3, 6, 3);
            helper.assertTrue(PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT,
                    new DashboardProfile(List.of(forged, second), Set.of()).toTag())).result() == ActionResult.Code.INVALID_PARAMETER,
                    "Reusing a widget id must not authorize a different device reference");
            helper.assertTrue(PreferencesPayloads.handle(owner, request(owner, network.id(), PreferencesPayloads.Operation.SAVE_LAYOUT,
                    new DashboardProfile(List.of(second), Set.of()).toTag())).result() == ActionResult.Code.SUCCESS,
                    "One stale widget can be removed while retaining another");
        } finally {
            DashboardPreferencesSavedData.get(server).put(owner.getUUID(), network.id(), DashboardProfile.EMPTY);
            manager.deleteNetwork(network.id());
        }
        helper.succeed();
    }

    private static DashboardWidget widget(UUID device, int x, int y) {
        return new DashboardWidget(UUID.randomUUID(), DashboardWidget.Type.DEVICE_SUMMARY, device, "", x, y, 6, 3);
    }
    private static PreferencesPayloads.Request request(ServerPlayer player, UUID network, PreferencesPayloads.Operation operation, CompoundTag data) {
        return new PreferencesPayloads.Request(UUID.randomUUID(), player.containerMenu.containerId, network, operation, data);
    }
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "PreferenceTest"), ClientInformation.createDefault());
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
    private static void rejects(GameTestHelper helper, Runnable operation, String message) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException exception) { rejected = true; }
        helper.assertTrue(rejected, message);
    }
}
