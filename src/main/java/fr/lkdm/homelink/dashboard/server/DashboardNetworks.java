package fr.lkdm.homelink.dashboard.server;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homecore.api.security.RateLimiter;
import fr.lkdm.homelink.dashboard.HomeLinkDashboard;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.blockentity.HomeServerBlockEntity;
import fr.lkdm.homelink.dashboard.network.AccessPointSession;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Authenticated setup of physical access points; HomeCore owns all created networks. */
@EventBusSubscriber(modid = HomeLinkDashboard.MOD_ID)
public final class DashboardNetworks {
    private static final Map<MinecraftServer, RateLimiter> LIMITERS = new HashMap<>();
    private DashboardNetworks() { }

    public static boolean canSetup(ServerPlayer player, AccessPointBlockEntity point) {
        return DashboardAccess.canView(player, point) && point.networkId().isEmpty()
                && point.owner().filter(player.getUUID()::equals).isPresent()
                && player.level().getBlockEntity(point.getBlockPos()) == point;
    }

    public static AccessPointSession describe(ServerPlayer player, AccessPointBlockEntity point, int requestedOffset) {
        if (!canSetup(player, point)) {
            return new AccessPointSession(point.getBlockPos(), point.networkId(), List.of(), 0, false, false);
        }
        var networks = DashboardAPI.networks(player.server).getNetworksForPlayer(player.getUUID()).stream()
                .filter(network -> DashboardAPI.hasPermission(player, network.id(), Permission.CONFIGURE)
                        && DashboardAPI.hasPermission(player, network.id(), Permission.VIEW))
                .toList();
        int offset = Math.max(0, Math.min(requestedOffset, Math.max(0, networks.size() - 1)));
        offset = offset / AccessPointSession.PAGE_SIZE * AccessPointSession.PAGE_SIZE;
        int end = Math.min(offset + AccessPointSession.PAGE_SIZE, networks.size());
        var choices = networks.subList(offset, end).stream()
                .map(network -> new AccessPointSession.NetworkChoice(network.id(), network.name())).toList();
        return new AccessPointSession(point.getBlockPos(), point.networkId(), choices, offset,
                end < networks.size(), point instanceof HomeServerBlockEntity);
    }

    public static ActionResult.Code create(ServerPlayer player, AccessPointBlockEntity point) {
        if (!acquire(player)) return ActionResult.Code.RATE_LIMITED;
        if (!canSetup(player, point) || !(point instanceof HomeServerBlockEntity)) return ActionResult.Code.DENIED;
        var network = DashboardAPI.networks(player.server).createNetwork("HomeLink · " + player.getGameProfile().getName(), player.getUUID());
        point.setNetworkId(network.id());
        return ActionResult.Code.SUCCESS;
    }

    public static ActionResult.Code bind(ServerPlayer player, AccessPointBlockEntity point, UUID network) {
        if (!acquire(player)) return ActionResult.Code.RATE_LIMITED;
        if (!canSetup(player, point) || network == null
                || !DashboardAPI.hasPermission(player, network, Permission.CONFIGURE)
                || !DashboardAPI.hasPermission(player, network, Permission.VIEW)) return ActionResult.Code.DENIED;
        point.setNetworkId(network);
        return ActionResult.Code.SUCCESS;
    }

    private static boolean acquire(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Network setup requires server thread");
        return LIMITERS.computeIfAbsent(player.server,
                ignored -> new RateLimiter(2, Duration.ofSeconds(1), 4096, System::nanoTime)).tryAcquire(player.getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LIMITERS.remove(event.getServer()); }
}
