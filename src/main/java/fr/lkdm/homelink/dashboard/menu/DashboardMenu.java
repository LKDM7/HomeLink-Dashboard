package fr.lkdm.homelink.dashboard.menu;

import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.server.DashboardAccess;
import fr.lkdm.homelink.dashboard.server.DashboardNetworks;
import fr.lkdm.homelink.dashboard.network.AccessPointSession;
import fr.lkdm.homecore.api.action.ActionResult;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** A server-validated session, with no inventory slots or client-owned authority. */
public final class DashboardMenu extends AbstractContainerMenu {
    private final AccessPointBlockEntity accessPoint;
    private final BlockPos position;
    private final AccessPointSession session;
    private long lastDirectoryRequest = Long.MIN_VALUE;
    public static final int CREATE_NETWORK = 0;
    public static final int PREVIOUS_NETWORKS = 1;
    public static final int NEXT_NETWORKS = 2;
    public static final int BIND_NETWORK_BASE = 100;

    public DashboardMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        super(DashboardRegistries.DASHBOARD_MENU.get(), id);
        accessPoint = null;
        session = AccessPointSession.read(buffer);
        position = session.position();
    }

    public DashboardMenu(int id, Inventory inventory, AccessPointBlockEntity point) {
        this(id, inventory, point, DashboardNetworks.describe((ServerPlayer) inventory.player, point, 0));
    }

    public DashboardMenu(int id, Inventory inventory, AccessPointBlockEntity point, AccessPointSession session) {
        super(DashboardRegistries.DASHBOARD_MENU.get(), id);
        accessPoint = point;
        position = point.getBlockPos().immutable();
        this.session = session;
    }

    public BlockPos position() { return position; }
    public AccessPointSession session() { return session; }

    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide) return true;
        return player instanceof ServerPlayer serverPlayer && accessPoint != null
                && player.level().getBlockEntity(position) == accessPoint
                && accessPoint.networkId().equals(session.networkId())
                && DashboardAccess.canView(serverPlayer, accessPoint);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.containerMenu != this
                || !stillValid(player) || !DashboardNetworks.canSetup(serverPlayer, accessPoint)) return false;
        if (button == PREVIOUS_NETWORKS || button == NEXT_NETWORKS) {
            long now = player.level().getGameTime();
            if (lastDirectoryRequest != Long.MIN_VALUE && now - lastDirectoryRequest < 10) return false;
            if (button == PREVIOUS_NETWORKS && session.directoryOffset() == 0
                    || button == NEXT_NETWORKS && !session.hasNext()) return false;
            lastDirectoryRequest = now;
            DashboardAccess.open(serverPlayer, accessPoint, session.directoryOffset()
                    + (button == NEXT_NETWORKS ? AccessPointSession.PAGE_SIZE : -AccessPointSession.PAGE_SIZE));
            return true;
        }
        ActionResult.Code result;
        if (button == CREATE_NETWORK && session.canCreate()) {
            result = DashboardNetworks.create(serverPlayer, accessPoint);
        } else {
            int index = button - BIND_NETWORK_BASE;
            if (index < 0 || index >= session.choices().size()) return false;
            result = DashboardNetworks.bind(serverPlayer, accessPoint, session.choices().get(index).id());
        }
        if (result == ActionResult.Code.SUCCESS) DashboardAccess.open(serverPlayer, accessPoint);
        else player.displayClientMessage(Component.translatable("message.homelink_dashboard.setup_result", Component.translatableWithFallback("value.homelink_dashboard." + result.name().toLowerCase(java.util.Locale.ROOT), result.name())), false);
        return result == ActionResult.Code.SUCCESS;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
