package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/** Public HomeCore network metadata and connection diagnostics; no invented radio topology. */
public final class NetworkView {
    private final DashboardClientState state;
    private final Font font;
    private boolean advanced;
    private int x;
    private int y;
    private int width;
    private int height;
    private int online;
    private int warning;
    private int offline;
    private long revision = -1;

    public NetworkView(DashboardClientState state, Font font) { this.state = state; this.font = font; }
    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        addWidget.accept(DashboardButton.builder(Component.translatable("screen.homelink_dashboard.network_advanced"), button -> advanced = !advanced)
                .bounds(x + Math.max(0, width - 90), y, Math.min(90, width), 20).build());
        tick();
    }
    public void tick() {
        if (revision == state.revision()) return;
        revision = state.revision();
        online = warning = offline = 0;
        for (var device : state.devices()) {
            switch (device.status()) { case "ONLINE" -> online++; case "WARNING" -> warning++; default -> offline++; }
        }
    }
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 0) return;
        graphics.enableScissor(x, y, x + width, y + height);
        DashboardTheme.panel(graphics, x, y + 21, width, 72);
        graphics.fill(x, y + 21, x + 2, y + 93, DashboardTheme.status(state.connectionStatus()));
        graphics.fill(x + 8, y + 36, x + width - 8, y + 37, DashboardTheme.LINE);
        graphics.drawString(font, font.plainSubstrByWidth(state.networkName(), Math.max(0, width - 98)), x, y + 5, 0xE7E5E0, false);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_connection", DashboardText.component(state.connectionStatus())), 25,
                state.connectionStatus().equals("CONNECTED") ? 0xA1BD92 : 0xD3B16F);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_owner", state.ownerId().map(Object::toString).orElse("?")), 39, 0xAFB1AD);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_members", state.memberCount(), DashboardText.component(state.role())), 53, 0xAFB1AD);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_devices", state.totalDeviceCount(), state.devices().size()), 67, 0xAFB1AD);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_states", online, warning, offline), 81, 0xAFB1AD);
        if (advanced) text(graphics, Component.literal("UUID: " + state.networkId()), 101, 0xA4A7A1);
        else text(graphics, Component.translatable("screen.homelink_dashboard.network_live_scope"), 101, 0xA4A7A1);
        graphics.disableScissor();
    }
    private void text(GuiGraphics graphics, Component text, int offset, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(0, width - 16)), x + 8, y + offset, color, false);
    }
    public void showAdvanced(boolean value) { advanced = value; }
}
