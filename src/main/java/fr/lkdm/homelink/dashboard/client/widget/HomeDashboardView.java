package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/** Personal HomeCore favorites and a persisted twelve-column dashboard, with a compact editor. */
public final class HomeDashboardView {
    private static final int CELL_HEIGHT = 20;
    private static final int FAVORITE_ROW = 48;
    private final DashboardClientState state;
    private final DashboardPreferencesClient preferences;
    private final Font font;
    private final List<Button> mutationButtons = new ArrayList<>();
    private Consumer<AbstractWidget> addWidget;
    private Runnable rebuild = () -> { };
    private UUID deviceId;
    private UUID widgetId;
    private int metricIndex;
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean editMode;
    private boolean arrangeMode;
    private long revision = -1;
    private long deviceRevision = -1;
    private int online;
    private int warning;
    private int offline;
    private String localError = "";

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
        mutationButtons.clear();
        ensureSelection();
        if (!editMode) {
            Button edit = button("edit_dashboard", width - 96, 0, 96, () -> setEditMode(true));
            edit.active = canConfigure();
            button("favorites_edit", width - 96, 24, 96, () -> { editMode = true; arrangeMode = false; rebuild.run(); });
        } else {
            button(arrangeMode ? "add_widgets" : "arrange_widgets", 0, 0, Math.min(150, width - 78), () -> { arrangeMode = !arrangeMode; rebuild.run(); });
            button("done", width - 70, 0, 70, () -> setEditMode(false));
            if (arrangeMode) initArrange(); else initAdd();
        }
        revision = preferences.revision();
        updateButtons();
    }

    private void initAdd() {
        buttonText(device() == null ? label("no_matching_devices") : Component.literal(device().name()), 0, 26, width, this::nextDevice);
        var current = device();
        Component metric = current == null || current.metrics().isEmpty() ? label("no_metrics")
                : Component.literal(current.metrics().get(Math.floorMod(metricIndex, current.metrics().size())).name());
        buttonText(metric, 0, 52, width, () -> { metricIndex++; rebuild.run(); });
        mutation(button("add_summary", 0, 78, (width - 4) / 2, this::addSummary));
        mutation(button("add_metric", (width + 4) / 2, 78, (width - 4) / 2, this::addMetric));
        Button favorite = button(deviceId != null && preferences.profile().favorites().contains(deviceId) ? "unfavorite" : "favorite", 0, 104, Math.min(160, width), this::toggleFavorite);
        favorite.active = device() != null && preferences.ready() && !preferences.pending();
    }

    private void initArrange() {
        DashboardWidget widget = selectedWidget();
        buttonText(widget == null ? label("no_widgets") : Component.literal(DashboardText.value(widget.type().name()) + " " + (preferences.profile().widgets().indexOf(widget) + 1)),
                0, 26, width, () -> {
                    var list = preferences.profile().widgets();
                    if (!list.isEmpty()) widgetId = list.get(Math.floorMod(list.indexOf(selectedWidget()) + 1, list.size())).id();
                    rebuild.run();
                });
        int part = (width - 12) / 4;
        mutation(buttonText(Component.literal("←"), 0, 52, part, () -> moveSelected(-1, 0)));
        mutation(buttonText(Component.literal("→"), part + 4, 52, part, () -> moveSelected(1, 0)));
        mutation(buttonText(Component.literal("↑"), (part + 4) * 2, 52, part, () -> moveSelected(0, -1)));
        mutation(buttonText(Component.literal("↓"), (part + 4) * 3, 52, part, () -> moveSelected(0, 1)));
        mutation(button("resize_widget", 0, 78, (width - 4) / 2, this::resizeSelected));
        mutation(button("remove_widget", (width + 4) / 2, 78, (width - 4) / 2, this::removeSelected));
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
    }

    private void updateButtons() {
        boolean enabled = canConfigure() && preferences.ready() && !preferences.pending();
        for (Button button : mutationButtons) button.active = enabled && (arrangeMode ? selectedWidget() != null : device() != null);
    }

    private void ensureSelection() {
        if (device() == null && !state.devices().isEmpty()) deviceId = state.devices().getFirst().id();
        if (selectedWidget() == null && !preferences.profile().widgets().isEmpty()) widgetId = preferences.profile().widgets().getFirst().id();
    }

    private void nextDevice() {
        var devices = state.devices();
        if (devices.isEmpty()) return;
        int index = devices.indexOf(device());
        selectDevice(devices.get(Math.floorMod(index + 1, devices.size())).id());
        rebuild.run();
    }

    public void selectDevice(UUID id) { deviceId = id; metricIndex = 0; }
    public void selectWidget(UUID id) { widgetId = id; }
    public void setEditMode(boolean value) { editMode = value; arrangeMode = false; rebuild.run(); }
    public void toggleFavorite() { if (device() != null && preferences.ready() && !preferences.pending()) preferences.toggleFavorite(deviceId); }

    public void addSummary() { add(DashboardWidget.Type.DEVICE_SUMMARY, ""); }
    public void addMetric() {
        var device = device();
        if (device != null && !device.metrics().isEmpty()) add(DashboardWidget.Type.METRIC,
                device.metrics().get(Math.floorMod(metricIndex, device.metrics().size())).id());
    }
    private void add(DashboardWidget.Type type, String metric) {
        if (!canConfigure() || !preferences.ready() || preferences.pending() || device() == null) return;
        var widgets = new ArrayList<>(preferences.profile().widgets());
        if (widgets.size() >= 32) { localError = "WIDGET_LIMIT"; return; }
        for (int row = 0; row <= 61; row++) for (int column = 0; column <= 6; column++) {
            DashboardWidget candidate = new DashboardWidget(UUID.randomUUID(), type, deviceId, metric, column, row, 6, 3);
            if (fits(candidate, widgets)) {
                widgets.add(candidate);
                widgetId = candidate.id();
                localError = "";
                preferences.saveLayout(widgets);
                return;
            }
        }
        localError = "GRID_FULL";
    }

    public void moveSelected(int dx, int dy) {
        var widget = selectedWidget();
        if (widget != null) {
            long nextX = (long) widget.x() + dx;
            long nextY = (long) widget.y() + dy;
            if (nextX < 0 || nextY < 0 || nextX + widget.width() > 12 || nextY + widget.height() > 64) { localError = "INVALID_POSITION"; return; }
            replace(new DashboardWidget(widget.id(), widget.type(), widget.deviceId(), widget.metricId(),
                    (int) nextX, (int) nextY, widget.width(), widget.height()));
        }
    }
    public void resizeSelected() {
        var widget = selectedWidget();
        if (widget != null) {
            int nextWidth = widget.width() == 12 ? 6 : 12;
            if (widget.y() + 3 > 64) { localError = "INVALID_POSITION"; return; }
            replace(new DashboardWidget(widget.id(), widget.type(), widget.deviceId(), widget.metricId(), Math.min(widget.x(), 12 - nextWidth), widget.y(), nextWidth, 3));
        }
    }
    private void replace(DashboardWidget candidate) {
        if (!canConfigure() || !preferences.ready() || preferences.pending()) return;
        var widgets = new ArrayList<>(preferences.profile().widgets());
        widgets.removeIf(widget -> widget.id().equals(candidate.id()));
        if (!fits(candidate, widgets)) { localError = "INVALID_POSITION"; return; }
        widgets.add(candidate);
        localError = "";
        preferences.saveLayout(widgets);
    }
    public void removeSelected() {
        if (!canConfigure() || !preferences.ready() || preferences.pending() || selectedWidget() == null) return;
        var widgets = new ArrayList<>(preferences.profile().widgets());
        widgets.removeIf(widget -> widget.id().equals(widgetId));
        preferences.saveLayout(widgets);
    }

    private static boolean fits(DashboardWidget candidate, List<DashboardWidget> existing) {
        if (candidate.x() < 0 || candidate.y() < 0 || candidate.x() + candidate.width() > 12 || candidate.y() + candidate.height() > 64) return false;
        for (var other : existing) if (candidate.x() < other.x() + other.width() && candidate.x() + candidate.width() > other.x()
                && candidate.y() < other.y() + other.height() && candidate.y() + candidate.height() > other.y()) return false;
        return true;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 0) return;
        graphics.enableScissor(x, y, x + width, y + height);
        if (editMode) {
            if (arrangeMode && selectedWidget() != null) {
                var widget = selectedWidget();
                text(graphics, widget.x() + "/" + widget.y() + " · " + widget.width() + "×" + widget.height(), x, y + 106, width / 2, 0xAFB1AD);
            }
            String result = !localError.isEmpty() ? localError : preferences.pending() ? "…" : preferences.result();
            text(graphics, DashboardText.message(result), x + width / 2, y + 108, width / 2, 0xD3B16F);
        } else renderHome(graphics);
        graphics.disableScissor();
    }

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
            int cardX = x + widget.x() * width / 12;
            int cardWidth = widget.width() * width / 12 - 4;
            var device = findDevice(widget.deviceId());
            graphics.enableScissor(cardX, cardY, cardX + Math.max(0, cardWidth), cardY + cardHeight);
            if (widget.type() == DashboardWidget.Type.DEVICE_SUMMARY) renderSummary(graphics, device, cardX, cardY, cardWidth, cardHeight, false);
            else {
                DashboardTheme.panel(graphics, cardX, cardY, cardWidth, cardHeight);
                text(graphics, device == null ? label("device_unloaded").getString() : device.name(), cardX + 5, cardY + 5, cardWidth - 10, 0xE7E5E0);
                var metric = device == null ? null : device.metrics().stream().filter(value -> value.id().equals(widget.metricId())).findFirst().orElse(null);
                if (metric == null) text(graphics, label("metric_unavailable").getString(), cardX + 5, cardY + 22, cardWidth - 10, 0xAFB1AD);
                else MetricRendererRegistry.render(graphics, font, metric, cardX + 5, cardY + 19, cardWidth - 10);
            }
            graphics.disableScissor();
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

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editMode || button != 0 || !inside(mouseX, mouseY) || mouseY < y + overviewHeight() || !canConfigure()) return false;
        int gridTop = y + overviewHeight() + preferences.profile().favorites().size() * FAVORITE_ROW - scroll;
        for (var widget : preferences.profile().widgets()) {
            int left = x + widget.x() * width / 12;
            int top = gridTop + widget.y() * CELL_HEIGHT;
            if (mouseX >= left && mouseX < left + widget.width() * width / 12 - 4 && mouseY >= top && mouseY < top + widget.height() * CELL_HEIGHT - 4) {
                widgetId = widget.id();
                editMode = arrangeMode = true;
                rebuild.run();
                return true;
            }
        }
        return false;
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (editMode || !inside(mouseX, mouseY)) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 24, 0, maxScroll());
        return true;
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
    private DebugDeviceView device() { return findDevice(deviceId); }
    private DebugDeviceView findDevice(UUID id) { return state.devices().stream().filter(device -> device.id().equals(id)).findFirst().orElse(null); }
    private DashboardWidget selectedWidget() { return preferences.profile().widgets().stream().filter(widget -> widget.id().equals(widgetId)).findFirst().orElse(null); }
    private void mutation(Button button) { mutationButtons.add(button); }
    private Button button(String key, int left, int top, int width, Runnable action) { return buttonText(label(key), left, top, width, action); }
    private Button buttonText(Component title, int left, int top, int width, Runnable action) {
        Button button = DashboardButton.builder(title, ignored -> action.run()).bounds(x + left, y + top, Math.max(16, width), 20).build();
        addWidget.accept(button);
        return button;
    }
    private void text(GuiGraphics graphics, String text, int left, int top, int width, int color) { graphics.drawString(font, font.plainSubstrByWidth(text, Math.max(0, width)), left, top, color, false); }
    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
}
