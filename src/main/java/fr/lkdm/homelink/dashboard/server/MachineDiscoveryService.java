package fr.lkdm.homelink.dashboard.server;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.network.MachineListing;
import fr.lkdm.homelink.dashboard.network.MachineListing.Entry;
import fr.lkdm.homelink.dashboard.network.MachineListing.State;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lists the HomeCore machines inside the radio area of an access point's network (its server and relays) and adds
 * them to that network. Only loaded devices already registered with HomeCore are considered: no world scan.
 * Callers pass an access point whose menu session they have already validated.
 */
public final class MachineDiscoveryService {
    private MachineDiscoveryService() { }

    public static MachineListing describe(ServerPlayer player, AccessPointBlockEntity point) {
        var network = authorize(player, point);
        if (network.code() != ActionResult.Code.SUCCESS) return MachineListing.of(network.code());
        var entries = new ArrayList<Entry>();
        for (var device : candidates(player, point, network.id())) entries.add(entry(player, point, network.id(), device));
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.canAdd() ? 0 : 1)
                .thenComparing(Entry::state).thenComparingInt(Entry::distance)
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id));
        return new MachineListing(ActionResult.Code.SUCCESS, entries.subList(0, Math.min(MachineListing.MAX_ENTRIES, entries.size())), entries.size());
    }

    /**
     * Adds one machine, or every addable machine in range when {@code device} is empty.
     * @return SUCCESS when at least one machine joined (or was already present), otherwise the reason
     */
    public static ActionResult.Code add(ServerPlayer player, AccessPointBlockEntity point, Optional<UUID> device) {
        var network = authorize(player, point);
        if (network.code() != ActionResult.Code.SUCCESS) return network.code();
        var targets = candidates(player, point, network.id()).stream()
                .filter(candidate -> device.map(candidate.id()::equals).orElse(true)).toList();
        if (targets.isEmpty()) return ActionResult.Code.DEVICE_OFFLINE;
        ActionResult.Code result = ActionResult.Code.DENIED;
        for (var target : targets) {
            if (device.isEmpty() && !entry(player, point, network.id(), target).canAdd()) continue;
            var bound = DashboardAPI.bindDevice(player, target, Optional.of(network.id()));
            if (bound == NetworkMember.BindResult.BOUND || bound == NetworkMember.BindResult.UNCHANGED) result = ActionResult.Code.SUCCESS;
            else if (device.isPresent()) result = switch (bound) {
                case UNKNOWN_NETWORK -> ActionResult.Code.FAILED;
                case NOT_SUPPORTED -> ActionResult.Code.INVALID_PARAMETER;
                default -> ActionResult.Code.DENIED;
            };
        }
        return result;
    }

    private record Authorization(ActionResult.Code code, UUID id) { }

    /** Only players who may change the network's membership see nearby machines, which include other players' blocks. */
    private static Authorization authorize(ServerPlayer player, AccessPointBlockEntity point) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Machine discovery requires the server thread");
        if (point.networkId().isEmpty() || !DashboardAccess.canView(player, point)) return new Authorization(ActionResult.Code.DENIED, null);
        UUID network = point.networkId().orElseThrow();
        if (!DashboardAPI.hasPermission(player, network, Permission.MANAGE_NETWORK)) return new Authorization(ActionResult.Code.DENIED, null);
        if (!RadioNetworkService.hasSignal(point)) return new Authorization(ActionResult.Code.DEVICE_OFFLINE, null);
        return new Authorization(ActionResult.Code.SUCCESS, network);
    }

    /** Loaded devices located in the access point's dimension and covered by its network's transmitters. */
    private static List<DashboardDevice> candidates(ServerPlayer player, AccessPointBlockEntity point, UUID network) {
        var dimension = point.getLevel().dimension();
        var result = new ArrayList<DashboardDevice>();
        for (var device : DashboardAPI.devices(player.server).getAll()) {
            try {
                var position = device.position();
                if (position.isEmpty() || !device.dimension().filter(dimension::equals).isPresent()) continue;
                if (RadioNetworkService.covers(player.server, network, dimension, position.orElseThrow())) result.add(device);
            } catch (RuntimeException faultyProvider) {
                // A broken integration must not hide every other machine.
            }
        }
        return result;
    }

    private static Entry entry(ServerPlayer player, AccessPointBlockEntity point, UUID network, DashboardDevice device) {
        var manager = DashboardAPI.networks(player.server);
        int distance = (int) Math.round(Math.sqrt(device.position().orElseThrow().distSqr(point.getBlockPos())));
        String name = safe(() -> device.displayName().getString(), device.id().toString());
        String type = safe(() -> device.deviceType().toString(), "unknown");
        boolean member = manager.getNetwork(network).map(home -> home.devices().contains(device.id())).orElse(false);
        if (!(device instanceof NetworkMember binding))
            return new Entry(device.id(), name, type, member ? State.IN_NETWORK : State.UNSUPPORTED, "", distance, false);
        Optional<UUID> current = safe(binding::homeNetwork, Optional.empty());
        if (member || current.filter(network::equals).isPresent())
            return new Entry(device.id(), name, type, State.IN_NETWORK, "", distance, false);
        boolean mayConfigure = safe(() -> binding.canConfigure(player), false);
        var other = current.flatMap(manager::getNetwork);
        if (other.isEmpty()) return new Entry(device.id(), name, type, State.FREE, "", distance, mayConfigure);
        UUID otherId = other.orElseThrow().id();
        String otherName = DashboardAPI.hasPermission(player, otherId, Permission.VIEW) ? other.orElseThrow().name() : "";
        return new Entry(device.id(), name, type, State.OTHER_NETWORK, otherName, distance,
                mayConfigure && DashboardAPI.hasPermission(player, otherId, Permission.MANAGE_NETWORK));
    }

    private static <T> T safe(Supplier<T> value, T fallback) {
        try {
            T result = value.get();
            return result == null ? fallback : result;
        } catch (RuntimeException faultyProvider) {
            return fallback;
        }
    }
}
