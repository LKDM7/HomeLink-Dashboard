package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Compact actual option popup: three visible rows, wheel/keyboard navigation, explicit selection. */
final class ActionDropdown extends AbstractWidget {
    private final Font font;
    private List<WireValue> options = List.of();
    private List<String> labels = List.of();
    private List<Tooltip> labelTooltips = List.of();
    private boolean expanded;
    private int selected;
    private int scroll;
    private Runnable changed = () -> { };

    ActionDropdown(Font font, int x, int y, int width) { super(x, y, width, 20, Component.empty()); this.font = font; }
    void configure(List<WireValue> options) {
        this.options = options;
        labels = options.stream().map(wire -> wire.value() instanceof WireValue.EnumName name ? name.name() : String.valueOf(wire.value()))
                .map(value -> value.length() > 256 ? value.substring(0, 253) + "..." : value).toList();
        labelTooltips = options.stream().map(wire -> wire.value() instanceof WireValue.EnumName name ? name.name() : String.valueOf(wire.value()))
                .map(value -> Tooltip.create(Component.literal(value))).toList();
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
    private void updateMessage() {
        setMessage(Component.literal(labels.isEmpty() ? "—" : labels.get(selected)));
        setTooltip(labelTooltips.isEmpty() ? null : labelTooltips.get(selected));
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        setTooltip(labelTooltips.isEmpty() ? null : labelTooltips.get(selected));
        graphics.fill(getX(), getY(), getX() + width, getY() + 20,
                active ? isHovered ? HomeLinkTheme.HOVER : HomeLinkTheme.HEADER : HomeLinkTheme.BACKGROUND);
        graphics.renderOutline(getX(), getY(), width, 20, isFocused() && active ? HomeLinkTheme.ACCENT : HomeLinkTheme.LINE);
        graphics.drawString(font, HomeLinkUi.clip(font, getMessage().getString(), Math.max(0, width - 24)), getX() + 5, getY() + 6,
                active ? HomeLinkTheme.TEXT : HomeLinkTheme.MUTED, false);
        graphics.drawString(font, expanded ? "▲" : "▼", getX() + width - 13, getY() + 6, HomeLinkTheme.MUTED, false);
        if (!expanded) return;
        HomeLinkUi.panel(graphics, getX(), getY() + 20, width, height - 20);
        int end = Math.min(options.size(), scroll + 3);
        for (int index = scroll; index < end; index++) {
            int top = getY() + 20 + (index - scroll) * 12;
            boolean hover = mouseX >= getX() && mouseX < getX() + width && mouseY >= top && mouseY < top + 12;
            if (hover || index == selected) graphics.fill(getX() + 1, top, getX() + width - 3, top + 12, HomeLinkTheme.HOVER);
            if (hover) setTooltip(labelTooltips.get(index));
            graphics.drawString(font, HomeLinkUi.clip(font, labels.get(index), Math.max(0, width - 12)), getX() + 5, top + 2, HomeLinkTheme.TEXT, false);
        }
        if (options.size() > 3) {
            int available = Math.min(3, options.size()) * 12;
            int thumb = Math.max(4, available * 3 / options.size());
            int top = getY() + 20 + (available - thumb) * scroll / (options.size() - 3);
            graphics.fill(getX() + width - 2, top, getX() + width, top + thumb, HomeLinkTheme.ACCENT);
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
