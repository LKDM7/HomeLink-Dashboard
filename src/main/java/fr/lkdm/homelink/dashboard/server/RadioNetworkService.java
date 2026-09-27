package fr.lkdm.homelink.dashboard.server;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homelink.dashboard.HomeLinkDashboard;
import fr.lkdm.homelink.dashboard.blockentity.AccessPointBlockEntity;
import fr.lkdm.homelink.dashboard.blockentity.HomeServerBlockEntity;
import fr.lkdm.homelink.dashboard.blockentity.SignalRepeaterBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Loaded transmitters only; no world scans or forced chunk loads. Server-thread owned. */
@EventBusSubscriber(modid = HomeLinkDashboard.MOD_ID)
public final class RadioNetworkService {
    public static final int RANGE = 64;
    private static final Map<MinecraftServer, RadioNetworkService> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final Set<AccessPointBlockEntity> points = new HashSet<>();
    private final Map<UUID, List<AccessPointBlockEntity>> connected = new HashMap<>();
    private long cacheTick = Long.MIN_VALUE;
    private RadioNetworkService(MinecraftServer server) { this.server = server; }

    private static RadioNetworkService get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Radio access requires server thread");
        return INSTANCES.computeIfAbsent(server, key -> {
            var service = new RadioNetworkService(key);
            DashboardAPI.networks(key).setReachabilityPolicy(ResourceLocation.fromNamespaceAndPath(HomeLinkDashboard.MOD_ID, "radio"), service::reachable);
            return service;
        });
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) { get(event.getServer()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { INSTANCES.remove(event.getServer()); }

    public static void changed(AccessPointBlockEntity point) {
        if (point.getLevel() == null || point.getLevel().isClientSide) return;
        var service = get(point.getLevel().getServer());
        service.points.add(point);
        point.networkId().ifPresent(id -> RadioNetworksSavedData.get(service.server).add(id, service.server));
        service.connected.clear();
    }
    public static void removed(AccessPointBlockEntity point) {
        if (point.getLevel() == null || point.getLevel().isClientSide) return;
        var service = INSTANCES.get(point.getLevel().getServer());
        if (service != null) { service.points.remove(point); service.connected.clear(); }
    }
    public static boolean hasSignal(AccessPointBlockEntity point) {
        return point.networkId().isPresent() && point.getLevel() != null && !point.getLevel().isClientSide
                && get(point.getLevel().getServer()).covers(point.networkId().orElseThrow(), point.getLevel().dimension(), point.getBlockPos());
    }
    public static boolean canReceive(AccessPointBlockEntity point, UUID network) {
        return point.getLevel() != null && !point.getLevel().isClientSide
                && get(point.getLevel().getServer()).covers(network, point.getLevel().dimension(), point.getBlockPos());
    }
    private boolean loaded(AccessPointBlockEntity point) {
        Level level = point.getLevel();
        return !point.isRemoved() && point.working() && level != null && level.hasChunkAt(point.getBlockPos())
                && level.getBlockEntity(point.getBlockPos()) == point;
    }
    private List<AccessPointBlockEntity> transmitters(UUID network) {
        long tick = server.getTickCount();
        if (cacheTick != tick) { connected.clear(); cacheTick = tick; }
        return connected.computeIfAbsent(network, id -> {
            List<AccessPointBlockEntity> reached = new ArrayList<>();
            List<AccessPointBlockEntity> pending = new ArrayList<>();
            for (var point : points) {
                if (!point.networkId().filter(id::equals).isPresent() || !loaded(point)) continue;
                if (point instanceof HomeServerBlockEntity) reached.add(point);
                else if (point instanceof SignalRepeaterBlockEntity) pending.add(point);
            }
            // Breadth-first expansion: disconnected cycles never bootstrap themselves.
            for (int cursor = 0; cursor < reached.size(); cursor++) {
                var source = reached.get(cursor);
                var iterator = pending.iterator();
                while (iterator.hasNext()) {
                    var relay = iterator.next();
                    if (source.getLevel() == relay.getLevel() && source.getBlockPos().distSqr(relay.getBlockPos()) <= RANGE * RANGE) {
                        reached.add(relay); iterator.remove();
                    }
                }
            }
            return List.copyOf(reached);
        });
    }
    private boolean covers(UUID network, ResourceKey<Level> dimension, BlockPos position) {
        for (var node : transmitters(network)) {
            if (node.getLevel().dimension().equals(dimension) && node.getBlockPos().distSqr(position) <= RANGE * RANGE) return true;
        }
        return false;
    }
    private boolean reachable(UUID network, DashboardDevice device) {
        if (!RadioNetworksSavedData.get(server).contains(network)) return true;
        var position = device.position();
        var dimension = device.dimension();
        // A purely logical device has no physical radio endpoint; it still requires a live server.
        if (position.isEmpty() && dimension.isEmpty()) return !transmitters(network).isEmpty();
        if (position.isEmpty() || dimension.isEmpty()) return false;
        return covers(network, dimension.orElseThrow(), position.orElseThrow());
    }
}
