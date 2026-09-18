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
                .map(network -> new AccessPointSession.NetworkChoice(network.id(), network.name(), RadioNetworkService.canReceive(point, network.id())))
                .sorted(java.util.Comparator.comparing(AccessPointSession.NetworkChoice::inRange).reversed()
                        .thenComparing(AccessPointSession.NetworkChoice::name).thenComparing(AccessPointSession.NetworkChoice::id))
                .toList();
        int offset = Math.max(0, Math.min(requestedOffset, Math.max(0, networks.size() - 1)));
        offset = offset / AccessPointSession.PAGE_SIZE * AccessPointSession.PAGE_SIZE;
        int end = Math.min(offset + AccessPointSession.PAGE_SIZE, networks.size());
        var choices = List.copyOf(networks.subList(offset, end));
        return new AccessPointSession(point.getBlockPos(), point.networkId(), choices, offset,
                end < networks.size(), point instanceof HomeServerBlockEntity);
    }

    public static ActionResult.Code create(ServerPlayer player, AccessPointBlockEntity point) {
        return create(player, point, "HomeLink · " + player.getGameProfile().getName());
    }
    public static ActionResult.Code create(ServerPlayer player, AccessPointBlockEntity point, String name) {
        if (!acquire(player)) return ActionResult.Code.RATE_LIMITED;
        if (!canSetup(player, point) || !(point instanceof HomeServerBlockEntity)) return ActionResult.Code.DENIED;
        if (!validName(name)) return ActionResult.Code.INVALID_PARAMETER;
        var network = DashboardAPI.networks(player.server).createNetwork(name.strip(), player.getUUID());
        point.setNetworkId(network.id());
        return ActionResult.Code.SUCCESS;
    }

    public static boolean validName(String name) {
        return fr.lkdm.homelink.dashboard.network.NetworkNames.isValid(name);
    }
    public static ActionResult.Code rename(ServerPlayer player, AccessPointBlockEntity point, String name) {
        if (!acquire(player)) return ActionResult.Code.RATE_LIMITED;
        if (!DashboardAccess.canView(player, point) || point.networkId().isEmpty()
                || !DashboardAPI.hasPermission(player, point.networkId().orElseThrow(), Permission.MANAGE_NETWORK))
            return ActionResult.Code.DENIED;
        if (!validName(name)) return ActionResult.Code.INVALID_PARAMETER;
        DashboardAPI.networks(player.server).renameNetwork(point.networkId().orElseThrow(), name.strip());
        return ActionResult.Code.SUCCESS;
    }

    public static ActionResult.Code bind(ServerPlayer player, AccessPointBlockEntity point, UUID network) {
        if (!acquire(player)) return ActionResult.Code.RATE_LIMITED;
        if (!canSetup(player, point) || network == null
                || !DashboardAPI.hasPermission(player, network, Permission.CONFIGURE)
                || !DashboardAPI.hasPermission(player, network, Permission.VIEW)) return ActionResult.Code.DENIED;
        if (!(point instanceof HomeServerBlockEntity) && !RadioNetworkService.canReceive(point, network))
            return ActionResult.Code.DEVICE_OFFLINE;
        point.setNetworkId(network);
        return ActionResult.Code.SUCCESS;
    }

    /** Explicit owner gesture; permits repair even when the old network has no radio signal. */
    public static boolean resetBinding(ServerPlayer player, AccessPointBlockEntity point) {
        if (point.isRemoved() || !point.active() || player.level() != point.getLevel()
                || player.level().getBlockEntity(point.getBlockPos()) != point
                || !point.getBlockPos().closerToCenterThan(player.position(), 8.0)
                || point.owner().filter(player.getUUID()::equals).isEmpty()) return false;
        if (point.networkId().isPresent()) {
            UUID network = point.networkId().orElseThrow();
            if (DashboardAPI.networks(player.server).getNetwork(network).isPresent()
                    && !DashboardAPI.hasPermission(player, network, Permission.CONFIGURE)) return false;
        }
        point.setNetworkId(null);
        return true;
    }

    private static boolean acquire(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Network setup requires server thread");
        return LIMITERS.computeIfAbsent(player.server,
                ignored -> new RateLimiter(2, Duration.ofSeconds(1), 4096, System::nanoTime)).tryAcquire(player.getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LIMITERS.remove(event.getServer()); }
}
