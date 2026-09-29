package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.dashboard.layout.DashboardProfile;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Home editor. The device list is on the left; a device (or the network energy balance) is dragged onto the
 * Home preview on the right to add it, and a widget is dragged to change its place in the reading order. A click
 * selects a widget and the bottom bar chooses its content, size, or removes it.
 */
public final class HomeEditorView {
    /** Whether the editor was opened from a wall display whose owner may show their Home to every viewer. */
    public interface Sharing {
        boolean available();
        boolean shared();
        void share(boolean value);
    }

    private static final int CELL_HEIGHT = 20;
    private static final int PREVIEW_TOP = 32;
    private static final int DRAG_THRESHOLD = 4;
    private final DashboardClientState state;
    private final DashboardPreferencesClient preferences;
    private final Font font;
    private final WidgetCards cards;
    private final EditorPalette palette;
    private final Sharing sharing;
    /** Buttons acting on the selected widget, with whether they apply to its kind. */
    private final java.util.Map<Button, Boolean> actionButtons = new java.util.LinkedHashMap<>();
    private Runnable rebuild = () -> { };
    private UUID deviceId;
    private UUID widgetId;
    private int previewScroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private String localError = "";
    private boolean searchFocused;
    private EditBox search;
    // Drag in progress: a palette row or a widget of the preview, with the press position.
    private EditorPalette.Row dragRow;
    private UUID dragWidget;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private double mouseX;
    private double mouseY;

    HomeEditorView(DashboardClientState state, DashboardPreferencesClient preferences, Font font, WidgetCards cards, Sharing sharing) {
        this.state = state;
        this.preferences = preferences;
        this.font = font;
        this.cards = cards;
        this.palette = new EditorPalette(state, preferences, font);
        this.sharing = sharing;
    }

    /**
     * @param focus gives the keyboard focus back to the search field after a rebuild
     * @param done leaves the editor
     */
    void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget, Consumer<AbstractWidget> focus,
            Runnable rebuild, Runnable done) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rebuild = rebuild;
        actionButtons.clear();
        if (widgetId != null && selectedWidget() == null) widgetId = null;
        if (search != null) searchFocused = search.isFocused();
        search = palette.init(x, y + 20, paletteWidth(), height - 20 - (fullWidthBar() ? DashboardTheme.CONTROL_HEIGHT + 4 : 0), addWidget);
        if (searchFocused) focus.accept(search);
        button(addWidget, WidgetCards.label("done"), width - 70, 0, 70, done);
        if (sharing.available()) {
            Button share = button(addWidget, WidgetCards.label(sharing.shared() ? "display_shared" : "display_private"),
                    width - 70 - 4 - 110, 0, 110, () -> { sharing.share(!sharing.shared()); rebuild.run(); });
            share.setTooltip(Tooltip.create(WidgetCards.label("display_sharing_tooltip")));
        }
        initWidgetBar(addWidget);
        updateButtons();
    }

    /** Bottom bar of the preview: what the selected widget shows, its size, and removal. */
    private void initWidgetBar(Consumer<AbstractWidget> addWidget) {
        int left = widgetBarLeft();
        int bar = height - DashboardTheme.CONTROL_HEIGHT;
        var selected = selectedWidget();
        Component size = Component.translatable("screen.homelink_dashboard.widget_width",
                WidgetCards.label((selected == null ? HomeLayout.Size.HALF : HomeLayout.Size.of(selected)).key()));
        int removeWidth = font.width(WidgetCards.label("remove_widget")) + 12, sizeWidth = font.width(size) + 12;
        int presetWidth = presetWidth(sizeWidth, removeWidth);
        boolean presets = selected != null && selected.type() != DashboardWidget.Type.ENERGY_BALANCE;
        action(button(addWidget, Component.literal("<"), left, bar, 20, () -> cyclePreset(-1)), presets);
        action(button(addWidget, Component.literal(">"), left + 24 + presetWidth, bar, 20, () -> cyclePreset(1)), presets);
        Button resize = button(addWidget, size, left + 48 + presetWidth + 4, bar, sizeWidth, this::resizeSelected);
        resize.setTooltip(Tooltip.create(WidgetCards.label("widget_width_tooltip")));
        action(resize, true);
        action(button(addWidget, WidgetCards.label("remove_widget"), width - removeWidth, bar, removeWidth, this::removeSelected), true);
    }

    private boolean fullWidthBar() {
        int largestSize = 0;
        for (var size : HomeLayout.Size.values()) largestSize = Math.max(largestSize,
                font.width(Component.translatable("screen.homelink_dashboard.widget_width", WidgetCards.label(size.key()))) + 12);
        int minimum = 48 + 30 + largestSize + font.width(WidgetCards.label("remove_widget")) + 12 + 8;
        return width - previewLeft() < minimum;
    }
    private int widgetBarLeft() { return fullWidthBar() ? 0 : previewLeft(); }
    private int presetWidth(int sizeWidth, int removeWidth) { return Math.max(0, width - widgetBarLeft() - 48 - sizeWidth - removeWidth - 8); }

    void tick() {
        updateButtons();
        previewScroll = Math.clamp(previewScroll, 0, maxPreviewScroll());
    }

    private void updateButtons() {
        boolean enabled = canEdit() && selectedWidget() != null;
        for (var entry : actionButtons.entrySet()) entry.getKey().active = enabled && entry.getValue();
    }

    void selectDevice(UUID id) { deviceId = id; }
    UUID device() { return deviceId; }
    void selectWidget(UUID id) { widgetId = id; }
    void reset() { localError = ""; dragRow = null; dragWidget = null; dragging = false; }
    void reveal(DashboardWidget widget) { widgetId = widget.id(); previewScroll = Math.max(0, widget.y() * CELL_HEIGHT - CELL_HEIGHT); }

    /** Appends the summary of the selected device. */
    void addSummary() { insert(HomeLayout.create(DashboardWidget.Type.DEVICE_SUMMARY, deviceId, ""), Integer.MAX_VALUE); }
    /** Appends the first metric of the selected device. */
    void addMetric() {
        var device = cards.device(deviceId);
        if (device != null && !device.metrics().isEmpty())
            insert(HomeLayout.create(DashboardWidget.Type.METRIC, deviceId, device.metrics().getFirst().id()), Integer.MAX_VALUE);
    }
    private void insert(DashboardWidget widget, int index) {
        if (widget.type() != DashboardWidget.Type.ENERGY_BALANCE && cards.device(widget.deviceId()) == null) return;
        var widgets = ordered();
        if (widgets.size() >= DashboardProfile.MAX_WIDGETS) { localError = "WIDGET_LIMIT"; return; }
        widgets.add(Math.clamp(index, 0, widgets.size()), widget);
        if (save(widgets)) widgetId = widget.id();
    }

    /** Moves the selected widget one place earlier (negative) or later (positive) in the reading order. */
    void moveSelected(int step) {
        int index = ordered().indexOf(selectedWidget());
        if (index >= 0) moveTo(index, index + step + (step > 0 ? 1 : 0));
    }
    /** Moves the widget at {@code from} before the widget currently at {@code target}, or to the end. */
    private void moveTo(int from, int target) {
        var widgets = ordered();
        if (from < 0 || from >= widgets.size()) return;
        int destination = Math.clamp(target > from ? target - 1 : target, 0, widgets.size() - 1);
        if (destination == from) return;
        widgets.add(destination, widgets.remove(from));
        save(widgets);
    }
    /** Steps the selected widget through the quarter, half and full sizes. */
    void resizeSelected() {
        var widget = selectedWidget();
        if (widget != null) replace(widget, HomeLayout.resized(widget, HomeLayout.Size.of(widget).next()));
    }
    void removeSelected() {
        var widgets = ordered();
        int index = widgets.indexOf(selectedWidget());
        if (index < 0) return;
        widgets.remove(index);
        if (save(widgets)) widgetId = null;
    }

    /** Steps the selected widget through its device's presets: summary, each metric, each one-click action. */
    private void cyclePreset(int step) {
        var widget = selectedWidget();
        var device = widget == null ? null : cards.device(widget.deviceId());
        if (device == null) return;
        var presets = HomeLayout.presets(device);
        var next = presets.get(Math.floorMod(HomeLayout.presetIndex(widget, presets) + step, presets.size()));
        replace(widget, HomeLayout.showing(widget, next));
    }

    private void replace(DashboardWidget widget, DashboardWidget replacement) {
        var widgets = ordered();
        int index = widgets.indexOf(widget);
        if (index < 0) return;
        widgets.set(index, replacement);
        save(widgets);
    }

    /** Lays the widgets out in reading order and saves them. */
    private boolean save(List<DashboardWidget> widgets) {
        if (!canEdit()) return false;
        var packed = HomeLayout.pack(widgets);
        if (packed == null) { localError = "GRID_FULL"; return false; }
        localError = "";
        return preferences.saveLayout(packed);
    }

    private List<DashboardWidget> ordered() { return HomeLayout.ordered(preferences.profile().widgets()); }

    // ---- Rendering ----------------------------------------------------------------------------------------

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int headerRight = width - 78 - (sharing.available() ? 114 : 0);
        String title = WidgetCards.label("editor_title").getString();
        text(graphics, title, x, y + 5, headerRight, DashboardTheme.TEXT);
        String result = !localError.isEmpty() ? localError : preferences.pending() ? "…" : preferences.result();
        if (!result.equals("SUCCESS")) {
            int titleWidth = font.width(title) + 12;
            text(graphics, DashboardText.message(result), x + titleWidth, y + 5, headerRight - titleWidth, DashboardTheme.WARNING);
        }
        graphics.fill(x + paletteWidth() + 5, y + 20, x + paletteWidth() + 6, fullWidthBar() ? previewBottom() : y + height, DashboardTheme.LINE);
        palette.render(graphics, mouseX, mouseY, dragging ? dragRow : null);
        renderPreview(graphics);
        renderWidgetBar(graphics);
        if (dragging) renderDrag(graphics);
    }

    private void renderPreview(GuiGraphics graphics) {
        int left = x + previewLeft();
        int span = width - previewLeft();
        text(graphics, WidgetCards.label(canEdit() || preferences.pending() ? "editor_hint" : "editor_read_only").getString(),
                left, y + PREVIEW_TOP - 11, span, DashboardTheme.MUTED);
        int top = y + PREVIEW_TOP, bottom = previewBottom();
        graphics.fill(left, top, left + span, bottom, 0xFF1D1F20);
        graphics.enableScissor(left, top, left + span, bottom);
        var widgets = ordered();
        for (var widget : widgets) {
            int[] card = card(widget);
            if (card[1] + card[3] <= top || card[1] >= bottom) continue;
            cards.render(graphics, widget, card[0], card[1], card[2], card[3], false, mouseX, mouseY);
            if (widget.id().equals(widgetId)) graphics.renderOutline(card[0] - 1, card[1] - 1, card[2] + 2, card[3] + 2, DashboardTheme.ACCENT);
        }
        if (widgets.isEmpty()) {
            int lineY = top + 10;
            for (var line : font.split(WidgetCards.label("no_widgets"), Math.max(16, span - 20))) {
                graphics.drawString(font, line, left + 10, lineY, DashboardTheme.MUTED, false);
                lineY += 12;
            }
        }
        graphics.disableScissor();
    }

    private void renderWidgetBar(GuiGraphics graphics) {
        var widget = selectedWidget();
        int left = x + widgetBarLeft();
        int bar = y + height - DashboardTheme.CONTROL_HEIGHT;
        Component size = Component.translatable("screen.homelink_dashboard.widget_width",
                WidgetCards.label((widget == null ? HomeLayout.Size.HALF : HomeLayout.Size.of(widget)).key()));
        int presetWidth = presetWidth(font.width(size) + 12, font.width(WidgetCards.label("remove_widget")) + 12);
        DashboardTheme.panel(graphics, left + 22, bar, presetWidth, DashboardTheme.CONTROL_HEIGHT);
        String value = widget == null ? WidgetCards.label("editor_select").getString() : cards.content(widget);
        value = font.plainSubstrByWidth(value, presetWidth - 8);
        graphics.drawString(font, value, left + 22 + (presetWidth - font.width(value)) / 2, bar + 5,
                widget == null ? DashboardTheme.MUTED : DashboardTheme.TEXT, false);
    }

    /** Ghost of the dragged item under the cursor and the insertion mark in the preview. */
    private void renderDrag(GuiGraphics graphics) {
        if (overPreview(mouseX, mouseY)) {
            var widgets = ordered();
            int index = dropIndex(mouseX, mouseY);
            if (index < widgets.size()) {
                int[] card = card(widgets.get(index));
                graphics.fill(card[0] - 3, card[1], card[0] - 1, card[1] + card[3], DashboardTheme.ACCENT);
            } else {
                int end = widgets.isEmpty() ? y + PREVIEW_TOP + 2 : endOfPreview();
                graphics.fill(x + previewLeft(), end, x + width, end + 2, DashboardTheme.ACCENT);
            }
        }
        String name = dragRow != null ? dragRow.label() : dragWidget == null || findWidget(dragWidget) == null ? "" : cards.name(findWidget(dragWidget));
        int ghostWidth = Math.min(140, font.width(name) + 12);
        int gx = (int) mouseX + 6, gy = (int) mouseY + 6;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(gx, gy, gx + ghostWidth, gy + 16, 0xE0303234);
        graphics.renderOutline(gx, gy, ghostWidth, 16, DashboardTheme.ACCENT);
        text(graphics, name, gx + 6, gy + 4, ghostWidth - 12, DashboardTheme.TEXT);
        graphics.pose().popPose();
    }

    /** @return x, y, width and height of a widget in the preview */
    private int[] card(DashboardWidget widget) {
        int left = x + previewLeft() + 3;
        int span = width - previewLeft() - 6;
        return new int[] {left + widget.x() * span / DashboardWidget.COLUMNS, y + PREVIEW_TOP + 3 + widget.y() * CELL_HEIGHT - previewScroll,
                widget.width() * span / DashboardWidget.COLUMNS - 4, widget.height() * CELL_HEIGHT - 4};
    }

    private int endOfPreview() {
        int rows = 0;
        for (var widget : preferences.profile().widgets()) rows = Math.max(rows, widget.y() + widget.height());
        return y + PREVIEW_TOP + 3 + rows * CELL_HEIGHT - previewScroll - 2;
    }

    /** Index before which a drop at the cursor inserts: the hovered card's half decides before or after it. */
    private int dropIndex(double mouseX, double mouseY) {
        var widgets = ordered();
        for (int index = 0; index < widgets.size(); index++) {
            int[] card = card(widgets.get(index));
            if (mouseX >= card[0] && mouseX < card[0] + card[2] + 4 && mouseY >= card[1] && mouseY < card[1] + card[3] + 4) {
                boolean after = widgets.get(index).width() == DashboardWidget.COLUMNS ? mouseY >= card[1] + card[3] / 2.0 : mouseX >= card[0] + card[2] / 2.0;
                return after ? index + 1 : index;
            }
            if (card[1] > mouseY) return index;
        }
        return widgets.size();
    }

    private DashboardWidget widgetAt(double mouseX, double mouseY) {
        for (var widget : preferences.profile().widgets()) {
            int[] card = card(widget);
            if (mouseX >= card[0] && mouseX < card[0] + card[2] && mouseY >= card[1] && mouseY < card[1] + card[3]) return widget;
        }
        return null;
    }

    // ---- Mouse --------------------------------------------------------------------------------------------

    /** A press on a device or a widget starts a possible drag; the header, search field and bottom bar are left to their widgets. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        pressX = mouseX;
        pressY = mouseY;
        if (palette.contains(mouseX, mouseY)) {
            var row = palette.rowAt(mouseX, mouseY);
            if (row == null) return true;
            if (row.kind() == EditorPalette.Kind.DEVICE) {
                if (palette.overStar(mouseX)) {
                    if (preferences.ready() && !preferences.pending()) preferences.toggleFavorite(row.device().id());
                    return true;
                }
                deviceId = row.device().id();
            }
            if (canEdit()) dragRow = row;
            return true;
        }
        if (overPreview(mouseX, mouseY)) {
            var widget = widgetAt(mouseX, mouseY);
            UUID selected = widget == null ? null : widget.id();
            if (canEdit()) dragWidget = selected;
            if (!Objects.equals(selected, widgetId)) { widgetId = selected; rebuild.run(); }
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (dragRow == null && dragWidget == null) return false;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) >= DRAG_THRESHOLD) dragging = true;
        return true;
    }

    boolean mouseReleased(double mouseX, double mouseY) {
        if (dragRow == null && dragWidget == null) return false;
        if (dragging && overPreview(mouseX, mouseY)) {
            int index = dropIndex(mouseX, mouseY);
            if (dragRow != null) insert(dragRow.kind() == EditorPalette.Kind.ENERGY
                    ? HomeLayout.create(DashboardWidget.Type.ENERGY_BALANCE, DashboardWidget.NETWORK, "")
                    : HomeLayout.create(DashboardWidget.Type.DEVICE_SUMMARY, dragRow.device().id(), ""), index);
            else moveTo(ordered().indexOf(findWidget(dragWidget)), index);
        }
        dragRow = null;
        dragWidget = null;
        dragging = false;
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        int direction = (int) Math.signum(vertical);
        if (palette.contains(mouseX, mouseY)) palette.scroll(direction);
        else if (overPreview(mouseX, mouseY)) previewScroll = Math.clamp(previewScroll - direction * CELL_HEIGHT, 0, maxPreviewScroll());
        else return false;
        return true;
    }

    // ---- Verification helpers -------------------------------------------------------------------------------

    /** @return the center of a device row in the list, or null when it is not visible */
    public double[] deviceRow(UUID device) { return palette.center(device); }
    /** @return a point of the preview below every widget, where a drop appends */
    public double[] previewEnd() { return new double[] {x + previewLeft() + 20, Math.min(previewBottom() - 4, endOfPreview() + 8)}; }

    // ---- Geometry and helpers -----------------------------------------------------------------------------

    private int paletteWidth() { return Math.clamp(width / 4, 100, 150); }
    private int previewLeft() { return paletteWidth() + 12; }
    private int previewBottom() { return y + height - DashboardTheme.CONTROL_HEIGHT - 4; }
    private boolean overPreview(double mouseX, double mouseY) {
        return mouseX >= x + previewLeft() && mouseX < x + width && mouseY >= y + PREVIEW_TOP && mouseY < previewBottom();
    }
    private int maxPreviewScroll() {
        int rows = 0;
        for (var widget : preferences.profile().widgets()) rows = Math.max(rows, widget.y() + widget.height());
        return Math.max(0, rows * CELL_HEIGHT + 6 - (previewBottom() - y - PREVIEW_TOP));
    }
    private boolean canEdit() {
        return (state.role().equals("OWNER") || state.role().equals("ADMIN")) && preferences.ready() && !preferences.pending();
    }
    private DashboardWidget findWidget(UUID id) {
        return preferences.profile().widgets().stream().filter(widget -> widget.id().equals(id)).findFirst().orElse(null);
    }
    DashboardWidget selectedWidget() { return findWidget(widgetId); }
    private void action(Button button, boolean applies) { actionButtons.put(button, applies); }
    private Button button(Consumer<AbstractWidget> addWidget, Component title, int left, int top, int width, Runnable action) {
        Button button = DashboardButton.builder(title, ignored -> action.run()).bounds(x + left, y + top, Math.max(16, width), DashboardTheme.CONTROL_HEIGHT).build();
        button.setTooltip(Tooltip.create(title));
        addWidget.accept(button);
        return button;
    }
    private void text(GuiGraphics graphics, String text, int left, int top, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text, Math.max(0, width)), left, top, color, false);
    }
}
