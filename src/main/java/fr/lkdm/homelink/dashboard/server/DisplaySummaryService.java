package fr.lkdm.homelink.dashboard.server;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.network.DisplaySummary;
import fr.lkdm.homelink.dashboard.network.DisplaySummaryPayloads;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-thread summaries, authorized independently for each nearby player. */
public final class DisplaySummaryService {
    public static final int RANGE = 16;
    private DisplaySummaryService() { }

    /** Called by the master's existing one-second tick, without loading any chunks. */
    public static void refresh(DashboardDisplayBlockEntity point) {
        if (!(point.getLevel() instanceof ServerLevel level) || point.isRemoved()) return;
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Display summaries require server thread");
        for (var player : level.players()) {
            if (nearby(player, point)) PacketDistributor.sendToPlayer(player,
                    new DisplaySummaryPayloads.Update(level.dimension().location(), point.getBlockPos(), describe(player, point)));
        }
    }

    /** A testable entry point whose caller is the authenticated server player, never packet identity. */
    public static DisplaySummary describe(ServerPlayer player, DashboardDisplayBlockEntity point) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Display summaries require server thread");
        if (point.isRemoved() || point.getLevel() != player.level() || !nearby(player, point)
                || !player.level().hasChunkAt(point.getBlockPos()) || player.level().getBlockEntity(point.getBlockPos()) != point)
            return DisplaySummary.empty(DisplaySummary.Mode.RESTRICTED);
        var networkId = point.networkId();
        if (networkId.isEmpty()) return DisplaySummary.empty(point.owner().filter(player.getUUID()::equals).isPresent()
                ? DisplaySummary.Mode.UNBOUND : DisplaySummary.Mode.RESTRICTED);
        try {
            var id = networkId.orElseThrow();
            if (!DashboardAPI.hasPermission(player, id, Permission.VIEW)) return DisplaySummary.empty(DisplaySummary.Mode.RESTRICTED);
            if (!point.working() || !RadioNetworkService.hasSignal(point)) return DisplaySummary.empty(DisplaySummary.Mode.OFFLINE);
            var manager = DashboardAPI.networks(player.server);
            var network = manager.getNetwork(id).orElseThrow();
            var registry = DashboardAPI.devices(player.server);
            var lines = new ArrayList<DisplaySummary.DeviceLine>();
            var order = Comparator.comparing(DisplaySummary.DeviceLine::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(DisplaySummary.DeviceLine::name);
            int online = 0, attention = 0, offline = 0;
            // Favorites have set semantics; UUID order supplies a stable tiebreak for identical names.
            var favorites = DashboardPreferencesSavedData.get(player.server).profile(player.getUUID(), id).favorites();
            for (var deviceId : favorites.stream().sorted().toList()) {
                try {
                    var candidate = registry.get(deviceId);
                    if (candidate.isEmpty() || !manager.isReachable(id, candidate.orElseThrow())) continue;
                    var device = candidate.orElseThrow();
                    var status = device.status().state();
                    String name = plainName(device.displayName().getString());
                    var metrics = new ArrayList<DisplaySummary.MetricLine>();
                    for (var metric : device.metrics().stream().limit(DisplaySummary.MAX_METRICS).toList()) {
                        try { metrics.add(metricLine(metric)); }
                        catch (RuntimeException unavailableMetric) { /* Isolate malformed provider values. */ }
                    }
                    var line = new DisplaySummary.DeviceLine(name, status.name(), metrics);
                    switch (status) {
                        case ONLINE -> online++;
                        case WARNING, ERROR -> attention++;
                        default -> offline++;
                    }
                    lines.add(line);
                    lines.sort(order);
                    if (lines.size() > DisplaySummary.MAX_LINES) lines.removeLast();
                } catch (RuntimeException unavailableDevice) {
                    // A faulty provider must not crash the display or expose an unvalidated partial row.
                }
            }
            return new DisplaySummary(DisplaySummary.Mode.LIVE, plainName(network.name()), online + attention + offline,
                    online, attention, offline, lines);
        } catch (RuntimeException unavailableNetwork) {
            return DisplaySummary.empty(DisplaySummary.Mode.OFFLINE);
        }
    }

    private static boolean nearby(ServerPlayer player, DashboardDisplayBlockEntity point) {
        return player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(point.getBlockPos())) <= RANGE * RANGE;
    }
    private static DisplaySummary.MetricLine metricLine(DeviceMetric<?> metric) {
        WireValue value;
        try {
            value = WireValue.from(metric.value());
            if (value.value() instanceof String text) value = WireValue.from(plainName(text));
            else if (value.value() instanceof WireValue.EnumName text)
                value = WireValue.from(new WireValue.EnumName(plainName(text.name())));
        } catch (RuntimeException unsupportedValue) { value = WireValue.from("?"); }
        return new DisplaySummary.MetricLine(plainName(metric.displayName().getString()), metric.type().id().toString(),
                plainName(metric.unit().symbol()), value);
    }
    private static String plainName(String value) {
        var clean = new StringBuilder();
        for (int i = 0; i < value.length() && clean.length() < DisplaySummary.MAX_NAME_LENGTH; i++) {
            char character = value.charAt(i);
            if (character == '\u00a7') { i++; continue; }
            if (!Character.isISOControl(character)) clean.append(character);
        }
        if (!clean.isEmpty() && Character.isHighSurrogate(clean.charAt(clean.length() - 1))) clean.setLength(clean.length() - 1);
        return clean.toString();
    }
}
