package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.AlertView;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;
import net.minecraft.util.FormattedCharSequence;

/** Bounded, locally filtered HomeCore event history. Only visible event rows are rendered. */
public final class AlertCenterView {
    private static final List<String> FILTERS = List.of("ALL", "INFO", "WARNING", "CRITICAL");
    private static final int ROW_HEIGHT = 49;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final DashboardClientState state;
    private final Font font;
    private final Consumer<UUID> openDevice;
    private List<AlertView> filtered = List.of();
    private List<Row> rows = List.of();
    private String filter = "ALL";
    private long revision = -1;
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private Button filterButton;
    private record Row(AlertView alert, String timestamp, List<FormattedCharSequence> details) { }

    public AlertCenterView(DashboardClientState state, Font font, Consumer<UUID> openDevice) {
        this.state = state;
        this.font = font;
        this.openDevice = openDevice;
    }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        filterButton = DashboardButton.builder(filterLabel(), button -> setFilter(FILTERS.get((FILTERS.indexOf(filter) + 1) % FILTERS.size())))
                .bounds(x, y, Math.min(width, 146), DashboardTheme.CONTROL_HEIGHT).build();
        addWidget.accept(filterButton);
        tick();
        rebuild();
    }

    public void tick() {
        if (revision != state.alertRevision()) { revision = state.alertRevision(); rebuild(); }
        clampScroll();
    }

    public void setFilter(String value) {
        String normalized = value == null ? "ALL" : value.toUpperCase(Locale.ROOT);
        if (!FILTERS.contains(normalized)) normalized = "ALL";
        if (filter.equals(normalized)) return;
        filter = normalized;
        scroll = 0;
        if (filterButton != null) filterButton.setMessage(filterLabel());
        rebuild();
    }

    private void rebuild() {
        filtered = state.alerts().stream().filter(alert -> filter.equals("ALL") || filter.equals(alert.severity())).toList();
        rows = filtered.stream().map(alert -> {
            StringBuilder details = new StringBuilder(alert.sourceName()).append('\n').append(alert.type()).append('\n').append(alert.message());
            alert.data().forEach((key, value) -> details.append('\n').append(key).append(": ").append(value));
            String time;
            try { time = TIME.format(alert.timestamp()); } catch (RuntimeException exception) { time = "?"; }
            List<FormattedCharSequence> lines = font.split(Component.literal(details.toString()), Math.max(40, Math.min(250, width)));
            return new Row(alert, time, List.copyOf(lines.subList(0, Math.min(12, lines.size()))));
        }).toList();
        clampScroll();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 26) return;
        text(graphics, Component.translatable("screen.homelink_dashboard.alert_counts", filtered.size(), state.alerts().size()).getString(),
                x + 152, y + 6, Math.max(0, width - 152), 0xAFB1AD);
        int top = y + 26;
        graphics.enableScissor(x, top, x + width, y + height);
        Row hovered = null;
        int end = Math.min(rows.size(), scroll + (height - 26 + ROW_HEIGHT - 1) / ROW_HEIGHT);
        for (int index = scroll; index < end; index++) {
            Row row = rows.get(index);
            var alert = row.alert();
            int rowY = top + (index - scroll) * ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < Math.min(y + height, rowY + ROW_HEIGHT - 3);
            if (hover) hovered = row;
            graphics.fill(x, rowY, x + width - 4, rowY + ROW_HEIGHT - 3, hover ? 0xFF293945 : 0xFF252729);
            graphics.fill(x, rowY, x + 2, rowY + ROW_HEIGHT - 3, 0xFF000000 | severityColor(alert.severity()));
            text(graphics, DashboardText.value(alert.severity()) + "  " + row.timestamp(), x + 7, rowY + 4, width - 16, severityColor(alert.severity()));
            text(graphics, alert.sourceName() + " · " + alert.type(), x + 7, rowY + 17, width - 16, 0xE7E5E0);
            text(graphics, alert.message(), x + 7, rowY + 30, width - 16, 0xAFB1AD);
        }
        if (rows.isEmpty()) text(graphics, Component.translatable("screen.homelink_dashboard.no_alerts").getString(), x + 4, top + 8, width - 8, 0xAFB1AD);
        if (rows.size() > visible()) {
            int available = height - 26;
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

    public boolean selectAlert(int index) {
        if (index < 0 || index >= filtered.size()) return false;
        UUID source = filtered.get(index).sourceId();
        if (state.devices().stream().noneMatch(device -> device.id().equals(source))) return false;
        openDevice.accept(source);
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !inside(mouseX, mouseY) || mouseY < y + 26) return false;
        return selectAlert(scroll + (int) ((mouseY - y - 26) / ROW_HEIGHT));
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!inside(mouseX, mouseY) || mouseY < y + 26 || vertical == 0) return false;
        scroll -= (int) Math.signum(vertical) * 2;
        clampScroll();
        return true;
    }
    private boolean inside(double mouseX, double mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
    private int visible() { return Math.max(1, (height - 26) / ROW_HEIGHT); }
    private void clampScroll() { scroll = Math.clamp(scroll, 0, Math.max(0, filtered.size() - visible())); }
    private Component filterLabel() { return Component.translatable("screen.homelink_dashboard.alert_filter", DashboardText.component(filter)); }
    private void text(GuiGraphics graphics, String value, int left, int top, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, maxWidth)), left, top, color, false);
    }
    private static int severityColor(String severity) {
        return switch (severity) { case "CRITICAL" -> 0xD19A8F; case "WARNING" -> 0xD3B16F; default -> 0xB4B8AE; };
    }
    public List<AlertView> filteredAlerts() { return filtered; }
}
