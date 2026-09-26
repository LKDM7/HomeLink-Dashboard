package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/** Local, cached device search and a virtualized list/detail viewport. */
public final class DeviceExplorerView {
    private static final List<String> STATUSES = List.of("ALL", "ONLINE", "WARNING", "OFFLINE", "ERROR", "UNKNOWN");
    private static final int LIST_ROW = 44;
    private static final int METRIC_ROW = MetricRendererRegistry.HEIGHT;
    private final DashboardClientState state;
    private final Font font;
    private final List<DebugDeviceView> filtered = new ArrayList<>();
    private final List<DebugDeviceView> filteredView = Collections.unmodifiableList(filtered);
    private final Map<UUID, Integer> filteredPositions = new HashMap<>();
    private long revision = -1;
    private long structureRevision = -1;
    private long filterBuildCount;
    private String search = "";
    private String statusFilter = "ALL";
    private UUID selected;
    private DebugDeviceView selectedView;
    private boolean removedDevice;
    private int x;
    private int y;
    private int width;
    private int height;
    private int listScroll;
    private int metricScroll;
    private EditBox searchBox;
    private Button filterButton;
    private Button backButton;

    public DeviceExplorerView(DashboardClientState state, Font font) {
        this.state = state;
        this.font = font;
    }

    /** Widgets are owned by the screen and replaced by its normal init lifecycle. */
    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x;
        this.y = y;
        this.width = Math.max(32, width);
        this.height = Math.max(20, height);
        int filterWidth = Math.min(94, this.width / 2);
        searchBox = DashboardTheme.input(new EditBox(font, x, y, Math.max(16, this.width - filterWidth - 6), DashboardTheme.CONTROL_HEIGHT, label("search")));
        searchBox.setMaxLength(128);
        searchBox.setTextColor(DashboardTheme.TEXT);
        searchBox.setHint(label("search"));
        searchBox.setValue(search);
        searchBox.setResponder(this::setSearch);
        addWidget.accept(searchBox);
        filterButton = DashboardButton.builder(filterLabel(), button -> {
            int current = STATUSES.indexOf(statusFilter);
            setStatusFilter(STATUSES.get((current + 1) % STATUSES.size()));
        }).bounds(x + this.width - filterWidth, y, filterWidth, DashboardTheme.CONTROL_HEIGHT).build();
        addWidget.accept(filterButton);
        backButton = DashboardButton.builder(label("back_devices"), button -> back())
                .bounds(x, y, Math.min(this.width, 140), DashboardTheme.CONTROL_HEIGHT).build();
        addWidget.accept(backButton);
        tick();
        updateVisibility();
    }

    public void tick() {
        if (revision != state.revision()) {
            revision = state.revision();
            if (structureRevision != state.structureRevision()) {
                structureRevision = state.structureRevision();
                rebuild();
            } else {
                for (var latest : state.devices()) {
                    Integer index = filteredPositions.get(latest.id());
                    if (index != null) filtered.set(index, latest);
                }
            }
            if (selected != null) {
                selectedView = state.devices().stream().filter(device -> device.id().equals(selected)).findFirst().orElse(null);
                if (selectedView == null) {
                    selected = null;
                    removedDevice = true;
                    updateVisibility();
                }
            }
        }
        clampScroll();
    }

    public void setSearch(String text) {
        String next = text == null ? "" : text.substring(0, Math.min(128, text.length()));
        if (search.equals(next)) return;
        search = next;
        listScroll = 0;
        if (searchBox != null && !searchBox.getValue().equals(next)) searchBox.setValue(next);
        rebuild();
    }

    public void setStatusFilter(String status) {
        String next = status == null ? "ALL" : status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(next)) next = "UNKNOWN";
        if (statusFilter.equals(next)) return;
        statusFilter = next;
        listScroll = 0;
        if (filterButton != null) filterButton.setMessage(filterLabel());
        rebuild();
    }

    private void rebuild() {
        String query = search.strip().toLowerCase(Locale.ROOT);
        List<DebugDeviceView> matching = state.devices().stream().filter(device -> matchesStatus(device.status()))
                .filter(device -> query.isEmpty() || device.searchText().contains(query))
                .sorted(Comparator.comparing(DebugDeviceView::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(device -> device.id().toString())).toList();
        filtered.clear();
        filtered.addAll(matching);
        filteredPositions.clear();
        for (int index = 0; index < filtered.size(); index++) filteredPositions.put(filtered.get(index).id(), index);
        filterBuildCount++;
        clampScroll();
    }

    private boolean matchesStatus(String status) {
        String normalized = status.toUpperCase(Locale.ROOT);
        if (statusFilter.equals("ALL")) return true;
        if (statusFilter.equals("UNKNOWN")) return !STATUSES.subList(1, 5).contains(normalized);
        return statusFilter.equals(normalized);
    }

    public boolean select(UUID id) {
        DebugDeviceView found = state.devices().stream().filter(device -> device.id().equals(id)).findFirst().orElse(null);
        if (found == null) return false;
        selected = id;
        selectedView = found;
        metricScroll = 0;
        removedDevice = false;
        updateVisibility();
        return true;
    }

    public void back() {
        selected = null;
        selectedView = null;
        updateVisibility();
    }

    private void updateVisibility() {
        if (searchBox == null) return;
        boolean details = showingDetails();
        searchBox.visible = !details;
        searchBox.active = !details;
        if (details) searchBox.setFocused(false);
        filterButton.visible = !details;
        filterButton.active = !details;
        backButton.visible = details;
        backButton.active = details;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 23) return;
        graphics.enableScissor(x, y + 23, x + width, y + height);
        if (showingDetails() && selectedView != null) renderDetails(graphics);
        else renderList(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        String notice = removedDevice ? label("device_removed").getString()
                : Component.translatable("screen.homelink_dashboard.devices_filtered", filtered.size(), state.devices().size()).getString();
        text(graphics, notice, x, y + 26, width, removedDevice ? 0xD3B16F : 0xA4A7A1);
        int top = y + 40;
        int available = Math.max(0, height - 40);
        int end = Math.min(filtered.size(), listScroll + (available + LIST_ROW - 1) / LIST_ROW);
        for (int index = listScroll; index < end; index++) {
            DebugDeviceView device = filtered.get(index);
            int rowY = top + (index - listScroll) * LIST_ROW;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + LIST_ROW - 3;
            graphics.fill(x, rowY, x + width - 4, rowY + LIST_ROW - 3, hover ? DashboardTheme.HOVER : DashboardTheme.SURFACE);
            graphics.fill(x, rowY, x + 2, rowY + LIST_ROW - 3, 0xFF000000 | statusColor(device.status()));
            graphics.renderOutline(x + 8, rowY + 7, 16, 16, DashboardTheme.LINE);
            graphics.fill(x + 12, rowY + 11, x + 20, rowY + 13, DashboardTheme.ACCENT);
            graphics.fill(x + 12, rowY + 17, x + 17, rowY + 19, DashboardTheme.MUTED);
            int badgeWidth = font.width(DashboardText.value(device.status())) + 12;
            text(graphics, device.name(), x + 32, rowY + 5, width - badgeWidth - 48, DashboardTheme.TEXT);
            text(graphics, device.type(), x + 32, rowY + 18, width - 46, DashboardTheme.MUTED);
            text(graphics, DashboardText.value(device.status()), x + width - badgeWidth, rowY + 5, badgeWidth - 6, statusColor(device.status()));
            for (int metricIndex = 0; metricIndex < Math.min(2, device.metrics().size()); metricIndex++) {
                var metric = device.metrics().get(metricIndex);
                int columnWidth = (width - 18) / Math.min(2, device.metrics().size());
                text(graphics, metric.name() + ": " + MetricRendererRegistry.localizedValue(metric), x + 8 + metricIndex * columnWidth,
                        rowY + 29, columnWidth - 6, 0xAFB1AD);
            }
        }
        if (filtered.isEmpty()) text(graphics, label("no_matching_devices").getString(), x + 4, top + 8, width - 8, 0xAFB1AD);
        scrollbar(graphics, top, available, listScroll, filtered.size(), listVisible());
    }

    private void renderDetails(GuiGraphics graphics) {
        DebugDeviceView device = selectedView;
        DashboardTheme.panel(graphics, x, y + 24, width - 4, 51);
        graphics.fill(x, y + 24, x + 2, y + 75, statusColor(device.status()) | 0xFF000000);
        text(graphics, device.name(), x + 8, y + 29, width - 20, DashboardTheme.TEXT);
        text(graphics, device.type(), x + 8, y + 41, width - 20, DashboardTheme.MUTED);
        text(graphics, DashboardText.value(device.status()), x + 8, y + 53, width - 20, statusColor(device.status()));
        text(graphics, device.position().isBlank() ? label("position_unavailable").getString() : device.position(), x + 8, y + 65, width - 20, DashboardTheme.MUTED);
        int top = y + 80;
        int available = Math.max(0, height - 80);
        if (available == 0) return;
        graphics.enableScissor(x, top, x + width, y + height);
        int end = Math.min(device.metrics().size(), metricScroll + (available + METRIC_ROW - 1) / METRIC_ROW);
        for (int index = metricScroll; index < end; index++) {
            MetricRendererRegistry.render(graphics, font, device.metrics().get(index), x, top + (index - metricScroll) * METRIC_ROW, width - 8);
        }
        if (device.metrics().isEmpty()) text(graphics, label("no_metrics").getString(), x + 4, top + 4, width - 8, 0xAFB1AD);
        scrollbar(graphics, top, available, metricScroll, device.metrics().size(), metricVisible());
        graphics.disableScissor();
    }

    private void scrollbar(GuiGraphics graphics, int top, int available, int offset, int count, int visible) {
        if (count <= visible || available <= 0) return;
        int thumb = Math.max(6, available * visible / count);
        int thumbY = top + (available - thumb) * offset / Math.max(1, count - visible);
        graphics.fill(x + width - 2, top, x + width, top + available, 0xFF202224);
        graphics.fill(x + width - 2, thumbY, x + width, thumbY + thumb, 0xFF858982);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || showingDetails() || !inside(mouseX, mouseY) || mouseY < y + 40) return false;
        int index = listScroll + (int) ((mouseY - y - 40) / LIST_ROW);
        return index >= 0 && index < filtered.size() && select(filtered.get(index).id());
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!inside(mouseX, mouseY) || mouseY < y + 23 || vertical == 0) return false;
        int movement = (int) Math.signum(vertical) * 2;
        if (showingDetails()) metricScroll -= movement;
        else listScroll -= movement;
        clampScroll();
        return true;
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int listVisible() { return Math.max(1, (height - 40) / LIST_ROW); }
    private int metricVisible() { return Math.max(1, (height - 80) / METRIC_ROW); }
    private void clampScroll() {
        listScroll = Math.clamp(listScroll, 0, Math.max(0, filtered.size() - listVisible()));
        metricScroll = Math.clamp(metricScroll, 0, Math.max(0, selectedView == null ? 0 : selectedView.metrics().size() - metricVisible()));
    }

    private void text(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, maxWidth)), x, y, color, false);
    }

    private static int statusColor(String status) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ONLINE" -> 0xA1BD92;
            case "WARNING" -> 0xD3B16F;
            case "OFFLINE", "ERROR" -> 0xD19A8F;
            default -> 0xA4A7A1;
        };
    }

    private Component filterLabel() { return Component.translatable("screen.homelink_dashboard.filter_status", DashboardText.component(statusFilter)); }
    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
    public List<DebugDeviceView> filteredDevices() { return filteredView; }
    public long filterBuildCount() { return filterBuildCount; }
    public Optional<UUID> selectedDeviceId() { return Optional.ofNullable(selected); }
    public Optional<DebugDeviceView> selectedDevice() { return Optional.ofNullable(selectedView); }
    public boolean showingDetails() { return selected != null; }
}
