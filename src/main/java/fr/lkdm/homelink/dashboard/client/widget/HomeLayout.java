package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import fr.lkdm.homelink.dashboard.dashboard.widget.EnergyBalance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Layout rules of the Home page, shared by the page and its editor. Widgets keep the saved grid geometry, but the
 * editor never places them cell by cell: it edits their reading order and {@link #pack flows} them again.
 */
public final class HomeLayout {
    /** Widget sizes offered by the editor, in grid cells. */
    public enum Size {
        QUARTER(3, 2), HALF(6, 3), FULL(12, 3);

        public final int width;
        public final int height;
        Size(int width, int height) { this.width = width; this.height = height; }

        public static Size of(DashboardWidget widget) {
            return widget.width() >= DashboardWidget.COLUMNS ? FULL : widget.width() >= HALF.width ? HALF : QUARTER;
        }
        public Size next() { return values()[(ordinal() + 1) % values().length]; }
        public String key() { return "width_" + name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** What a widget of one device can show: its summary, one metric, or a one-click action. */
    public record Preset(DashboardWidget.Type type, String entry, String name) { }

    private HomeLayout() { }

    /** @return a mutable copy in reading order: top to bottom, then left to right */
    public static List<DashboardWidget> ordered(Collection<DashboardWidget> widgets) {
        var result = new ArrayList<>(widgets);
        result.sort(Comparator.comparingInt(DashboardWidget::y).thenComparingInt(DashboardWidget::x));
        return result;
    }

    /** @return the widgets placed left to right in rows, in the given order, or null when they exceed the grid */
    public static List<DashboardWidget> pack(List<DashboardWidget> widgets) {
        List<DashboardWidget> packed = new ArrayList<>();
        int column = 0, row = 0, rowHeight = 0;
        for (var widget : widgets) {
            if (column + widget.width() > DashboardWidget.COLUMNS) { row += rowHeight; column = 0; rowHeight = 0; }
            if (row + widget.height() > DashboardWidget.MAX_ROWS) return null;
            packed.add(new DashboardWidget(widget.id(), widget.type(), widget.deviceId(), widget.metricId(), column, row, widget.width(), widget.height()));
            column += widget.width();
            rowHeight = Math.max(rowHeight, widget.height());
        }
        return packed;
    }

    /** @return a new widget of that content, placed later by {@link #pack} */
    public static DashboardWidget create(DashboardWidget.Type type, UUID device, String entry) {
        return new DashboardWidget(UUID.randomUUID(), type, device, entry, 0, 0, Size.HALF.width, Size.HALF.height);
    }

    public static DashboardWidget resized(DashboardWidget widget, Size size) {
        return new DashboardWidget(widget.id(), widget.type(), widget.deviceId(), widget.metricId(), 0, 0, size.width, size.height);
    }

    public static DashboardWidget showing(DashboardWidget widget, Preset preset) {
        return new DashboardWidget(widget.id(), preset.type(), widget.deviceId(), preset.entry(), 0, 0, widget.width(), widget.height());
    }

    public static List<Preset> presets(DebugDeviceView device) {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset(DashboardWidget.Type.DEVICE_SUMMARY, "", ""));
        for (var metric : device.metrics()) presets.add(new Preset(DashboardWidget.Type.METRIC, metric.id(), metric.name()));
        for (var action : device.actions())
            if (action.supported() && DashboardWidget.oneClick(action.type())) presets.add(new Preset(DashboardWidget.Type.ACTION, action.id(), action.name()));
        return presets;
    }

    /** @return index of the widget's content among the presets, 0 (the summary) when it is gone */
    public static int presetIndex(DashboardWidget widget, List<Preset> presets) {
        for (int index = 0; index < presets.size(); index++)
            if (presets.get(index).type() == widget.type() && presets.get(index).entry().equals(widget.metricId())) return index;
        return 0;
    }

    public static EnergyBalance.Result energy(List<DebugDeviceView> devices) {
        return EnergyBalance.of(devices.stream().filter(device -> device.type().startsWith("homelink_energy:")).map(device -> new EnergyBalance.Source() {
            @Override public String type() { return device.type(); }
            @Override public double number(String metric) {
                for (var value : device.metrics())
                    if (value.id().equals(metric) && value.value() != null && value.value().value() instanceof Number number) return number.doubleValue();
                return 0;
            }
        }).toList());
    }
}
