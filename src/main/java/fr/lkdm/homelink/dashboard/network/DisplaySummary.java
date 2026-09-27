package fr.lkdm.homelink.dashboard.network;

import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;
import java.util.Objects;

/** Small, recipient-authorized view of a display's network; never persisted in block NBT. */
public record DisplaySummary(Mode mode, String networkName, int total, int online, int attention,
                             int offline, List<DeviceLine> devices) {
    public static final int MAX_LINES = 4;
    public static final int MAX_METRICS = 2;
    public static final int MAX_NAME_LENGTH = 128;
    public static final int MAX_DEVICES = 100_000;
    public enum Mode { LIVE, UNBOUND, OFFLINE, RESTRICTED }

    public DisplaySummary {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(networkName);
        devices = List.copyOf(devices);
        if (networkName.length() > MAX_NAME_LENGTH || devices.size() > MAX_LINES
                || total < 0 || total > MAX_DEVICES || online < 0 || attention < 0 || offline < 0
                || (long) online + attention + offline != total || devices.size() > total)
            throw new IllegalArgumentException("Invalid display summary");
        if (mode != Mode.LIVE && (!networkName.isEmpty() || total != 0 || !devices.isEmpty()))
            throw new IllegalArgumentException("Inactive display must not expose network data");
    }

    public static DisplaySummary empty(Mode mode) {
        return new DisplaySummary(mode, "", 0, 0, 0, 0, List.of());
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
