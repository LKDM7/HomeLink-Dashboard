package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Personal HomeCore favorites and a persisted twelve-column dashboard.
 *
 * <p>The editor lists every device on the left; a device is dragged onto the home preview on the right to
 * add it. Widgets are laid out automatically in reading order, so dragging a widget only changes its place
 * in that order. Clicking a widget selects it and the bottom bar chooses what it shows (the device summary
 * or one of its metrics), its width, or removes it.
 */
public final class HomeDashboardView {
    private static final int CELL_HEIGHT = 20;
    private static final int FAVORITE_ROW = 48;
    private static final int EDITOR_TOP = 32;
    private static final int PALETTE_ROW = 20;
    private static final int DRAG_THRESHOLD = 4;
    private final DashboardClientState state;
    private final DashboardPreferencesClient preferences;
    private final Font font;
    private final List<Button> actionButtons = new ArrayList<>();
    private Consumer<AbstractWidget> addWidget;
    private Runnable rebuild = () -> { };
    private UUID deviceId;
    private UUID widgetId;
    private int metricIndex;
    private int scroll;
    private int paletteScroll;
    private int previewScroll;
    /** Width of the preset name between the bottom bar arrows, set by the bar layout. */
    private int presetWidth = 30;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean editMode;
    private long revision = -1;
    private long deviceRevision = -1;
    private int online;
    private int warning;
    private int offline;
    private String localError = "";
    // Drag in progress: a device from the list or a widget of the preview, with the press position.
    private UUID dragDevice;
    private UUID dragWidget;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private double mouseX;
    private double mouseY;

    public HomeDashboardView(DashboardClientState state, DashboardPreferencesClient preferences, Font font) {
        this.state = state;
        this.preferences = preferences;
        this.font = font;
    }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget, Runnable rebuild) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.addWidget = addWidget;
        this.rebuild = rebuild;
        actionButtons.clear();
        ensureSelection();
        if (!editMode) {
            button(label("edit_dashboard"), width - 96, 0, 96, () -> setEditMode(true));
        } else {
            button(label("done"), width - 70, 0, 70, () -> setEditMode(false));
            initWidgetBar();
        }
        revision = preferences.revision();
        updateButtons();
    }

    /** Bottom bar of the preview: what the selected widget shows, its width, and removal. */
    private void initWidgetBar() {
        int left = previewLeft();
        int bar = height - DashboardTheme.CONTROL_HEIGHT;
        DashboardWidget selected = selectedWidget();
        Component widthLabel = Component.translatable("screen.homelink_dashboard.widget_width",
                label(selected != null && selected.width() == DashboardWidget.COLUMNS ? "width_full" : "width_half"));
        int removeWidth = font.width(label("remove_widget")) + 12, widthWidth = font.width(widthLabel) + 12;
        presetWidth = Math.max(30, width - left - 48 - widthWidth - removeWidth - 8);
        action(button(Component.literal("<"), left, bar, 20, () -> cyclePreset(-1)));
        action(button(Component.literal(">"), left + 24 + presetWidth, bar, 20, () -> cyclePreset(1)));
        Button resize = button(widthLabel, left + 48 + presetWidth + 4, bar, widthWidth, this::resizeSelected);
        resize.setTooltip(Tooltip.create(label("widget_width_tooltip")));
        action(resize);
        action(button(label("remove_widget"), width - removeWidth, bar, removeWidth, this::removeSelected));
    }

    public void tick() {
        if (deviceRevision != state.revision()) {
            deviceRevision = state.revision();
            online = warning = offline = 0;
            for (var device : state.devices()) {
                switch (device.status()) {
                    case "ONLINE" -> online++;
                    case "WARNING" -> warning++;
                    default -> offline++;
                }
            }
        }
        if (revision != preferences.revision()) {
            revision = preferences.revision();
            ensureSelection();
            rebuild.run();
        }
        updateButtons();
        scroll = Math.clamp(scroll, 0, maxScroll());
        paletteScroll = Math.clamp(paletteScroll, 0, maxPaletteScroll());
        previewScroll = Math.clamp(previewScroll, 0, maxPreviewScroll());
    }

    private void updateButtons() {
        boolean enabled = canEdit() && selectedWidget() != null;
        for (Button button : actionButtons) button.active = enabled;
    }

    private void ensureSelection() {
        if (device() == null && !state.devices().isEmpty()) selectDevice(state.devices().getFirst().id());
        if (selectedWidget() == null) widgetId = null;
    }

    public void selectDevice(UUID id) { deviceId = id; metricIndex = 0; }
    public void selectWidget(UUID id) { widgetId = id; }
    public void setEditMode(boolean value) {
        editMode = value;
        localError = "";
        dragDevice = dragWidget = null;
        dragging = false;
        rebuild.run();
    }
    public void toggleFavorite() { toggleFavorite(deviceId); }
    private void toggleFavorite(UUID device) {
        if (device != null && preferences.ready() && !preferences.pending()) preferences.toggleFavorite(device);
    }

    /** Appends the selected device's summary. */
    public void addSummary() { insert(DashboardWidget.Type.DEVICE_SUMMARY, deviceId, "", preferences.profile().widgets().size()); }
    /** Appends the selected metric of the selected device. */
    public void addMetric() {
        var device = device();
        if (device != null && !device.metrics().isEmpty())
            insert(DashboardWidget.Type.METRIC, deviceId, device.metrics().get(Math.floorMod(metricIndex, device.metrics().size())).id(),
                    preferences.profile().widgets().size());
    }
    private void insert(DashboardWidget.Type type, UUID device, String metric, int index) {
        if (device == null) return;
        var widgets = ordered();
        if (widgets.size() >= DashboardProfile.MAX_WIDGETS) { localError = "WIDGET_LIMIT"; return; }
        DashboardWidget widget = new DashboardWidget(UUID.randomUUID(), type, device, metric, 0, 0, 6, 3);
        widgets.add(Math.clamp(index, 0, widgets.size()), widget);
        if (save(widgets)) widgetId = widget.id();
    }

    /** Moves the selected widget one place earlier (negative) or later (positive) in the display order. */
    public void moveSelected(int step) {
        int index = ordered().indexOf(selectedWidget());
        if (index >= 0) moveTo(index, index + step + (step > 0 ? 1 : 0));
    }
    /** Moves the widget at {@code from} before the widget currently at {@code target} (or to the end). */
    private void moveTo(int from, int target) {
        var widgets = ordered();
        if (from < 0 || from >= widgets.size()) return;
        int destination = Math.clamp(target > from ? target - 1 : target, 0, widgets.size() - 1);
        if (destination == from) return;
        widgets.add(destination, widgets.remove(from));
        save(widgets);
    }
    /** Switches the selected widget between half and full width. */
    public void resizeSelected() {
        var widget = selectedWidget();
        if (widget != null) replace(widget, widget.type(), widget.metricId(), widget.width() == DashboardWidget.COLUMNS ? 6 : DashboardWidget.COLUMNS);
    }
    public void removeSelected() {
        var widgets = ordered();
        int index = widgets.indexOf(selectedWidget());
        if (index < 0) return;
        widgets.remove(index);
        if (save(widgets)) widgetId = null;
    }

    /**
     * Preset contents of a widget: the device summary, then each metric of the device.
     * Steps through them for the selected widget.
     */
    private void cyclePreset(int step) {
        var widget = selectedWidget();
        var device = widget == null ? null : findDevice(widget.deviceId());
        if (device == null) return;
        int count = device.metrics().size() + 1;
        int next = Math.floorMod(presetIndex(widget, device) + step, count);
        if (next == 0) replace(widget, DashboardWidget.Type.DEVICE_SUMMARY, "", widget.width());
        else replace(widget, DashboardWidget.Type.METRIC, device.metrics().get(next - 1).id(), widget.width());
    }
    private static int presetIndex(DashboardWidget widget, DebugDeviceView device) {
        if (widget.type() == DashboardWidget.Type.DEVICE_SUMMARY) return 0;
        for (int index = 0; index < device.metrics().size(); index++)
            if (device.metrics().get(index).id().equals(widget.metricId())) return index + 1;
        return 0;
    }
    private String presetName(DashboardWidget widget) {
        if (widget.type() == DashboardWidget.Type.DEVICE_SUMMARY) return label("preset_summary").getString();
        var metric = metric(widget);
        return metric == null ? label("metric_unavailable").getString() : metric.name();
    }

    private void replace(DashboardWidget widget, DashboardWidget.Type type, String metric, int widgetWidth) {
        var widgets = ordered();
        int index = widgets.indexOf(widget);
        if (index < 0) return;
        widgets.set(index, new DashboardWidget(widget.id(), type, widget.deviceId(), metric, 0, 0, widgetWidth, 3));
        save(widgets);
    }

    /** Places the widgets in reading order, left to right then top to bottom, and saves them. */
    private boolean save(List<DashboardWidget> widgets) {
        if (!canEdit()) return false;
        List<DashboardWidget> packed = new ArrayList<>();
        int column = 0, row = 0, rowHeight = 0;
        for (var widget : widgets) {
            if (column + widget.width() > DashboardWidget.COLUMNS) { row += rowHeight; column = 0; rowHeight = 0; }
            if (row + widget.height() > DashboardWidget.MAX_ROWS) { localError = "GRID_FULL"; return false; }
            packed.add(new DashboardWidget(widget.id(), widget.type(), widget.deviceId(), widget.metricId(), column, row, widget.width(), widget.height()));
            column += widget.width();
            rowHeight = Math.max(rowHeight, widget.height());
        }
        localError = "";
        return preferences.saveLayout(packed);
    }

    /** @return a mutable copy of the widgets in display order */
    private List<DashboardWidget> ordered() {
        var widgets = new ArrayList<>(preferences.profile().widgets());
        widgets.sort(Comparator.comparingInt(DashboardWidget::y).thenComparingInt(DashboardWidget::x));
        return widgets;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 0) return;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        graphics.enableScissor(x, y, x + width, y + height);
        if (editMode) renderEditor(graphics); else renderHome(graphics);
        graphics.disableScissor();
    }

    // ---- Editor -------------------------------------------------------------------------------------------

    private void renderEditor(GuiGraphics graphics) {
        text(graphics, label("editor_title").getString(), x, y + 5, width - 78, DashboardTheme.TEXT);
        String result = !localError.isEmpty() ? localError : preferences.pending() ? "…" : preferences.result();
        if (!result.equals("SUCCESS")) {
            String message = DashboardText.message(result);
            int titleWidth = font.width(label("editor_title")) + 12;
            text(graphics, message, x + titleWidth, y + 5, width - 78 - titleWidth, DashboardTheme.WARNING);
        }
        renderPalette(graphics);
        renderPreview(graphics);
        renderWidgetBar(graphics);
        if (dragging) renderDrag(graphics);
    }

    private void renderPalette(GuiGraphics graphics) {
        int column = paletteWidth();
        var devices = state.devices();
        text(graphics, Component.translatable("screen.homelink_dashboard.editor_devices", devices.size()).getString(),
                x, y + EDITOR_TOP - 11, column, DashboardTheme.ACCENT);
        graphics.fill(x + column + 5, y + EDITOR_TOP - 11, x + column + 6, y + height, DashboardTheme.LINE);
        int top = y + EDITOR_TOP;
        graphics.enableScissor(x, top, x + column, y + height);
        for (int index = paletteScroll; index < devices.size(); index++) {
            int rowY = top + (index - paletteScroll) * PALETTE_ROW;
            if (rowY >= y + height) break;
            var device = devices.get(index);
            boolean hovered = !dragging && mouseX >= x && mouseX < x + column && mouseY >= rowY && mouseY < rowY + PALETTE_ROW - 2;
            DashboardTheme.panel(graphics, x, rowY, column, PALETTE_ROW - 2);
            if (hovered || device.id().equals(dragDevice)) graphics.renderOutline(x, rowY, column, PALETTE_ROW - 2, DashboardTheme.ACCENT);
            graphics.fill(x + 5, rowY + 7, x + 9, rowY + 11, DashboardTheme.status(device.status()));
            text(graphics, device.name(), x + 13, rowY + 5, column - 30, DashboardTheme.TEXT);
            boolean favored = preferences.profile().favorites().contains(device.id());
            text(graphics, favored ? "★" : "☆", x + column - 12, rowY + 5, 10, favored ? DashboardTheme.ACCENT : DashboardTheme.MUTED);
        }
        if (devices.isEmpty()) text(graphics, label("no_matching_devices").getString(), x + 4, top + 5, column - 8, DashboardTheme.MUTED);
        graphics.disableScissor();
    }

    private void renderPreview(GuiGraphics graphics) {
        int left = x + previewLeft();
        int span = width - previewLeft();
        text(graphics, label(canEdit() ? "editor_hint" : "editor_read_only").getString(), left, y + EDITOR_TOP - 11, span, DashboardTheme.MUTED);
        int top = y + EDITOR_TOP, bottom = previewBottom();
        graphics.fill(left, top, left + span, bottom, 0xFF1D1F20);
        graphics.enableScissor(left, top, left + span, bottom);
        var widgets = ordered();
        for (var widget : widgets) {
            int[] card = previewCard(widget);
            if (card[1] + card[3] <= top || card[1] >= bottom) continue;
            renderWidget(graphics, widget, card[0], card[1], card[2], card[3]);
            if (widget.id().equals(widgetId)) graphics.renderOutline(card[0] - 1, card[1] - 1, card[2] + 2, card[3] + 2, DashboardTheme.ACCENT);
        }
        if (widgets.isEmpty()) {
            int lineY = top + 10;
            for (var line : font.split(label("no_widgets"), Math.max(16, span - 20))) {
                graphics.drawString(font, line, left + 10, lineY, DashboardTheme.MUTED, false);
                lineY += 12;
            }
        }
        graphics.disableScissor();
    }

    private void renderWidgetBar(GuiGraphics graphics) {
        var widget = selectedWidget();
        int left = x + previewLeft();
        int bar = y + height - DashboardTheme.CONTROL_HEIGHT;
        if (widget == null) {
            // The bar buttons stay inactive; a hint replaces the preset name.
            DashboardTheme.panel(graphics, left + 22, bar, presetWidth, DashboardTheme.CONTROL_HEIGHT);
            text(graphics, label("editor_select").getString(), left + 26, bar + 5, presetWidth - 8, DashboardTheme.MUTED);
            return;
        }
        DashboardTheme.panel(graphics, left + 22, bar, presetWidth, DashboardTheme.CONTROL_HEIGHT);
        String value = font.plainSubstrByWidth(presetName(widget), presetWidth - 8);
        graphics.drawString(font, value, left + 22 + (presetWidth - font.width(value)) / 2, bar + 5, DashboardTheme.TEXT, false);
    }

    /** Ghost of the dragged item under the cursor and the insertion mark in the preview. */
    private void renderDrag(GuiGraphics graphics) {
        if (overPreview(mouseX, mouseY)) {
            var widgets = ordered();
            int index = dropIndex(mouseX, mouseY);
            int left = x + previewLeft();
            if (index < widgets.size()) {
                int[] card = previewCard(widgets.get(index));
                graphics.fill(card[0] - 3, card[1], card[0] - 1, card[1] + card[3], DashboardTheme.ACCENT);
            } else {
                int end = widgets.isEmpty() ? y + EDITOR_TOP + 2 : endOfPreview();
                graphics.fill(left, end, left + width - previewLeft(), end + 2, DashboardTheme.ACCENT);
            }
        }
        String name;
        if (dragDevice != null) {
            var device = findDevice(dragDevice);
            name = device == null ? "" : device.name();
        } else {
            var widget = findWidget(dragWidget);
            name = widget == null ? "" : widgetName(widget);
        }
        int ghostWidth = Math.min(140, font.width(name) + 12);
        int gx = (int) mouseX + 6, gy = (int) mouseY + 6;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(gx, gy, gx + ghostWidth, gy + 16, 0xE0303234);
        graphics.renderOutline(gx, gy, ghostWidth, 16, DashboardTheme.ACCENT);
        text(graphics, name, gx + 6, gy + 4, ghostWidth - 12, DashboardTheme.TEXT);
        graphics.pose().popPose();
    }

    private String widgetName(DashboardWidget widget) {
        var device = findDevice(widget.deviceId());
        String name = device == null ? label("device_unloaded").getString() : device.name();
        return widget.type() == DashboardWidget.Type.DEVICE_SUMMARY ? name : name + " · " + presetName(widget);
    }

    /** @return x, y, width and height of a widget in the editor preview */
    private int[] previewCard(DashboardWidget widget) {
        int left = x + previewLeft() + 3;
        int span = width - previewLeft() - 6;
        return new int[] {left + widget.x() * span / DashboardWidget.COLUMNS, y + EDITOR_TOP + 3 + widget.y() * CELL_HEIGHT - previewScroll,
                widget.width() * span / DashboardWidget.COLUMNS - 4, widget.height() * CELL_HEIGHT - 4};
    }

    private int endOfPreview() {
        int rows = 0;
        for (var widget : preferences.profile().widgets()) rows = Math.max(rows, widget.y() + widget.height());
        return y + EDITOR_TOP + 3 + rows * CELL_HEIGHT - previewScroll - 2;
    }

    /** Index before which a drop at the cursor inserts: the hovered card's half decides before or after it. */
    private int dropIndex(double mouseX, double mouseY) {
        var widgets = ordered();
        for (int index = 0; index < widgets.size(); index++) {
            int[] card = previewCard(widgets.get(index));
            if (mouseX >= card[0] && mouseX < card[0] + card[2] + 4 && mouseY >= card[1] && mouseY < card[1] + card[3] + 4) {
                boolean after = widgets.get(index).width() == DashboardWidget.COLUMNS ? mouseY >= card[1] + card[3] / 2.0 : mouseX >= card[0] + card[2] / 2.0;
                return after ? index + 1 : index;
            }
            if (card[1] > mouseY) return index;
        }
        return widgets.size();
    }

    private DashboardWidget widgetAt(double mouseX, double mouseY) {
        for (var widget : preferences.profile().widgets()) {
            int[] card = previewCard(widget);
            if (mouseX >= card[0] && mouseX < card[0] + card[2] && mouseY >= card[1] && mouseY < card[1] + card[3]) return widget;
        }
        return null;
    }

    // ---- Home ---------------------------------------------------------------------------------------------

    private void renderHome(GuiGraphics graphics) {
        graphics.fill(x, y + 4, x + 4, y + 8, DashboardTheme.status(state.connectionStatus()));
        text(graphics, state.networkName(), x + 10, y + 2, width - 114, DashboardTheme.TEXT);
        text(graphics, Component.translatable("screen.homelink_dashboard.home_device_total", state.devices().size(), state.totalDeviceCount()).getString(),
                x, y + 17, width - 104, DashboardTheme.MUTED);
        if (overviewHeight() > 56) {
            int cell = width / 4;
            statistic(graphics, 0, cell, "stat_online", online, DashboardTheme.ONLINE);
            statistic(graphics, 1, cell, "stat_warning", warning, DashboardTheme.WARNING);
            statistic(graphics, 2, cell, "stat_offline", offline, DashboardTheme.OFFLINE);
            statistic(graphics, 3, cell, "stat_alerts", state.alerts().size(), DashboardTheme.ACCENT);
            text(graphics, label("favorites_widgets").getString(), x, y + 72, width, DashboardTheme.MUTED);
        } else {
            text(graphics, Component.translatable("screen.homelink_dashboard.home_device_states", online, warning, offline).getString(),
                    x, y + 29, width - 104, DashboardTheme.MUTED);
            text(graphics, label("favorites_widgets").getString() + " · "
                    + Component.translatable("screen.homelink_dashboard.home_alerts", state.alerts().size()).getString(), x, y + 44, width, DashboardTheme.ACCENT);
        }
        int top = y + overviewHeight();
        if (top >= y + height) return;
        graphics.enableScissor(x, top, x + width, y + height);
        int favoriteIndex = 0;
        for (UUID favorite : preferences.profile().favorites()) {
            int cardY = top + favoriteIndex++ * FAVORITE_ROW - scroll;
            if (cardY + FAVORITE_ROW - 4 > top && cardY < y + height) renderSummary(graphics, findDevice(favorite), x, cardY, width - 4, FAVORITE_ROW - 4, true);
        }
        int gridTop = top + favoriteIndex * FAVORITE_ROW - scroll;
        for (DashboardWidget widget : preferences.profile().widgets()) {
            int cardY = gridTop + widget.y() * CELL_HEIGHT;
            int cardHeight = widget.height() * CELL_HEIGHT - 4;
            if (cardY + cardHeight <= top || cardY >= y + height) continue;
            renderWidget(graphics, widget, x + widget.x() * width / 12, cardY, widget.width() * width / 12 - 4, cardHeight);
        }
        if (preferences.profile().favorites().isEmpty() && preferences.profile().widgets().isEmpty()) {
            DashboardTheme.panel(graphics, x, top, width - 4, Math.min(64, y + height - top));
            int lineY = top + 10;
            for (var line : font.split(label("home_empty"), Math.max(16, width - 24))) {
                graphics.drawString(font, line, x + 10, lineY, DashboardTheme.MUTED, false);
                lineY += 12;
            }
        }
        graphics.disableScissor();
        int maximum = maxScroll();
        if (maximum > 0) {
            int available = height - overviewHeight();
            int thumb = Math.max(6, available * available / (available + maximum));
            int thumbY = top + (available - thumb) * scroll / maximum;
            graphics.fill(x + width - 2, top, x + width, y + height, 0xFF202224);
            graphics.fill(x + width - 2, thumbY, x + width, thumbY + thumb, 0xFF858982);
        }
    }

    /** Draws one widget card, on the home page or in the editor preview. */
    private void renderWidget(GuiGraphics graphics, DashboardWidget widget, int cardX, int cardY, int cardWidth, int cardHeight) {
        var device = findDevice(widget.deviceId());
        graphics.enableScissor(cardX, cardY, cardX + Math.max(0, cardWidth), cardY + cardHeight);
        if (widget.type() == DashboardWidget.Type.DEVICE_SUMMARY) renderSummary(graphics, device, cardX, cardY, cardWidth, cardHeight, false);
        else {
            DashboardTheme.panel(graphics, cardX, cardY, cardWidth, cardHeight);
            text(graphics, device == null ? label("device_unloaded").getString() : device.name(), cardX + 5, cardY + 5, cardWidth - 10, 0xE7E5E0);
            var metric = metric(widget);
            if (metric == null) text(graphics, label("metric_unavailable").getString(), cardX + 5, cardY + 22, cardWidth - 10, 0xAFB1AD);
            else MetricRendererRegistry.render(graphics, font, metric, cardX + 5, cardY + 19, cardWidth - 10);
        }
        graphics.disableScissor();
    }

    private void renderSummary(GuiGraphics graphics, DebugDeviceView device, int left, int top, int cardWidth, int cardHeight, boolean favorite) {
        DashboardTheme.panel(graphics, left, top, cardWidth, cardHeight);
        if (favorite && device != null) {
            String status = DashboardText.value(device.status());
            int badge = font.width(status) + 12;
            text(graphics, "★ " + device.name(), left + 7, top + 5, cardWidth - badge - 14, DashboardTheme.TEXT);
            text(graphics, status, left + cardWidth - badge, top + 5, badge - 5, DashboardTheme.status(device.status()));
            for (int index = 0; index < Math.min(2, device.metrics().size()); index++) {
                var metric = device.metrics().get(index);
                text(graphics, metric.name() + ": " + MetricRendererRegistry.localizedValue(metric), left + 7, top + 19 + index * 11,
                        cardWidth - 14, DashboardTheme.MUTED);
            }
            return;
        }
        text(graphics, (favorite ? "★ " : "") + (device == null ? label("device_unloaded").getString() : device.name()), left + 5, top + 5, cardWidth - 10, 0xE7E5E0);
        if (device == null) return;
        text(graphics, DashboardText.value(device.status()), left + 5, top + 17, cardWidth - 10, device.status().equals("ONLINE") ? 0xA1BD92 : 0xD3B16F);
        int count = Math.min(Math.max(0, (cardHeight - 29) / 11), Math.min(2, device.metrics().size()));
        for (int index = 0; index < count; index++) {
            var metric = device.metrics().get(index);
            text(graphics, metric.name() + ": " + MetricRendererRegistry.localizedValue(metric), left + 5, top + 29 + index * 11, cardWidth - 10, 0xAFB1AD);
        }
    }

    // ---- Mouse --------------------------------------------------------------------------------------------

    /**
     * Home: a click on a widget opens the editor with it selected. Editor: a press on a device or a widget
     * starts a possible drag; a click on a device's star toggles the favorite. The header and the bottom bar
     * are left to the buttons.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !inside(mouseX, mouseY)) return false;
        if (!editMode) return homeClicked(mouseX, mouseY);
        if (mouseY < y + EDITOR_TOP || mouseY >= previewBottom() && !overPalette(mouseX, mouseY)) return false;
        pressX = mouseX;
        pressY = mouseY;
        if (overPalette(mouseX, mouseY)) {
            int index = paletteScroll + (int) ((mouseY - y - EDITOR_TOP) / PALETTE_ROW);
            if (index >= state.devices().size()) return true;
            var device = state.devices().get(index);
            if (mouseX >= x + paletteWidth() - 16) { toggleFavorite(device.id()); return true; }
            selectDevice(device.id());
            if (canEdit()) dragDevice = device.id();
            return true;
        }
        if (overPreview(mouseX, mouseY)) {
            var widget = widgetAt(mouseX, mouseY);
            UUID selected = widget == null ? null : widget.id();
            if (canEdit()) dragWidget = selected;
            if (!java.util.Objects.equals(selected, widgetId)) { widgetId = selected; rebuild.run(); }
            return true;
        }
        return false;
    }

    private boolean homeClicked(double mouseX, double mouseY) {
        if (mouseY < y + overviewHeight() || !canConfigure()) return false;
        int gridTop = y + overviewHeight() + preferences.profile().favorites().size() * FAVORITE_ROW - scroll;
        for (var widget : preferences.profile().widgets()) {
            int left = x + widget.x() * width / 12;
            int top = gridTop + widget.y() * CELL_HEIGHT;
            if (mouseX >= left && mouseX < left + widget.width() * width / 12 - 4 && mouseY >= top && mouseY < top + widget.height() * CELL_HEIGHT - 4) {
                widgetId = widget.id();
                previewScroll = Math.clamp(widget.y() * CELL_HEIGHT - CELL_HEIGHT, 0, maxPreviewScroll());
                setEditMode(true);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!editMode || button != 0 || dragDevice == null && dragWidget == null) return false;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= DRAG_THRESHOLD) dragging = true;
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!editMode || button != 0 || dragDevice == null && dragWidget == null) return false;
        if (dragging && overPreview(mouseX, mouseY)) {
            int index = dropIndex(mouseX, mouseY);
            if (dragDevice != null) insert(DashboardWidget.Type.DEVICE_SUMMARY, dragDevice, "", index);
            else moveTo(ordered().indexOf(findWidget(dragWidget)), index);
        }
        dragDevice = dragWidget = null;
        dragging = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!inside(mouseX, mouseY)) return false;
        int direction = (int) Math.signum(vertical);
        if (!editMode) scroll = Math.clamp(scroll - direction * 24, 0, maxScroll());
        else if (overPalette(mouseX, mouseY)) paletteScroll = Math.clamp(paletteScroll - direction, 0, maxPaletteScroll());
        else if (overPreview(mouseX, mouseY)) previewScroll = Math.clamp(previewScroll - direction * CELL_HEIGHT, 0, maxPreviewScroll());
        return true;
    }

    // ---- Geometry and helpers -----------------------------------------------------------------------------

    private int paletteWidth() { return Math.clamp(width / 4, 100, 150); }
    private int previewLeft() { return paletteWidth() + 12; }
    private int previewBottom() { return y + height - DashboardTheme.CONTROL_HEIGHT - 4; }
    private boolean overPalette(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + paletteWidth() && mouseY >= y + EDITOR_TOP && mouseY < y + height;
    }
    private boolean overPreview(double mouseX, double mouseY) {
        return mouseX >= x + previewLeft() && mouseX < x + width && mouseY >= y + EDITOR_TOP && mouseY < previewBottom();
    }
    private int maxPaletteScroll() {
        int visible = Math.max(1, (height - EDITOR_TOP) / PALETTE_ROW);
        return Math.max(0, state.devices().size() - visible);
    }
    private int maxPreviewScroll() {
        int rows = 0;
        for (var widget : preferences.profile().widgets()) rows = Math.max(rows, widget.y() + widget.height());
        return Math.max(0, rows * CELL_HEIGHT + 6 - (previewBottom() - y - EDITOR_TOP));
    }
    private int maxScroll() {
        int rows = 0;
        for (var widget : preferences.profile().widgets()) rows = Math.max(rows, widget.y() + widget.height());
        return Math.max(0, preferences.profile().favorites().size() * FAVORITE_ROW + rows * CELL_HEIGHT - Math.max(0, height - overviewHeight()));
    }
    private int overviewHeight() { return height >= 180 && width >= 350 ? 88 : 56; }
    private void statistic(GuiGraphics graphics, int index, int cell, String key, int value, int color) {
        int left = x + index * cell;
        graphics.fill(left, y + 48, left + cell, y + 64, DashboardTheme.SURFACE);
        if (index > 0) graphics.fill(left, y + 51, left + 1, y + 61, DashboardTheme.LINE);
        graphics.fill(left + 6, y + 54, left + 9, y + 57, color);
        text(graphics, value + " " + label(key).getString(), left + 14, y + 52, cell - 20, DashboardTheme.TEXT);
    }
    private boolean inside(double mouseX, double mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
    private boolean canConfigure() { return state.role().equals("OWNER") || state.role().equals("ADMIN"); }
    private boolean canEdit() { return canConfigure() && preferences.ready() && !preferences.pending(); }
    private DebugDeviceView device() { return findDevice(deviceId); }
    private DebugDeviceView findDevice(UUID id) { return state.devices().stream().filter(device -> device.id().equals(id)).findFirst().orElse(null); }
    private DashboardWidget findWidget(UUID id) { return preferences.profile().widgets().stream().filter(widget -> widget.id().equals(id)).findFirst().orElse(null); }
    private DashboardWidget selectedWidget() { return findWidget(widgetId); }
    private DebugDeviceView.Metric metric(DashboardWidget widget) {
        var device = findDevice(widget.deviceId());
        return device == null ? null : device.metrics().stream().filter(value -> value.id().equals(widget.metricId())).findFirst().orElse(null);
    }
    /** Registers a button that changes the selected widget. */
    private void action(Button button) { actionButtons.add(button); }
    private Button button(Component title, int left, int top, int width, Runnable action) {
        Button button = DashboardButton.builder(title, ignored -> action.run()).bounds(x + left, y + top, Math.max(16, width), DashboardTheme.CONTROL_HEIGHT).build();
        button.setTooltip(Tooltip.create(title));
        addWidget.accept(button);
        return button;
    }
    private void text(GuiGraphics graphics, String text, int left, int top, int width, int color) { graphics.drawString(font, font.plainSubstrByWidth(text, Math.max(0, width)), left, top, color, false); }
    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
}
