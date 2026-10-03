package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;

import fr.lkdm.homelink.dashboard.config.DashboardConfig;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Immediately saved local settings, kept inside the current authenticated dashboard menu. */
public final class SettingsView {
    private final Font font;
    private final Runnable changed;
    private int x, y, width, height;
    public SettingsView(Font font, Runnable changed) { this.font = font; this.changed = changed; }
    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        addWidget.accept(HomeLinkButton.builder(alertLabel(), button -> {
            int value = DashboardConfig.alertLimit();
            DashboardConfig.setAlertLimit(value < 64 ? 64 : value < 128 ? 128 : value < 256 ? 256 : value < 512 ? 512 : 32);
            button.setMessage(alertLabel());
            changed.run();
        }).bounds(x, y + 24, width, HomeLinkTheme.CONTROL_HEIGHT).build());
        addWidget.accept(HomeLinkButton.builder(intervalLabel(), button -> {
            int value = DashboardConfig.uiInterval();
            DashboardConfig.setUiInterval(value < 2 ? 2 : value < 4 ? 4 : value < 10 ? 10 : value < 20 ? 20 : 1);
            button.setMessage(intervalLabel());
            changed.run();
        }).bounds(x, y + 50, width, HomeLinkTheme.CONTROL_HEIGHT).build());
    }
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(x, y, x + width, y + Math.max(0, height));
        graphics.fill(x, y + 76, x + width, y + 77, HomeLinkTheme.LINE);
        graphics.drawString(font, Component.translatable("screen.homelink_dashboard.settings_local"), x, y + 4, 0xC6C8C0, false);
        int lineY = y + 80;
        for (var line : font.split(Component.translatable("screen.homelink_dashboard.settings_hint"), width)) {
            graphics.drawString(font, line, x, lineY, HomeLinkTheme.MUTED, false);
            lineY += 11;
        }
        graphics.disableScissor();
    }
    private static Component alertLabel() { return Component.translatable("screen.homelink_dashboard.settings_alert_limit", DashboardConfig.alertLimit()); }
    private static Component intervalLabel() { return Component.translatable("screen.homelink_dashboard.settings_ui_interval", DashboardConfig.uiInterval()); }
}
