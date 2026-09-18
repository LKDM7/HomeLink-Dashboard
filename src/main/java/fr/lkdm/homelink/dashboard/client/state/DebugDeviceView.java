package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Immutable device presentation; no handlers or mutable HomeCore data are retained. */
public record DebugDeviceView(UUID id, String name, String type, String status, List<Metric> metrics,
                              String position, List<String> capabilities, String searchText, List<DeviceActionView> actions) {
    public DebugDeviceView {
        metrics = List.copyOf(metrics);
        capabilities = List.copyOf(capabilities);
        actions = List.copyOf(actions);
        if (searchText == null || searchText.isEmpty())
            searchText = (name + " " + type + " " + String.join(" ", capabilities)).toLowerCase(Locale.ROOT);
    }

    public DebugDeviceView(UUID id, String name, String type, String status, List<Metric> metrics) {
        this(id, name, type, status, metrics, "", List.of(), "", List.of());
    }

    public DebugDeviceView(UUID id, String name, String type, String status, List<Metric> metrics,
                           String position, List<String> capabilities) {
        this(id, name, type, status, metrics, position, capabilities, "", List.of());
    }

    public DebugDeviceView(UUID id, String name, String type, String status, List<Metric> metrics,
                           String position, List<String> capabilities, String searchText) {
        this(id, name, type, status, metrics, position, capabilities, searchText, List.of());
    }

    /** value is null when a future or malformed wire kind could not be decoded. */
    public record Metric(String id, String name, String displayValue, long revision, String type, String unit,
                         WireValue value) {
        public Metric(String id, String name, String displayValue, long revision, String type, String unit) {
            this(id, name, displayValue, revision, type, unit, null);
        }
    }
}
