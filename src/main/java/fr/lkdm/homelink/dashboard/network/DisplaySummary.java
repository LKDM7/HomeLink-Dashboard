package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;
import java.util.Objects;

/**
 * Small, recipient-authorized view of a display's network; never persisted in block NBT. The recipient's
 * dashboard widgets, when there are any, travel with their saved grid geometry so the screen mirrors Home.
 */
public record DisplaySummary(Mode mode, String networkName, int total, int online, int attention,
                             int offline, List<DeviceLine> devices, List<WidgetTile> widgets) {
    public static final int MAX_LINES = 4;
    public static final int MAX_METRICS = 2;
    public static final int MAX_NAME_LENGTH = 128;
    public static final int MAX_DEVICES = 100_000;
    /** More widgets never fit on the largest display. */
    public static final int MAX_WIDGETS = 12;
    public enum Mode { LIVE, UNBOUND, OFFLINE, RESTRICTED }

    public DisplaySummary {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(networkName);
        devices = List.copyOf(devices);
        widgets = List.copyOf(widgets);
        if (networkName.length() > MAX_NAME_LENGTH || devices.size() > MAX_LINES || widgets.size() > MAX_WIDGETS
                || total < 0 || total > MAX_DEVICES || online < 0 || attention < 0 || offline < 0
                || (long) online + attention + offline != total || devices.size() > total)
            throw new IllegalArgumentException("Invalid display summary");
        if (mode != Mode.LIVE && (!networkName.isEmpty() || total != 0 || !devices.isEmpty() || !widgets.isEmpty()))
            throw new IllegalArgumentException("Inactive display must not expose network data");
    }

    /** Summary without widgets: the display lists favorites. */
    public DisplaySummary(Mode mode, String networkName, int total, int online, int attention, int offline, List<DeviceLine> devices) {
        this(mode, networkName, total, online, attention, offline, devices, List.of());
    }

    public static DisplaySummary empty(Mode mode) {
        return new DisplaySummary(mode, "", 0, 0, 0, 0, List.of());
    }

    /**
     * One dashboard widget at its saved grid place. A summary tile carries up to two metrics; a metric tile
     * carries only its metric, or none when the device no longer provides it; an action tile carries the action
     * name as label; an energy tile carries production then consumption as metrics and the battery charge
     * percentage as label (empty without battery). An unavailable device has an empty name.
     */
    public record WidgetTile(int x, int y, int width, int height, Kind kind, String label, DeviceLine device) {
        public enum Kind { SUMMARY, METRIC, ACTION, ENERGY }

        public WidgetTile {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(label);
            Objects.requireNonNull(device);
            if (!fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.supportedSize(width, height) || x < 0 || y < 0
                    || x > fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.COLUMNS - width
                    || y > fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget.MAX_ROWS - height
                    || label.length() > MAX_NAME_LENGTH
                    || kind == Kind.METRIC && device.metrics().size() > 1 || kind == Kind.ACTION && !device.metrics().isEmpty())
                throw new IllegalArgumentException("Invalid display widget");
        }
    }

    public record DeviceLine(String name, String status, List<MetricLine> metrics) {
        public DeviceLine {
            Objects.requireNonNull(name);
            Objects.requireNonNull(status);
            metrics = List.copyOf(metrics);
            if (name.length() > MAX_NAME_LENGTH || metrics.size() > MAX_METRICS
                    || !List.of("ONLINE", "OFFLINE", "WARNING", "ERROR", "DISABLED", "UNKNOWN").contains(status))
                throw new IllegalArgumentException("Invalid display device line");
        }
        public DeviceLine(String name, String status) { this(name, status, List.of()); }
    }

    public record MetricLine(String name, String type, String unit, WireValue value) {
        public MetricLine {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
            Objects.requireNonNull(unit);
            Objects.requireNonNull(value);
            if (name.length() > MAX_NAME_LENGTH || type.length() > 256 || unit.length() > MAX_NAME_LENGTH
                    || net.minecraft.resources.ResourceLocation.tryParse(type) == null
                    || value.value() instanceof String text && text.length() > MAX_NAME_LENGTH
                    || value.value() instanceof WireValue.EnumName enumValue && enumValue.name().length() > MAX_NAME_LENGTH)
                throw new IllegalArgumentException("Invalid display metric");
        }
    }
}
