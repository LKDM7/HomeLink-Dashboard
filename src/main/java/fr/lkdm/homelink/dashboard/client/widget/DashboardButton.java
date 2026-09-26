package fr.lkdm.homelink.dashboard.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;

/** Quiet, keyboard-accessible control using Minecraft's button behavior and narration. */
public final class DashboardButton extends Button {
    private boolean navigation;
    private boolean selected;
    public DashboardButton navigation(boolean selected) {
        this.navigation = true;
        this.selected = selected;
        return this;
    }
    /** Pressed-in toggle state, as used by HomeLink Storage, without the navigation marker. */
    public DashboardButton selected(boolean value) { selected = value; return this; }
    private DashboardButton(Builder builder) { super(builder); }
    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress) {
            @Override public Button build() { return new DashboardButton(this); }
        };
    }
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = active && isHoveredOrFocused();
        int left = getX(), top = getY();
        int background = selected ? 0xFF292B2D : highlighted ? 0xFF5A5D60 : active ? 0xFF474A4D : 0xFF36383A;
        graphics.fill(left, top, left + width, top + height, 0xFF181A1B);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, background);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, selected ? 0xFF202224 : active ? 0xFF74787A : 0xFF484B4D);
        graphics.fill(left + 1, top + 2, left + 2, top + height - 1, selected ? 0xFF202224 : 0xFF626669);
        if (selected && navigation) graphics.fill(left + 5, top + height / 2 - 1, left + 7, top + height / 2 + 1, DashboardTheme.ACCENT);
        if (isFocused() && active) graphics.renderOutline(left, top, width, height, DashboardTheme.ACCENT);
        var font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        int available = Math.max(0, width - (navigation ? 16 : 8));
        if (font.width(text) > available) text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        graphics.drawString(font, text, left + (width - font.width(text)) / 2, top + (height - 8) / 2,
                selected ? DashboardTheme.ACCENT : active ? DashboardTheme.TEXT : 0xFF91948F, false);
    }
}
