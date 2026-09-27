package fr.lkdm.homelink.dashboard.server;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.dashboard.block.AccessPointStatus;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** All entry points and ongoing access checks use the authenticated server player. */
public final class DashboardAccess {
    private DashboardAccess() { }

    public static boolean canView(ServerPlayer player, AccessPointBlockEntity point) {
        if (point.isRemoved() || player.level() != point.getLevel() || !point.working()
                || !point.getBlockPos().closerToCenterThan(player.position(), 8.0)) return false;
        return point.networkId().map(id -> RadioNetworkService.hasSignal(point)
                        && DashboardAPI.hasPermission(player, id, Permission.VIEW))
                .orElseGet(() -> point.owner().map(player.getUUID()::equals).orElse(false));
    }

    public static void open(ServerPlayer player, AccessPointBlockEntity point) {
        open(player, point, 0);
    }

    public static void open(ServerPlayer player, AccessPointBlockEntity point, int directoryOffset) {
        refreshStatus(point);
        if (!canView(player, point)) {
            if (point.active() && !point.powered()) {
                player.displayClientMessage(Component.translatable("message.homelink_dashboard.no_power"), true);
                return;
            }
            player.displayClientMessage(Component.translatable(point.networkId().isPresent()
                    && DashboardAPI.hasPermission(player, point.networkId().orElseThrow(), Permission.VIEW)
                    && !RadioNetworkService.hasSignal(point) ? "message.homelink_dashboard.no_signal"
                    : "message.homelink_dashboard.access_denied"), true);
            return;
        }
        var session = DashboardNetworks.describe(player, point, directoryOffset);
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new DashboardMenu(id, inventory, point, session),
                Component.translatable("screen.homelink_dashboard.title")), session::write);
    }

    public static void refreshStatus(AccessPointBlockEntity point) {
        var status = !point.working() || point.networkId().isEmpty() ? AccessPointStatus.OFFLINE
                : DashboardAPI.networks(point.getLevel().getServer()).getNetwork(point.networkId().orElseThrow()).isPresent()
                    ? (RadioNetworkService.hasSignal(point) ? AccessPointStatus.ONLINE : AccessPointStatus.OFFLINE) : AccessPointStatus.ERROR;
        point.setStatus(status);
    }
}
