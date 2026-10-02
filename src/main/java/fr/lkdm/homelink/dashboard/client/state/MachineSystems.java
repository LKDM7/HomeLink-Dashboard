package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.transport.WireValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Groups watched devices by HomeLink system and derives a summary from their public HomeCore metrics.
 * Only device type and metric identifiers are read; the dashboard keeps no compile-time dependency on the machine mods.
 */
public final class MachineSystems {
    /** Known HomeLink modules, keyed by the namespace of their device types. OTHER collects third-party devices. */
    public enum Group {
        ENERGY("homelink_energy"), FARM("homelink_farm"), QUARRY("homelink_quarry"), STORAGE("homelink_storage"), OTHER("");

        private final String namespace;
        Group(String namespace) { this.namespace = namespace; }
        public String key() { return name().toLowerCase(Locale.ROOT); }

        /** Storage runs its own self-created network, so its group only appears when the watched network holds a controller. */
        boolean hiddenWhenEmpty() { return this == STORAGE || this == OTHER; }

        public static Group of(DebugDeviceView device) {
            String type = device.type();
            int split = type.indexOf(':');
            String namespace = split < 0 ? "" : type.substring(0, split);
            for (Group group : values()) if (group != OTHER && group.namespace.equals(namespace)) return group;
            return OTHER;
        }
    }

    /** One summary line: translation key, preformatted arguments and an optional progress fraction (negative = none). */
    public record Line(String key, List<String> args, double fraction) {
        public Line { args = List.copyOf(args); }
        static Line of(String key, String... args) { return new Line(key, List.of(args), -1); }
        static Line bar(String key, double fraction, String... args) {
            return new Line(key, List.of(args), Double.isFinite(fraction) ? Math.max(0, Math.min(1, fraction)) : 0);
        }
    }

    public record Summary(Group group, List<DebugDeviceView> devices, int online, int warning, int offline, List<Line> lines) {
        public Summary {
            devices = List.copyOf(devices);
            lines = List.copyOf(lines);
        }
        public int total() { return devices.size(); }
        /** Worst state among the group's devices, for the card indicator. */
        public String status() {
            if (devices.isEmpty()) return "UNKNOWN";
            if (offline > 0) return "OFFLINE";
            return warning > 0 ? "WARNING" : "ONLINE";
        }
    }

    private static final Comparator<DebugDeviceView> ORDER = Comparator
            .comparingInt((DebugDeviceView device) -> statusRank(device.status()))
            .thenComparing(device -> device.name().toLowerCase(Locale.ROOT))
            .thenComparing(DebugDeviceView::id);

    private MachineSystems() { }

    /** Energy, farm and quarry are always present; storage and OTHER only when they hold devices. */
    public static Map<Group, Summary> summarize(List<DebugDeviceView> devices) {
        Map<Group, List<DebugDeviceView>> grouped = new EnumMap<>(Group.class);
        for (Group group : Group.values()) grouped.put(group, new ArrayList<>());
        for (DebugDeviceView device : devices) grouped.get(Group.of(device)).add(device);
        Map<Group, Summary> result = new EnumMap<>(Group.class);
        for (var entry : grouped.entrySet()) {
            if (entry.getKey().hiddenWhenEmpty() && entry.getValue().isEmpty()) continue;
            result.put(entry.getKey(), summarize(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    static Summary summarize(Group group, List<DebugDeviceView> members) {
        List<DebugDeviceView> sorted = new ArrayList<>(members);
        sorted.sort(ORDER);
        int online = 0, warning = 0, offline = 0;
        for (DebugDeviceView device : sorted) {
            switch (device.status()) {
                case "ONLINE" -> online++;
                case "WARNING" -> warning++;
                default -> offline++;
            }
        }
        List<Line> lines = switch (group) {
            case ENERGY -> energy(sorted);
            case FARM -> farm(sorted);
            case QUARRY -> quarry(sorted);
            case STORAGE -> storage(sorted);
            case OTHER -> List.of();
        };
        return new Summary(group, sorted, online, warning, offline, lines);
    }

    private static List<Line> energy(List<DebugDeviceView> devices) {
        double production = 0;
        long stored = 0, capacity = 0;
        int producers = 0, batteries = 0;
        for (DebugDeviceView device : devices) {
            switch (path(device)) {
                case "solar_panel" -> { producers++; production += number(device, "homelink_energy:generation_rate"); }
                case "wind_turbine", "hydro_turbine" -> { producers++; production += number(device, "homelink_energy:current_generation"); }
                case "battery" -> {
                    batteries++;
                    stored += (long) number(device, "homelink_energy:stored_energy");
                    capacity += (long) number(device, "homelink_energy:capacity");
                }
                default -> { }
            }
        }
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("energy_production", decimal(production) + " HE/t", Integer.toString(producers)));
        if (batteries > 0) lines.add(Line.bar("energy_storage", capacity <= 0 ? 0 : (double) stored / capacity,
                compact(stored), compact(capacity), Integer.toString(batteries)));
        else lines.add(Line.of("energy_no_battery"));
        return lines;
    }

    private static List<Line> farm(List<DebugDeviceView> devices) {
        long crops = 0, problems = 0;
        double readyWeighted = 0;
        int pumps = 0, activePumps = 0, bots = 0, workingBots = 0;
        for (DebugDeviceView device : devices) {
            switch (path(device)) {
                case "farm_controller" -> {
                    long count = (long) number(device, "homelink_farm:crop_count");
                    crops += count;
                    readyWeighted += count * number(device, "homelink_farm:ready_percentage");
                    problems += (long) number(device, "homelink_farm:problem_count");
                }
                case "irrigation_pump" -> { pumps++; if (text(device, "homelink_farm:pump_status").equals("ACTIVE")) activePumps++; }
                case "farmbot_station" -> {
                    bots++;
                    String status = text(device, "homelink_farm:farmbot_status");
                    if (List.of("SEARCHING", "MOVING", "HARVESTING", "RETURNING", "UNLOADING").contains(status)) workingBots++;
                }
                default -> { }
            }
        }
        double ready = crops <= 0 ? 0 : readyWeighted / crops;
        List<Line> lines = new ArrayList<>();
        lines.add(Line.bar("farm_crops", ready / 100, compact(crops), decimal(Math.round(ready * 10) / 10.0) + "%"));
        lines.add(Line.of("farm_equipment", activePumps + "/" + pumps, workingBots + "/" + bots));
        lines.add(Line.of("farm_problems", Long.toString(problems)));
        return lines;
    }

    private static List<Line> quarry(List<DebugDeviceView> devices) {
        int quarries = 0, mining = 0;
        long mined = 0;
        double progress = 0;
        for (DebugDeviceView device : devices) {
            if (!path(device).equals("quarry")) continue;
            quarries++;
            if (text(device, "homelink_quarry:status").equals("MINING")) mining++;
            mined += (long) number(device, "homelink_quarry:blocks_mined");
            progress += number(device, "homelink_quarry:progress");
        }
        double average = quarries == 0 ? 0 : progress / quarries;
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("quarry_active", mining + "/" + quarries));
        lines.add(Line.bar("quarry_progress", average / 100, decimal(Math.round(average * 10) / 10.0) + "%", compact(mined)));
        return lines;
    }

    private static List<Line> storage(List<DebugDeviceView> devices) {
        int controllers = 0;
        long items = 0, full = 0;
        double fill = 0;
        for (DebugDeviceView device : devices) {
            if (!path(device).equals("storage_controller")) continue;
            controllers++;
            items += (long) number(device, "homelink_storage:item_count");
            full += (long) number(device, "homelink_storage:full_inventories");
            fill += number(device, "homelink_storage:capacity");
        }
        double average = controllers == 0 ? 0 : fill / controllers;
        List<Line> lines = new ArrayList<>();
        lines.add(Line.bar("storage_fill", average / 100, decimal(Math.round(average * 10) / 10.0) + "%", compact(items)));
        lines.add(Line.of("storage_full", Long.toString(full)));
        return lines;
    }

    /** Two metric ids shown on a machine row, chosen per known device type; unknown types show their first metrics. */
    public static List<DebugDeviceView.Metric> keyMetrics(DebugDeviceView device) {
        List<String> ids = switch (device.type()) {
            case "homelink_energy:solar_panel" -> List.of("homelink_energy:status", "homelink_energy:generation_rate");
            case "homelink_energy:wind_turbine" -> List.of("homelink_energy:wind_status", "homelink_energy:current_generation");
            case "homelink_energy:hydro_turbine" -> List.of("homelink_energy:hydro_turbine_status", "homelink_energy:current_generation");
            case "homelink_energy:hydro_pump" -> List.of("homelink_energy:hydro_pump_status", "homelink_energy:available_flow");
            case "homelink_energy:battery" -> List.of("homelink_energy:percentage", "homelink_energy:stored_energy");
            case "homelink_farm:farm_controller" -> List.of("homelink_farm:crop_count", "homelink_farm:ready_percentage");
            case "homelink_farm:irrigation_pump" -> List.of("homelink_farm:pump_status", "homelink_farm:sprinklers_connected");
            case "homelink_farm:farmbot_station" -> List.of("homelink_farm:farmbot_status", "homelink_farm:farmbot_battery");
            case "homelink_quarry:quarry" -> List.of("homelink_quarry:status", "homelink_quarry:progress");
            case "homelink_storage:storage_controller" -> List.of("homelink_storage:capacity", "homelink_storage:item_count");
            default -> List.of();
        };
        List<DebugDeviceView.Metric> result = new ArrayList<>();
        for (String id : ids) metric(device, id).ifPresent(result::add);
        for (int index = 0; result.size() < 2 && index < device.metrics().size(); index++) {
            var metric = device.metrics().get(index);
            if (!result.contains(metric)) result.add(metric);
        }
        return result;
    }

    /** Device type path, e.g. {@code battery} for {@code homelink_energy:battery}. */
    public static String path(DebugDeviceView device) {
        int split = device.type().indexOf(':');
        return split < 0 ? device.type() : device.type().substring(split + 1);
    }

    private static Optional<DebugDeviceView.Metric> metric(DebugDeviceView device, String id) {
        for (var metric : device.metrics()) if (metric.id().equals(id)) return Optional.of(metric);
        return Optional.empty();
    }

    /** Numeric metric value; missing, malformed or non-finite values count as zero. */
    static double number(DebugDeviceView device, String id) {
        Object value = metric(device, id).map(DebugDeviceView.Metric::value).map(WireValue::value).orElse(null);
        double result = switch (value) {
            case Number number -> number.doubleValue();
            case Percentage percent -> percent.value();
            case null, default -> 0;
        };
        return Double.isFinite(result) ? result : 0;
    }

    /** Enumeration or text metric value; empty when missing. */
    static String text(DebugDeviceView device, String id) {
        Object value = metric(device, id).map(DebugDeviceView.Metric::value).map(WireValue::value).orElse(null);
        return switch (value) {
            case WireValue.EnumName enumeration -> enumeration.name();
            case String string -> string;
            case null, default -> "";
        };
    }

    private static int statusRank(String status) {
        return switch (status) {
            case "OFFLINE", "ERROR" -> 0;
            case "WARNING" -> 1;
            case "ONLINE" -> 3;
            default -> 2;
        };
    }

    static String decimal(double value) {
        if (!Double.isFinite(value)) return "?";
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String compact(long value) {
        long magnitude = Math.abs(value);
        if (magnitude < 1_000) return Long.toString(value);
        long divisor;
        String suffix;
        if (magnitude >= 1_000_000_000_000L) { divisor = 1_000_000_000_000L; suffix = "T"; }
        else if (magnitude >= 1_000_000_000L) { divisor = 1_000_000_000L; suffix = "G"; }
        else if (magnitude >= 1_000_000L) { divisor = 1_000_000L; suffix = "M"; }
        else { divisor = 1_000; suffix = "k"; }
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + suffix;
    }
}
