package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.MachineSystems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * Device list of the Home editor: a search field, the network-wide widgets, then the devices grouped by HomeLink
 * system (as on the Machines page) and sorted by name. Rows are dragged onto the preview by the editor.
 */
final class EditorPalette {
    static final int SEARCH_HEIGHT = 22;
    private static final int DEVICE_ROW = 20;
    private static final int HEADER_ROW = 14;
    private final DashboardClientState state;
    private final DashboardPreferencesClient preferences;
    private final Font font;
    private String query = "";
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private EditBox search;

    enum Kind { HEADER, DEVICE, ENERGY }

    record Row(Kind kind, String label, DebugDeviceView device) {
        int height() { return kind == Kind.HEADER ? HEADER_ROW : DEVICE_ROW; }
    }

    EditorPalette(DashboardClientState state, DashboardPreferencesClient preferences, Font font) {
        this.state = state;
        this.preferences = preferences;
        this.font = font;
    }

    /** @return the search field, so the screen can give it back the focus after a rebuild */
    EditBox init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (search == null) {
            search = DashboardTheme.input(new EditBox(font, x, y, width, DashboardTheme.CONTROL_HEIGHT, WidgetCards.label("palette_search")));
            search.setHint(WidgetCards.label("palette_search"));
            search.setMaxLength(64);
            search.setValue(query);
            search.setResponder(value -> { query = value; scroll = 0; });
        } else {
            // Keep the cursor and selection while server acknowledgments rebuild the surrounding controls.
            search.setX(x);
            search.setY(y);
            search.setWidth(width);
        }
        addWidget.accept(search);
        scroll = Math.clamp(scroll, 0, maxScroll());
        return search;
    }

    List<Row> rows() {
        String filter = query.strip().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        String energy = WidgetCards.label("energy_balance").getString();
        if (filter.isEmpty() || energy.toLowerCase(Locale.ROOT).contains(filter)) {
            rows.add(new Row(Kind.HEADER, WidgetCards.label("palette_network").getString(), null));
            rows.add(new Row(Kind.ENERGY, energy, null));
        }
        var grouped = new EnumMap<MachineSystems.Group, List<DebugDeviceView>>(MachineSystems.Group.class);
        for (var device : state.devices()) {
            if (!filter.isEmpty() && !device.name().toLowerCase(Locale.ROOT).contains(filter)
                    && !device.searchText().toLowerCase(Locale.ROOT).contains(filter)) continue;
            grouped.computeIfAbsent(MachineSystems.Group.of(device), ignored -> new ArrayList<>()).add(device);
        }
        for (var entry : grouped.entrySet()) {
            var devices = entry.getValue();
            devices.sort(Comparator.comparing((DebugDeviceView device) -> device.name().toLowerCase(Locale.ROOT)).thenComparing(DebugDeviceView::id));
            rows.add(new Row(Kind.HEADER, WidgetCards.label("machines_group_" + entry.getKey().key()).getString() + " (" + devices.size() + ")", null));
            for (var device : devices) rows.add(new Row(Kind.DEVICE, device.name(), device));
        }
        return rows;
    }

    /** @param dragged row being dragged, drawn outlined */
    void render(GuiGraphics graphics, double mouseX, double mouseY, Row dragged) {
        int top = listTop();
        graphics.enableScissor(x, top, x + width, y + height);
        var rows = rows();
        int rowY = top;
        for (int index = scroll; index < rows.size() && rowY < y + height; index++) {
            var row = rows.get(index);
            if (row.kind() == Kind.HEADER) {
                graphics.drawString(font, font.plainSubstrByWidth(row.label(), width - 4), x + 2, rowY + 3, DashboardTheme.ACCENT, false);
            } else {
                boolean hovered = dragged == null && mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + DEVICE_ROW - 2;
                DashboardTheme.panel(graphics, x, rowY, width, DEVICE_ROW - 2);
                if (hovered || row.equals(dragged)) graphics.renderOutline(x, rowY, width, DEVICE_ROW - 2, DashboardTheme.ACCENT);
                int dot = row.kind() == Kind.ENERGY ? DashboardTheme.ACCENT : DashboardTheme.status(row.device().status());
                graphics.fill(x + 5, rowY + 7, x + 9, rowY + 11, dot);
                graphics.drawString(font, font.plainSubstrByWidth(row.label(), width - 30), x + 13, rowY + 5, DashboardTheme.TEXT, false);
                if (row.kind() == Kind.DEVICE) {
                    boolean favored = preferences.profile().favorites().contains(row.device().id());
                    graphics.drawString(font, favored ? "★" : "☆", x + width - 12, rowY + 5, favored ? DashboardTheme.ACCENT : DashboardTheme.MUTED, false);
                }
            }
            rowY += row.height();
        }
        if (rows.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(WidgetCards.label("no_matching_devices").getString(), width - 8),
                x + 4, top + 5, DashboardTheme.MUTED, false);
        graphics.disableScissor();
    }

    /** @return the device or energy row under the cursor, or null (headers are not rows one can take) */
    Row rowAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return null;
        var rows = rows();
        int rowY = listTop();
        for (int index = scroll; index < rows.size(); index++) {
            var row = rows.get(index);
            if (mouseY >= rowY && mouseY < rowY + row.height()) return row.kind() == Kind.HEADER ? null : row;
            rowY += row.height();
        }
        return null;
    }

    /** @return whether the cursor is on the favorite star of a device row */
    boolean overStar(double mouseX) { return mouseX >= x + width - 16; }

    /** @return the center of a device's row when it is visible, for verification; null otherwise */
    double[] center(java.util.UUID device) {
        var rows = rows();
        int rowY = listTop();
        for (int index = scroll; index < rows.size() && rowY < y + height; index++) {
            var row = rows.get(index);
            if (row.kind() == Kind.DEVICE && row.device().id().equals(device)) return new double[] {x + width / 2.0 - 8, rowY + DEVICE_ROW / 2.0};
            rowY += row.height();
        }
        return null;
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= listTop() && mouseY < y + height;
    }

    void scroll(int direction) { scroll = Math.clamp(scroll - direction, 0, maxScroll()); }

    private int listTop() { return y + SEARCH_HEIGHT; }

    /** Scrolling stops once the last row is visible. */
    private int maxScroll() {
        var rows = rows();
        int available = height - SEARCH_HEIGHT, used = 0;
        for (int index = rows.size() - 1; index >= 0; index--) {
            used += rows.get(index).height();
            if (used > available) return index + 1;
        }
        return 0;
    }
}
