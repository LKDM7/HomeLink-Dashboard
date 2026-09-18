package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Compact actual option popup: three visible rows, wheel/keyboard navigation, explicit selection. */
final class ActionDropdown extends AbstractWidget {
    private final Font font;
    private List<WireValue> options = List.of();
    private List<String> labels = List.of();
    private boolean expanded;
    private int selected;
    private int scroll;
    private Runnable changed = () -> { };

    ActionDropdown(Font font, int x, int y, int width) { super(x, y, width, 20, Component.empty()); this.font = font; }
    void configure(List<WireValue> options) {
        this.options = options;
        labels = options.stream().map(wire -> wire.value() instanceof WireValue.EnumName name ? name.name() : String.valueOf(wire.value()))
                .map(value -> value.length() > 256 ? value.substring(0, 253) + "..." : value).toList();
        selected = scroll = 0;
        close();
        updateMessage();
    }
    void onExpandedChange(Runnable changed) { this.changed = changed; }
    boolean expanded() { return expanded; }
    Object value() { if (options.isEmpty()) throw new IllegalArgumentException("NO_OPTIONS"); return options.get(selected).value(); }
    void close() { setExpanded(false); }
    private void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        setHeight(value ? 20 + Math.min(3, options.size()) * 12 : 20);
        changed.run();
    }
    private void updateMessage() { setMessage(Component.literal(labels.isEmpty() ? "—" : labels.get(selected))); }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + 20, active ? 0xFF484B4D : 0xFF343638);
        graphics.drawString(font, font.plainSubstrByWidth(getMessage().getString(), Math.max(0, width - 24)), getX() + 5, getY() + 6, active ? 0xE7E5E0 : 0x768893, false);
        graphics.drawString(font, expanded ? "▲" : "▼", getX() + width - 13, getY() + 6, 0xA9BCC7, false);
        if (!expanded) return;
        graphics.fill(getX(), getY() + 20, getX() + width, getY() + height, 0xFF232527);
        int end = Math.min(options.size(), scroll + 3);
        for (int index = scroll; index < end; index++) {
            int top = getY() + 20 + (index - scroll) * 12;
            boolean hover = mouseX >= getX() && mouseX < getX() + width && mouseY >= top && mouseY < top + 12;
            if (hover || index == selected) graphics.fill(getX() + 1, top, getX() + width - 3, top + 12, 0xFF505352);
            graphics.drawString(font, font.plainSubstrByWidth(labels.get(index), Math.max(0, width - 12)), getX() + 5, top + 2, 0xE7E5E0, false);
        }
        if (options.size() > 3) {
            int available = Math.min(3, options.size()) * 12;
            int thumb = Math.max(4, available * 3 / options.size());
            int top = getY() + 20 + (available - thumb) * scroll / (options.size() - 3);
            graphics.fill(getX() + width - 2, top, getX() + width, top + thumb, 0xFF8AC4B4);
        }
    }
    @Override public void onClick(double mouseX, double mouseY) {
        if (options.isEmpty()) return;
        if (!expanded || mouseY < getY() + 20) setExpanded(!expanded);
        else {
            int index = scroll + (int) ((mouseY - getY() - 20) / 12);
            if (index >= 0 && index < options.size()) { selected = index; updateMessage(); close(); }
        }
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!active || !visible || !expanded || !isMouseOver(mouseX, mouseY)) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, options.size() - 3));
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !visible || !isFocused() || options.isEmpty()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && expanded) { close(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) { setExpanded(!expanded); return true; }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            selected = key == GLFW.GLFW_KEY_HOME ? 0 : key == GLFW.GLFW_KEY_END ? options.size() - 1
                    : Math.clamp(selected + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 0, options.size() - 1);
            scroll = Math.clamp(selected - 1, 0, Math.max(0, options.size() - 3));
            updateMessage();
            setExpanded(true);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void setFocused(boolean focused) { super.setFocused(focused); if (!focused) close(); }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
