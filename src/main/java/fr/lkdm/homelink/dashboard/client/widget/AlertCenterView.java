package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

import fr.lkdm.homelink.dashboard.client.state.AlertView;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.MachineSystems;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Bounded session history with independent severity, system and treatment filters. */
public final class AlertCenterView {
    private static final List<String> FILTERS = List.of("ALL", "INFO", "WARNING", "CRITICAL");
    private static final List<String> STATUS_FILTERS = List.of("ALL", "UNREAD", "READ", "ACTIVE", "ACKNOWLEDGED");
    private static final int ROW_HEIGHT = 49;
    private static final int LIST_TOP = 48;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final DashboardClientState state;
    private final Font font;
    private final Consumer<UUID> openDevice;
    private final List<RowControls> controls = new ArrayList<>();
    private List<AlertView> filtered = List.of();
    private List<Row> rows = List.of();
    private String filter = "ALL";
    private String statusFilter = "ALL";
    private MachineSystems.Group systemFilter;
    private long revision = -1;
    private int scroll;
    private int x, y, width, height;
    private Button filterButton, systemButton, statusButton, acknowledgeAllButton;
    private record Row(AlertView alert, String timestamp, List<FormattedCharSequence> details) { }
    private record RowControls(Button read, Button acknowledge) { }

    public AlertCenterView(DashboardClientState state, Font font, Consumer<UUID> openDevice) {
        this.state = state;
        this.font = font;
        this.openDevice = openDevice;
    }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        controls.clear();
        int column = Math.max(1, (width - 8) / 3);
        filterButton = button(filterLabel(), x, y, column, ignored -> setFilter(FILTERS.get((FILTERS.indexOf(filter) + 1) % FILTERS.size())), addWidget);
        systemButton = button(systemLabel(), x + column + 4, y, column, ignored -> {
            var groups = MachineSystems.Group.values();
            setSystemFilter(systemFilter == null ? groups[0] : systemFilter.ordinal() + 1 == groups.length ? null : groups[systemFilter.ordinal() + 1]);
        }, addWidget);
        statusButton = button(statusLabel(), x + 2 * (column + 4), y, width - 2 * (column + 4),
                ignored -> setStatusFilter(STATUS_FILTERS.get((STATUS_FILTERS.indexOf(statusFilter) + 1) % STATUS_FILTERS.size())), addWidget);
        int bulkWidth = Math.min(130, width);
        acknowledgeAllButton = button(label("alert_acknowledge_all"), x + width - bulkWidth, y + 22, bulkWidth,
                ignored -> acknowledgeAllAlerts(), addWidget);
        for (int slot = 0; slot < visible(); slot++) {
            final int rowSlot = slot;
            int left = actionX(), top = y + LIST_TOP + slot * ROW_HEIGHT;
            Button read = button(label("alert_mark_read"), left, top + 3, actionWidth(), ignored -> {
                int index = scroll + rowSlot;
                if (index < filtered.size()) markAlertRead(index, !filtered.get(index).read());
            }, addWidget);
            Button acknowledge = button(label("alert_acknowledge"), left, top + 25, actionWidth(),
                    ignored -> acknowledgeAlert(scroll + rowSlot), addWidget);
            controls.add(new RowControls(read, acknowledge));
        }
        rebuild();
    }

    private Button button(Component message, int left, int top, int buttonWidth, Button.OnPress action, Consumer<AbstractWidget> addWidget) {
        Button button = HomeLinkButton.builder(message, action).bounds(left, top, buttonWidth, HomeLinkTheme.CONTROL_HEIGHT).build();
        button.setTooltip(Tooltip.create(message));
        addWidget.accept(button);
        return button;
    }
    public void tick() { if (revision != state.alertRevision()) rebuild(); }

    public void setFilter(String value) {
        String normalized = normalize(value, FILTERS);
        if (filter.equals(normalized)) return;
        filter = normalized;
        filtersChanged();
    }
    public void setSystemFilter(MachineSystems.Group value) {
        if (systemFilter == value) return;
        systemFilter = value;
        filtersChanged();
    }
    public void setStatusFilter(String value) {
        String normalized = normalize(value, STATUS_FILTERS);
        if (statusFilter.equals(normalized)) return;
        statusFilter = normalized;
        filtersChanged();
    }
    private static String normalize(String value, List<String> accepted) {
        String normalized = value == null ? "ALL" : value.toUpperCase(Locale.ROOT);
        return accepted.contains(normalized) ? normalized : "ALL";
    }
    private void filtersChanged() {
        scroll = 0;
        if (filterButton != null) {
            message(filterButton, filterLabel());
            message(systemButton, systemLabel());
            message(statusButton, statusLabel());
        }
        rebuild();
    }
    private boolean matchesStatus(AlertView alert) {
        return switch (statusFilter) {
            case "UNREAD" -> !alert.read();
            case "READ" -> alert.read();
            case "ACTIVE" -> !alert.acknowledged();
            case "ACKNOWLEDGED" -> alert.acknowledged();
            default -> true;
        };
    }

    private void rebuild() {
        revision = state.alertRevision();
        filtered = state.alerts().stream().filter(alert -> (filter.equals("ALL") || filter.equals(alert.severity()))
                && (systemFilter == null || systemFilter == alert.system()) && matchesStatus(alert)).toList();
        rows = filtered.stream().map(alert -> {
            StringBuilder details = new StringBuilder(alert.sourceName()).append('\n').append(groupLabel(alert.system()).getString())
                    .append('\n').append(alert.type()).append('\n').append(label(alert.read() ? "alert_read" : "alert_unread").getString())
                    .append(" · ").append(label(alert.acknowledged() ? "alert_acknowledged" : "alert_active").getString()).append('\n').append(alert.message());
            alert.data().forEach((key, value) -> details.append('\n').append(key).append(": ").append(value));
            String time;
            try { time = TIME.format(alert.timestamp()); } catch (RuntimeException exception) { time = "?"; }
            List<FormattedCharSequence> lines = font.split(Component.literal(details.toString()), Math.max(40, Math.min(250, width)));
            return new Row(alert, time, List.copyOf(lines.subList(0, Math.min(12, lines.size()))));
        }).toList();
        clampScroll();
        updateControls();
    }
    private void updateControls() {
        if (acknowledgeAllButton != null) acknowledgeAllButton.active = state.activeAlertCount() > 0 || state.unreadAlertCount() > 0;
        for (int slot = 0; slot < controls.size(); slot++) {
            RowControls buttons = controls.get(slot);
            int index = scroll + slot;
            buttons.read().visible = buttons.acknowledge().visible = index < filtered.size();
            if (index >= filtered.size()) continue;
            AlertView alert = filtered.get(index);
            message(buttons.read(), label(alert.read() ? "alert_mark_unread" : "alert_mark_read"));
            message(buttons.acknowledge(), label(alert.acknowledged() ? "alert_acknowledged" : "alert_acknowledge"));
            buttons.acknowledge().active = !alert.acknowledged();
        }
    }
    private static void message(Button button, Component value) {
        button.setMessage(value);
        button.setTooltip(Tooltip.create(value));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= LIST_TOP) return;
        Component counts = Component.translatable("screen.homelink_dashboard.alert_active_counts", state.activeAlertCount(), state.unreadAlertCount());
        text(graphics, counts.getString(), x, y + 27, Math.max(0, width - Math.min(130, width) - 6), HomeLinkTheme.ACCENT);
        if (mouseX >= x && mouseX < x + width - 136 && mouseY >= y + 22 && mouseY < y + 40)
            graphics.renderTooltip(font, Component.translatable("screen.homelink_dashboard.alert_counts", filtered.size(), state.alerts().size()), mouseX, mouseY);
        int top = y + LIST_TOP;
        graphics.enableScissor(x, top, x + width, y + height);
        Row hovered = null;
        int end = Math.min(rows.size(), scroll + visible());
        for (int index = scroll; index < end; index++) {
            Row row = rows.get(index);
            AlertView alert = row.alert();
            int rowY = top + (index - scroll) * ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < actionX() - 4 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 3;
            if (hover) hovered = row;
            graphics.fill(x, rowY, x + width - 4, rowY + ROW_HEIGHT - 3, hover ? 0xFF293945 : HomeLinkTheme.SURFACE);
            int color = alert.acknowledged() ? HomeLinkTheme.MUTED : severityColor(alert.severity());
            graphics.fill(x, rowY, x + 2, rowY + ROW_HEIGHT - 3, color);
            int textWidth = Math.max(0, actionX() - x - 14);
            String readMarker = alert.read() ? "" : "● ";
            text(graphics, readMarker + DashboardText.value(alert.severity()) + "  " + row.timestamp(), x + 7, rowY + 4, textWidth, color);
            text(graphics, alert.sourceName() + " · " + groupLabel(alert.system()).getString(), x + 7, rowY + 17, textWidth, HomeLinkTheme.TEXT);
            text(graphics, alert.message(), x + 7, rowY + 30, textWidth, HomeLinkTheme.MUTED);
        }
        if (rows.isEmpty()) text(graphics, label("no_alerts").getString(), x + 4, top + 8, width - 8, HomeLinkTheme.MUTED);
        if (rows.size() > visible() && visible() > 0) {
            int available = height - LIST_TOP;
            int thumb = Math.max(6, available * visible() / rows.size());
            int thumbY = top + (available - thumb) * scroll / Math.max(1, rows.size() - visible());
            graphics.fill(x + width - 2, top, x + width, y + height, 0xFF202224);
            graphics.fill(x + width - 2, thumbY, x + width, thumbY + thumb, 0xFF858982);
        }
        graphics.disableScissor();
        if (hovered != null) {
            int lines = Math.min(hovered.details().size(), Math.max(1, (graphics.guiHeight() - 20) / font.lineHeight));
            graphics.renderTooltip(font, hovered.details().subList(0, lines), mouseX, mouseY);
        }
    }

    public boolean markAlertRead(int index, boolean read) {
        if (index < 0 || index >= filtered.size()) return false;
        boolean changed = state.setAlertRead(filtered.get(index).id(), read);
        if (changed) rebuild();
        return changed;
    }
    public boolean acknowledgeAlert(int index) {
        if (index < 0 || index >= filtered.size()) return false;
        boolean changed = state.acknowledgeAlert(filtered.get(index).id());
        if (changed) rebuild();
        return changed;
    }
    /** Applies to all retained events, including those hidden by filters. */
    public int acknowledgeAllAlerts() {
        int changed = state.acknowledgeAllAlerts();
        if (changed > 0) rebuild();
        return changed;
    }
    public boolean selectAlert(int index) {
        if (index < 0 || index >= filtered.size()) return false;
        AlertView alert = filtered.get(index);
        state.setAlertRead(alert.id(), true);
        rebuild();
        if (state.devices().stream().noneMatch(device -> device.id().equals(alert.sourceId()))) return false;
        openDevice.accept(alert.sourceId());
        return true;
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !inside(mouseX, mouseY) || mouseY < y + LIST_TOP || mouseX >= actionX() - 4) return false;
        int slot = (int) ((mouseY - y - LIST_TOP) / ROW_HEIGHT);
        if (slot >= visible() || (mouseY - y - LIST_TOP) % ROW_HEIGHT >= ROW_HEIGHT - 3) return false;
        int index = scroll + slot;
        if (index >= filtered.size()) return false;
        selectAlert(index);
        return true;
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!inside(mouseX, mouseY) || mouseY < y + LIST_TOP || vertical == 0) return false;
        scroll -= (int) Math.signum(vertical) * 2;
        clampScroll();
        updateControls();
        return true;
    }
    public boolean keyPressed(int key) {
        int page = Math.max(1, visible());
        switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> scroll -= page;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> scroll += page;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> scroll = 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> scroll = filtered.size();
            default -> { return false; }
        }
        clampScroll();
        updateControls();
        return true;
    }
    private int actionWidth() { return Math.min(110, Math.max(32, width / 3)); }
    private int actionX() { return x + width - actionWidth() - 8; }
    private boolean inside(double mouseX, double mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
    private int visible() { return Math.max(0, (height - LIST_TOP) / ROW_HEIGHT); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, filtered.size() - Math.max(1, visible()))); }
    private Component filterLabel() { return Component.translatable("screen.homelink_dashboard.alert_filter", DashboardText.component(filter)); }
    private Component systemLabel() { return Component.translatable("screen.homelink_dashboard.alert_system_filter", systemFilter == null ? DashboardText.component("ALL") : groupLabel(systemFilter)); }
    private Component statusLabel() { return Component.translatable("screen.homelink_dashboard.alert_status_filter", label("alert_status_" + statusFilter.toLowerCase(Locale.ROOT))); }
    private static Component groupLabel(MachineSystems.Group group) { return label("machines_group_" + group.key()); }
    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
    private void text(GuiGraphics graphics, String value, int left, int top, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, maxWidth)), left, top, color, false);
    }
    private static int severityColor(String severity) {
        return switch (severity) { case "CRITICAL" -> HomeLinkTheme.OFFLINE; case "WARNING" -> HomeLinkTheme.WARNING; default -> HomeLinkTheme.MUTED; };
    }
    public List<AlertView> filteredAlerts() { return filtered; }
}
