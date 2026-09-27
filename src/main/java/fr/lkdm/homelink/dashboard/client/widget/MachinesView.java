package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.MachineSystems;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Machine overview grouped by HomeLink system: side navigation, one summary card per system and per-system machine lists. */
public final class MachinesView {
    private static final int CARD_HEIGHT = 68;
    private static final int ROW_HEIGHT = 30;
    private static final int GAP = 4;
    private final DashboardClientState state;
    private final Font font;
    private final Consumer<UUID> openDevice;
    private Map<MachineSystems.Group, MachineSystems.Summary> summaries = Map.of();
    private MachineSystems.Group selected;
    private Runnable rebuild = () -> { };
    private long revision = -1;
    private String navigationSignature = "";
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;

    public MachinesView(DashboardClientState state, Font font, Consumer<UUID> openDevice) {
        this.state = state;
        this.font = font;
        this.openDevice = openDevice;
    }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget, Runnable rebuild) {
        this.x = x;
        this.y = y;
        this.width = Math.max(64, width);
        this.height = Math.max(20, height);
        this.rebuild = rebuild;
        refresh();
        if (selected != null && !summaries.containsKey(selected)) selected = null;
        navigationSignature = signature();
        int buttonWidth = sidebarWidth();
        int top = 0;
        addWidget.accept(navigationButton(label("machines_overview"), null, top, buttonWidth));
        for (var summary : summaries.values()) {
            top += DashboardTheme.CONTROL_HEIGHT + GAP;
            if (top + DashboardTheme.CONTROL_HEIGHT > this.height) break;
            addWidget.accept(navigationButton(Component.translatable("screen.homelink_dashboard.machines_nav",
                    groupName(summary.group()), summary.total()), summary.group(), top, buttonWidth));
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private AbstractWidget navigationButton(Component label, MachineSystems.Group target, int top, int buttonWidth) {
        var button = DashboardButton.builder(label, ignored -> select(target))
                .bounds(x, y + top, buttonWidth, DashboardTheme.CONTROL_HEIGHT).build();
        ((DashboardButton) button).navigation(selected == target);
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        return button;
    }

    public void select(MachineSystems.Group group) {
        if (selected == group) return;
        selected = group;
        scroll = 0;
        rebuild.run();
    }

    public MachineSystems.Group selected() { return selected; }
    public Map<MachineSystems.Group, MachineSystems.Summary> summaries() { return summaries; }

    public void tick() {
        refresh();
        // Machine counts are part of the navigation labels; rebuild the buttons only when they change.
        if (!navigationSignature.equals(signature())) rebuild.run();
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private void refresh() {
        if (revision == state.revision()) return;
        revision = state.revision();
        summaries = MachineSystems.summarize(state.devices());
    }

    private String signature() {
        var builder = new StringBuilder();
        summaries.values().forEach(summary -> builder.append(summary.group().ordinal()).append(':').append(summary.total()).append(';'));
        return builder.toString();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = contentX(), right = x + width;
        graphics.fill(left - 5, y, left - 4, y + height, DashboardTheme.LINE);
        graphics.enableScissor(left, y, right, y + height);
        if (selected == null) renderOverview(graphics, mouseX, mouseY);
        else renderGroup(graphics, summaries.get(selected), mouseX, mouseY);
        graphics.disableScissor();
        renderScrollbar(graphics);
    }

    private void renderOverview(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = contentX(), available = contentWidth();
        String heading = Component.translatable("screen.homelink_dashboard.machines_heading", state.devices().size()).getString();
        if (state.truncated()) heading += "  ·  " + label("machines_truncated").getString();
        text(graphics, heading, left, y + 2 - scroll, available, DashboardTheme.MUTED);
        int columns = columns();
        int cardWidth = (available - GAP * (columns - 1)) / columns;
        int index = 0;
        for (var summary : summaries.values()) {
            int cardX = left + (index % columns) * (cardWidth + GAP);
            int cardY = cardsTop() + (index / columns) * (CARD_HEIGHT + GAP) - scroll;
            index++;
            if (cardY + CARD_HEIGHT < y || cardY > y + height) continue;
            renderCard(graphics, summary, cardX, cardY, cardWidth, hovered(mouseX, mouseY, cardX, cardY, cardWidth, CARD_HEIGHT));
        }
    }

    private void renderCard(GuiGraphics graphics, MachineSystems.Summary summary, int left, int top, int cardWidth, boolean hover) {
        DashboardTheme.panel(graphics, left, top, cardWidth, CARD_HEIGHT);
        if (hover) graphics.renderOutline(left, top, cardWidth, CARD_HEIGHT, DashboardTheme.ACCENT);
        lamp(graphics, left + 6, top + 6, summary.status());
        String count = Component.translatable("screen.homelink_dashboard.machines_count", summary.total()).getString();
        int countWidth = font.width(count);
        text(graphics, groupName(summary.group()).getString(), left + 15, top + 5, cardWidth - countWidth - 26, DashboardTheme.TEXT);
        text(graphics, count, left + cardWidth - countWidth - 6, top + 5, countWidth, DashboardTheme.MUTED);
        if (summary.total() == 0) {
            int lineY = top + 20;
            for (var line : font.split(Component.translatable("screen.homelink_dashboard.machines_none_" + summary.group().key()),
                    Math.max(16, cardWidth - 12))) {
                if (lineY > top + CARD_HEIGHT - 10) break;
                graphics.drawString(font, line, left + 6, lineY, DashboardTheme.MUTED, false);
                lineY += 11;
            }
            return;
        }
        statusCounts(graphics, summary, left + 6, top + 18, cardWidth - 12);
        int lineY = top + 31;
        for (var line : summary.lines()) {
            if (lineY + 9 > top + CARD_HEIGHT) break;
            summaryLine(graphics, line, left + 6, lineY, cardWidth - 12);
            lineY += line.fraction() >= 0 ? 14 : 11;
        }
    }

    private void renderGroup(GuiGraphics graphics, MachineSystems.Summary summary, int mouseX, int mouseY) {
        if (summary == null) return;
        int left = contentX(), available = contentWidth();
        int top = y - scroll;
        int header = groupHeaderHeight(summary);
        DashboardTheme.panel(graphics, left, top, available, header);
        lamp(graphics, left + 6, top + 6, summary.status());
        text(graphics, groupName(summary.group()).getString(), left + 15, top + 5, available / 2, DashboardTheme.TEXT);
        statusCounts(graphics, summary, left + available / 2, top + 5, available / 2 - 6);
        int columns = Math.max(1, Math.min(summary.lines().size(), available >= 300 ? 3 : 1));
        int columnWidth = (available - 12 - GAP * (columns - 1)) / columns;
        for (int index = 0; index < summary.lines().size(); index++) {
            int column = index % columns, row = index / columns;
            summaryLine(graphics, summary.lines().get(index), left + 6 + column * (columnWidth + GAP), top + 20 + row * 15, columnWidth);
        }
        int rowsTop = top + header + GAP;
        if (summary.total() == 0) {
            DashboardTheme.panel(graphics, left, rowsTop, available, 40);
            int lineY = rowsTop + 8;
            for (var line : font.split(Component.translatable("screen.homelink_dashboard.machines_none_" + summary.group().key()),
                    Math.max(16, available - 16))) {
                graphics.drawString(font, line, left + 8, lineY, DashboardTheme.MUTED, false);
                lineY += 11;
            }
            return;
        }
        for (int index = 0; index < summary.devices().size(); index++) {
            int rowY = rowsTop + index * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < y) continue;
            if (rowY > y + height) break;
            renderMachine(graphics, summary.devices().get(index), left, rowY, available,
                    hovered(mouseX, mouseY, left, rowY, available, ROW_HEIGHT - 2));
        }
    }

    private void renderMachine(GuiGraphics graphics, DebugDeviceView device, int left, int top, int rowWidth, boolean hover) {
        int rowHeight = ROW_HEIGHT - 2;
        DashboardTheme.panel(graphics, left, top, rowWidth, rowHeight);
        if (hover) graphics.renderOutline(left, top, rowWidth, rowHeight, DashboardTheme.ACCENT);
        lamp(graphics, left + 6, top + 6, device.status());
        int nameWidth = rowWidth * 11 / 20 - 20;
        text(graphics, device.name(), left + 15, top + 5, nameWidth, DashboardTheme.TEXT);
        text(graphics, typeName(device).getString() + "  ·  " + DashboardText.value(device.status()),
                left + 15, top + 16, nameWidth, DashboardTheme.MUTED);
        var metrics = MachineSystems.keyMetrics(device);
        int metricsLeft = left + rowWidth * 11 / 20;
        int metricWidth = (left + rowWidth - 6 - metricsLeft - GAP) / 2;
        for (int index = 0; index < metrics.size(); index++) {
            var metric = metrics.get(index);
            int metricX = metricsLeft + index * (metricWidth + GAP);
            text(graphics, metric.name(), metricX, top + 5, metricWidth, DashboardTheme.MUTED);
            text(graphics, value(metric), metricX, top + 16, metricWidth, DashboardTheme.TEXT);
            double fraction = MetricRendererRegistry.fraction(metric);
            if (fraction >= 0) bar(graphics, metricX, top + 25, metricWidth, fraction);
        }
    }

    private void statusCounts(GuiGraphics graphics, MachineSystems.Summary summary, int left, int top, int available) {
        int cursor = left;
        int[] values = {summary.online(), summary.warning(), summary.offline()};
        int[] colors = {DashboardTheme.ONLINE, DashboardTheme.WARNING, DashboardTheme.OFFLINE};
        String[] keys = {"stat_online", "stat_warning", "stat_offline"};
        for (int index = 0; index < values.length; index++) {
            String text = values[index] + " " + label(keys[index]).getString();
            int textWidth = font.width(text);
            if (cursor + 7 + textWidth > left + available) break;
            graphics.fill(cursor, top + 2, cursor + 3, top + 5, colors[index]);
            graphics.drawString(font, text, cursor + 6, top, values[index] == 0 ? DashboardTheme.MUTED : DashboardTheme.TEXT, false);
            cursor += textWidth + 14;
        }
    }

    private void summaryLine(GuiGraphics graphics, MachineSystems.Line line, int left, int top, int available) {
        text(graphics, Component.translatable("screen.homelink_dashboard.machines_" + line.key(), line.args().toArray()).getString(),
                left, top, available, DashboardTheme.TEXT);
        if (line.fraction() >= 0) bar(graphics, left, top + 10, available, line.fraction());
    }

    private void bar(GuiGraphics graphics, int left, int top, int barWidth, double fraction) {
        graphics.fill(left, top, left + barWidth, top + 2, DashboardTheme.LINE);
        graphics.fill(left, top, left + (int) Math.round(barWidth * MetricRendererRegistry.clampFraction(fraction)), top + 2, DashboardTheme.ACCENT);
    }

    private void lamp(GuiGraphics graphics, int left, int top, String status) {
        graphics.fill(left - 1, top - 1, left + 5, top + 5, 0xFF1D1F20);
        graphics.fill(left, top, left + 4, top + 4, DashboardTheme.status(status));
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maximum = maxScroll();
        if (maximum <= 0) return;
        int thumb = Math.max(6, height * height / (height + maximum));
        int thumbY = y + (height - thumb) * scroll / maximum;
        graphics.fill(x + width - 2, y, x + width, y + height, 0xFF202224);
        graphics.fill(x + width - 2, thumbY, x + width, thumbY + thumb, 0xFF858982);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || mouseX < contentX() || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        int left = contentX(), available = contentWidth();
        if (selected == null) {
            int columns = columns();
            int cardWidth = (available - GAP * (columns - 1)) / columns;
            int index = 0;
            for (var summary : summaries.values()) {
                int cardX = left + (index % columns) * (cardWidth + GAP);
                int cardY = cardsTop() + (index / columns) * (CARD_HEIGHT + GAP) - scroll;
                index++;
                if (hovered(mouseX, mouseY, cardX, cardY, cardWidth, CARD_HEIGHT)) { select(summary.group()); return true; }
            }
            return false;
        }
        var summary = summaries.get(selected);
        if (summary == null) return false;
        int rowsTop = y - scroll + groupHeaderHeight(summary) + GAP;
        int index = (int) Math.floor((mouseY - rowsTop) / ROW_HEIGHT);
        if (mouseY < rowsTop || index < 0 || index >= summary.devices().size() || mouseY - rowsTop - index * ROW_HEIGHT >= ROW_HEIGHT - 2) return false;
        openDevice.accept(summary.devices().get(index).id());
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < contentX() || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 24, 0, maxScroll());
        return true;
    }

    private int maxScroll() {
        int content;
        if (selected == null) {
            int rows = (summaries.size() + columns() - 1) / columns();
            content = cardsTop() - y + rows * (CARD_HEIGHT + GAP);
        } else {
            var summary = summaries.get(selected);
            if (summary == null) return 0;
            content = groupHeaderHeight(summary) + GAP + Math.max(40, summary.total() * ROW_HEIGHT);
        }
        return Math.max(0, content - height);
    }

    private int groupHeaderHeight(MachineSystems.Summary summary) {
        int columns = Math.max(1, Math.min(summary.lines().size(), contentWidth() >= 300 ? 3 : 1));
        int rows = (summary.lines().size() + columns - 1) / columns;
        return 22 + rows * 15;
    }

    private int sidebarWidth() { return Math.clamp(width / 5, 84, 116); }
    private int contentX() { return x + sidebarWidth() + 10; }
    private int contentWidth() { return Math.max(32, x + width - 4 - contentX()); }
    private int cardsTop() { return y + 14; }
    private int columns() { return contentWidth() >= 300 ? 2 : 1; }

    private static boolean hovered(double mouseX, double mouseY, int left, int top, int boxWidth, int boxHeight) {
        return mouseX >= left && mouseX < left + boxWidth && mouseY >= top && mouseY < top + boxHeight;
    }

    private static Component groupName(MachineSystems.Group group) {
        return Component.translatable("screen.homelink_dashboard.machines_group_" + group.key());
    }

    private static Component typeName(DebugDeviceView device) { return typeLabel(device.type()); }

    /** Localized machine type, e.g. {@code homelink_energy:wind_turbine} → "Wind turbine"; unknown types in readable case. */
    public static Component typeLabel(String type) {
        int split = type.indexOf(':');
        String key = "machine.homelink_dashboard." + type.replace(':', '.');
        return Component.translatableWithFallback(key, pretty(split < 0 ? type : type.substring(split + 1)));
    }

    /** Enumeration states are translated when the dashboard knows them, otherwise shown in readable case. */
    private static String value(DebugDeviceView.Metric metric) {
        if (metric.value() != null && metric.value().value() instanceof WireValue.EnumName enumeration) {
            String name = enumeration.name();
            return Component.translatableWithFallback("value.homelink_dashboard." + name.toLowerCase(Locale.ROOT), pretty(name)).getString();
        }
        return MetricRendererRegistry.localizedValue(metric);
    }

    private static String pretty(String identifier) {
        String spaced = identifier.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        return spaced.isEmpty() ? identifier : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private void text(GuiGraphics graphics, String text, int left, int top, int available, int color) {
        if (available <= 0) return;
        String fitted = text;
        if (font.width(fitted) > available) fitted = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        graphics.drawString(font, fitted, left, top, color, false);
    }

    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
}
