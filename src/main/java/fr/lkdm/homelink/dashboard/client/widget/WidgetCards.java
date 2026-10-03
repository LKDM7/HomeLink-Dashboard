package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;

import fr.lkdm.homecore.api.action.Unit;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.DeviceActionView;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws the Home widget cards, on the page and in the editor preview, and runs the one-click actions of ACTION
 * widgets. A card taller than {@link #TALL} shows details; a quarter card only its name and main value.
 */
final class WidgetCards {
    private static final int TALL = 50;
    private static final int BUTTON_HEIGHT = 14;
    private final DashboardClientState state;
    private final Font font;
    /** Widget whose action was sent last, so only that card shows the pending state and the result. */
    private UUID lastActionWidget;

    private record ActionButton(int x, int y, int width, Component label, Object parameter) {
        boolean contains(double mouseX, double mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT; }
    }

    WidgetCards(DashboardClientState state, Font font) {
        this.state = state;
        this.font = font;
    }

    /** @param live whether the action buttons respond (the Home page) or only show (the editor preview) */
    void render(GuiGraphics graphics, DashboardWidget widget, int x, int y, int width, int height, boolean live, double mouseX, double mouseY) {
        graphics.enableScissor(x, y, x + Math.max(0, width), y + height);
        HomeLinkUi.panel(graphics, x, y, width, height);
        var device = device(widget.deviceId());
        switch (widget.type()) {
            case DEVICE_SUMMARY -> summary(graphics, device, x, y, width, height);
            case METRIC -> metric(graphics, device, widget, x, y, width, height);
            case ACTION -> action(graphics, device, widget, x, y, width, height, live, mouseX, mouseY);
            case ENERGY_BALANCE -> energy(graphics, x, y, width, height);
        }
        graphics.disableScissor();
    }

    /** Favorite row of the Home page: name, status badge and the first two metrics. */
    void favorite(GuiGraphics graphics, DebugDeviceView device, int x, int y, int width, int height) {
        HomeLinkUi.panel(graphics, x, y, width, height);
        if (device == null) { text(graphics, "★ " + label("device_unloaded").getString(), x + 7, y + 5, width - 14, HomeLinkTheme.TEXT); return; }
        String status = DashboardText.value(device.status());
        int badge = font.width(status) + 12;
        text(graphics, "★ " + device.name(), x + 7, y + 5, width - badge - 14, HomeLinkTheme.TEXT);
        text(graphics, status, x + width - badge, y + 5, badge - 5, HomeLinkTheme.statusColor(device.status()));
        for (int index = 0; index < Math.min(2, device.metrics().size()); index++) {
            var metric = device.metrics().get(index);
            text(graphics, metric.name() + ": " + MetricRendererRegistry.localizedValue(metric), x + 7, y + 19 + index * 11, width - 14, HomeLinkTheme.MUTED);
        }
    }

    private void summary(GuiGraphics graphics, DebugDeviceView device, int x, int y, int width, int height) {
        text(graphics, name(device), x + 5, y + 5, width - 10, HomeLinkTheme.TEXT);
        if (device == null) return;
        text(graphics, DashboardText.value(device.status()), x + 5, y + 17, width - 10, HomeLinkTheme.statusColor(device.status()));
        int count = Math.min(Math.max(0, (height - 29) / 11), Math.min(2, device.metrics().size()));
        for (int index = 0; index < count; index++) {
            var metric = device.metrics().get(index);
            text(graphics, metric.name() + ": " + MetricRendererRegistry.localizedValue(metric), x + 5, y + 29 + index * 11, width - 10, HomeLinkTheme.MUTED);
        }
    }

    private void metric(GuiGraphics graphics, DebugDeviceView device, DashboardWidget widget, int x, int y, int width, int height) {
        text(graphics, name(device), x + 5, y + 5, width - 10, HomeLinkTheme.TEXT);
        var metric = metric(device, widget.metricId());
        if (metric == null) text(graphics, label("metric_unavailable").getString(), x + 5, y + 19, width - 10, HomeLinkTheme.MUTED);
        else if (height >= TALL) MetricRendererRegistry.render(graphics, font, metric, x + 5, y + 19, width - 10);
        else text(graphics, MetricRendererRegistry.localizedValue(metric), x + 5, y + 19, width - 10, HomeLinkTheme.TEXT);
    }

    private void action(GuiGraphics graphics, DebugDeviceView device, DashboardWidget widget, int x, int y, int width, int height,
            boolean live, double mouseX, double mouseY) {
        text(graphics, name(device), x + 5, y + 5, width - 10, HomeLinkTheme.TEXT);
        var action = action(device, widget.metricId());
        if (device == null) return;
        if (action == null) { text(graphics, label("action_unavailable").getString(), x + 5, y + 17, width - 10, HomeLinkTheme.MUTED); return; }
        boolean mine = widget.id().equals(lastActionWidget) && (state.actionPending() || !state.lastActionCode().isEmpty());
        if (height >= TALL) {
            String caption = !mine ? action.name() : state.actionPending() ? label("action_pending").getString() : DashboardText.value(state.lastActionCode());
            int color = !mine ? HomeLinkTheme.MUTED : state.lastActionCode().equals("SUCCESS") ? HomeLinkTheme.ONLINE : HomeLinkTheme.WARNING;
            text(graphics, caption, x + 5, y + 17, width - 10, color);
        }
        boolean usable = live && usable(device, action);
        for (var button : buttons(action, x, y, width, height)) {
            boolean hovered = usable && button.contains(mouseX, mouseY);
            graphics.fill(button.x(), button.y(), button.x() + button.width(), button.y() + BUTTON_HEIGHT, usable ? 0xFF3A3D40 : 0xFF2C2E30);
            graphics.renderOutline(button.x(), button.y(), button.width(), BUTTON_HEIGHT, hovered ? HomeLinkTheme.ACCENT : HomeLinkTheme.LINE);
            String caption = font.plainSubstrByWidth(button.label().getString(), Math.max(0, button.width() - 4));
            graphics.drawString(font, caption, button.x() + (button.width() - font.width(caption)) / 2, button.y() + 3,
                    usable ? HomeLinkTheme.TEXT : HomeLinkTheme.MUTED, false);
        }
    }

    private void energy(GuiGraphics graphics, int x, int y, int width, int height) {
        var balance = HomeLayout.energy(state.devices());
        String net = (balance.net() >= 0 ? "+" : "") + MetricRendererRegistry.decimal(balance.net()) + " HE/t";
        int netColor = balance.net() >= 0 ? HomeLinkTheme.ONLINE : HomeLinkTheme.WARNING;
        boolean none = balance.producers() == 0 && balance.batteries() == 0;
        if (height < TALL) {
            // A quarter card is too narrow for the title and the net flow side by side.
            text(graphics, label("energy_balance").getString(), x + 5, y + 5, width - 10, HomeLinkTheme.TEXT);
            text(graphics, none ? label("energy_none").getString() : net, x + 5, y + 19, width - 10, none ? HomeLinkTheme.MUTED : netColor);
            return;
        }
        int netWidth = Math.min(font.width(net), width / 2);
        text(graphics, label("energy_balance").getString(), x + 5, y + 5, width - netWidth - 14, HomeLinkTheme.TEXT);
        text(graphics, net, x + width - 5 - netWidth, y + 5, netWidth, netColor);
        if (none) {
            text(graphics, label("energy_none").getString(), x + 5, y + 19, width - 10, HomeLinkTheme.MUTED);
            return;
        }
        row(graphics, label("energy_production"), MetricRendererRegistry.decimal(balance.production()) + " HE/t", x + 5, y + 19, width - 10);
        row(graphics, label("energy_consumption"), MetricRendererRegistry.decimal(balance.consumption()) + " HE/t", x + 5, y + 30, width - 10);
        if (balance.charge() >= 0) {
            row(graphics, label("energy_stored"), Math.round(balance.charge() * 100) + "%", x + 5, y + 41, width - 10);
            graphics.fill(x + 5, y + height - 5, x + width - 5, y + height - 3, HomeLinkTheme.LINE);
            graphics.fill(x + 5, y + height - 5, x + 5 + (int) Math.round((width - 10) * balance.charge()), y + height - 3, HomeLinkTheme.ACCENT);
        }
    }

    /** @return the action parameter of the button under the cursor, or null */
    Object actionAt(DashboardWidget widget, int x, int y, int width, int height, double mouseX, double mouseY) {
        if (widget.type() != DashboardWidget.Type.ACTION) return null;
        var device = device(widget.deviceId());
        var action = action(device, widget.metricId());
        if (action == null) return null;
        for (var button : buttons(action, x, y, width, height)) if (button.contains(mouseX, mouseY)) return button.parameter();
        return null;
    }

    /** @return the center of the widget's first action button, for verification; null for other widgets */
    double[] firstButton(DashboardWidget widget, int x, int y, int width, int height) {
        var action = widget.type() == DashboardWidget.Type.ACTION ? action(device(widget.deviceId()), widget.metricId()) : null;
        if (action == null) return null;
        var button = buttons(action, x, y, width, height).getFirst();
        return new double[] {button.x() + button.width() / 2.0, button.y() + BUTTON_HEIGHT / 2.0};
    }

    /** Sends the action of an ACTION widget; the server re-checks permission, device and parameter. */
    boolean execute(DashboardWidget widget, Object parameter) {
        var device = device(widget.deviceId());
        var action = action(device, widget.metricId());
        if (action == null || !usable(device, action) || !state.executeAction(device.id(), action, parameter)) return false;
        lastActionWidget = widget.id();
        return true;
    }

    /** @return the device name, then what the widget shows, as the editor lists it */
    String name(DashboardWidget widget) {
        if (widget.type() == DashboardWidget.Type.ENERGY_BALANCE) return label("energy_balance").getString();
        String name = name(device(widget.deviceId()));
        return widget.type() == DashboardWidget.Type.DEVICE_SUMMARY ? name : name + " · " + content(widget);
    }

    /** @return what the widget shows: the summary, a metric name or an action name */
    String content(DashboardWidget widget) {
        var device = device(widget.deviceId());
        return switch (widget.type()) {
            case DEVICE_SUMMARY -> label("preset_summary").getString();
            case ENERGY_BALANCE -> label("energy_balance").getString();
            case METRIC -> {
                var metric = metric(device, widget.metricId());
                yield metric == null ? label("metric_unavailable").getString() : metric.name();
            }
            case ACTION -> {
                var action = action(device, widget.metricId());
                yield action == null ? label("action_unavailable").getString() : "▶ " + action.name();
            }
        };
    }

    private List<ActionButton> buttons(DeviceActionView action, int x, int y, int width, int height) {
        int top = y + height - BUTTON_HEIGHT - 4, left = x + 4, span = width - 8;
        if (action.type().equals("TOGGLE")) {
            int half = (span - 2) / 2;
            return List.of(new ActionButton(left, top, half, label("action_on"), Boolean.TRUE),
                    new ActionButton(left + half + 2, top, span - half - 2, label("action_off"), Boolean.FALSE));
        }
        return List.of(new ActionButton(left, top, span, label("action_run"), Unit.INSTANCE));
    }

    private boolean usable(DebugDeviceView device, DeviceActionView action) {
        return device != null && action.availableWhen(device.status()) && state.canControl(action) && !state.actionPending();
    }

    private void row(GuiGraphics graphics, Component name, String value, int x, int y, int width) {
        int valueWidth = Math.min(font.width(value), width);
        text(graphics, name.getString(), x, y, width - valueWidth - 4, HomeLinkTheme.MUTED);
        text(graphics, value, x + width - valueWidth, y, valueWidth, HomeLinkTheme.TEXT);
    }

    DebugDeviceView device(UUID id) { return state.devices().stream().filter(device -> device.id().equals(id)).findFirst().orElse(null); }
    private static DebugDeviceView.Metric metric(DebugDeviceView device, String id) {
        return device == null ? null : device.metrics().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }
    private static DeviceActionView action(DebugDeviceView device, String id) {
        return device == null ? null : device.actions().stream().filter(value -> value.id().equals(id) && DashboardWidget.oneClick(value.type()))
                .findFirst().orElse(null);
    }
    private static String name(DebugDeviceView device) { return device == null ? label("device_unloaded").getString() : device.name(); }
    private void text(GuiGraphics graphics, String text, int left, int top, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text, Math.max(0, width)), left, top, color, false);
    }
    static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
}
