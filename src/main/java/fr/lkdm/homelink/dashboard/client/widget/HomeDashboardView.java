package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Home page: network overview, personal favorites and the persisted twelve-column widget grid. Action widgets run
 * from here; the Edit button (or a click elsewhere on a widget) opens the {@link HomeEditorView editor}.
 */
public final class HomeDashboardView {
    private static final int CELL_HEIGHT = 20;
    private static final int FAVORITE_ROW = 48;
    private final DashboardClientState state;
    private final DashboardPreferencesClient preferences;
    private final WidgetCards cards;
    private final HomeEditorView editor;
    private final Font font;
    private Runnable rebuild = () -> { };
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean editMode;
    private long revision = -1;
    private long deviceRevision = -1;
    private int online;
    private int warning;
    private int offline;

    public HomeDashboardView(DashboardClientState state, DashboardPreferencesClient preferences, Font font, HomeEditorView.Sharing sharing) {
        this.state = state;
        this.preferences = preferences;
        this.font = font;
        this.cards = new WidgetCards(state, font);
        this.editor = new HomeEditorView(state, preferences, font, cards, sharing);
    }

    /** @param focus gives the keyboard focus to a widget, used to keep it on the editor's search field */
    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget, Consumer<AbstractWidget> focus, Runnable rebuild) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.rebuild = rebuild;
        if (editor.device() == null && !state.devices().isEmpty()) editor.selectDevice(state.devices().getFirst().id());
        if (editMode) editor.init(x, y, width, height, addWidget, focus, rebuild, () -> setEditMode(false));
        else {
            Button edit = DashboardButton.builder(WidgetCards.label("edit_dashboard"), ignored -> setEditMode(true))
                    .bounds(x + width - 96, y, 96, DashboardTheme.CONTROL_HEIGHT).build();
            edit.setTooltip(Tooltip.create(WidgetCards.label("edit_dashboard")));
            addWidget.accept(edit);
        }
        revision = preferences.revision();
    }

    public void tick() {
        if (deviceRevision != state.revision()) {
            deviceRevision = state.revision();
            online = warning = offline = 0;
            for (var device : state.devices()) {
                switch (device.status()) {
                    case "ONLINE" -> online++;
                    case "WARNING" -> warning++;
                    default -> offline++;
                }
            }
        }
        if (revision != preferences.revision()) {
            revision = preferences.revision();
            rebuild.run();
        }
        if (editMode) editor.tick();
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    public void selectDevice(UUID id) { editor.selectDevice(id); }
    public void selectWidget(UUID id) { editor.selectWidget(id); }
    public void setEditMode(boolean value) {
        editMode = value;
        editor.reset();
        rebuild.run();
    }
    public void toggleFavorite() {
        if (editor.device() != null && preferences.ready() && !preferences.pending()) preferences.toggleFavorite(editor.device());
    }
    public void addSummary() { editor.addSummary(); }
    public void addMetric() { editor.addMetric(); }
    public void moveSelected(int step) { editor.moveSelected(step); }
    public void resizeSelected() { editor.resizeSelected(); }
    public void removeSelected() { editor.removeSelected(); }
    /** @return the editor, for the in-game verification of dragging */
    public HomeEditorView editor() { return editor; }
    /** @return the center of an action widget's first button on the Home page, for verification; null otherwise */
    public double[] actionButton(UUID widgetId) {
        var widget = preferences.profile().widgets().stream().filter(value -> value.id().equals(widgetId)).findFirst().orElse(null);
        if (widget == null || editMode) return null;
        int[] card = card(widget);
        return cards.firstButton(widget, card[0], card[1], card[2], card[3]);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 0) return;
        graphics.enableScissor(x, y, x + width, y + height);
        if (editMode) editor.render(graphics, mouseX, mouseY); else renderHome(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    private void renderHome(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(x, y + 4, x + 4, y + 8, DashboardTheme.status(state.connectionStatus()));
        text(graphics, state.networkName(), x + 10, y + 2, width - 114, DashboardTheme.TEXT);
        text(graphics, Component.translatable("screen.homelink_dashboard.home_device_total", state.devices().size(), state.totalDeviceCount()).getString(),
                x, y + 17, width - 104, DashboardTheme.MUTED);
        if (overviewHeight() > 56) {
            int cell = width / 4;
            statistic(graphics, 0, cell, "stat_online", online, DashboardTheme.ONLINE);
            statistic(graphics, 1, cell, "stat_warning", warning, DashboardTheme.WARNING);
            statistic(graphics, 2, cell, "stat_offline", offline, DashboardTheme.OFFLINE);
            statistic(graphics, 3, cell, "stat_alerts", state.activeAlertCount(), DashboardTheme.ACCENT);
            text(graphics, WidgetCards.label("favorites_widgets").getString(), x, y + 72, width, DashboardTheme.MUTED);
        } else {
            text(graphics, Component.translatable("screen.homelink_dashboard.home_device_states", online, warning, offline).getString(),
                    x, y + 29, width - 104, DashboardTheme.MUTED);
            text(graphics, WidgetCards.label("favorites_widgets").getString() + " · "
                    + Component.translatable("screen.homelink_dashboard.home_alerts", state.activeAlertCount()).getString(), x, y + 44, width, DashboardTheme.ACCENT);
        }
        int top = y + overviewHeight();
        if (top >= y + height) return;
        graphics.enableScissor(x, top, x + width, y + height);
        int favoriteIndex = 0;
        for (UUID favorite : preferences.profile().favorites()) {
            int cardY = top + favoriteIndex++ * FAVORITE_ROW - scroll;
            if (cardY + FAVORITE_ROW - 4 > top && cardY < y + height) cards.favorite(graphics, cards.device(favorite), x, cardY, width - 4, FAVORITE_ROW - 4);
        }
        for (DashboardWidget widget : preferences.profile().widgets()) {
            int[] card = card(widget);
            if (card[1] + card[3] <= top || card[1] >= y + height) continue;
            cards.render(graphics, widget, card[0], card[1], card[2], card[3], true, mouseX, mouseY);
        }
        if (preferences.profile().favorites().isEmpty() && preferences.profile().widgets().isEmpty()) {
            DashboardTheme.panel(graphics, x, top, width - 4, Math.min(64, y + height - top));
            int lineY = top + 10;
            for (var line : font.split(WidgetCards.label("home_empty"), Math.max(16, width - 24))) {
                graphics.drawString(font, line, x + 10, lineY, DashboardTheme.MUTED, false);
                lineY += 12;
            }
        }
        graphics.disableScissor();
        int maximum = maxScroll();
        if (maximum > 0) {
            int available = height - overviewHeight();
            int thumb = Math.max(6, available * available / (available + maximum));
            int thumbY = top + (available - thumb) * scroll / maximum;
            graphics.fill(x + width - 2, top, x + width, y + height, 0xFF202224);
            graphics.fill(x + width - 2, thumbY, x + width, thumbY + thumb, 0xFF858982);
        }
    }

    /** @return x, y, width and height of a widget on the Home page */
    private int[] card(DashboardWidget widget) {
        int gridTop = y + overviewHeight() + preferences.profile().favorites().size() * FAVORITE_ROW - scroll;
        return new int[] {x + widget.x() * width / DashboardWidget.COLUMNS, gridTop + widget.y() * CELL_HEIGHT,
                widget.width() * width / DashboardWidget.COLUMNS - 4, widget.height() * CELL_HEIGHT - 4};
    }

    /**
     * Home: an action button of a widget runs its action; a click elsewhere on a widget opens the editor with it
     * selected. Editor: see {@link HomeEditorView#mouseClicked}.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inside(mouseX, mouseY)) return false;
        if (editMode) return editor.mouseClicked(mouseX, mouseY, button);
        if (button != 0 || mouseY < y + overviewHeight()) return false;
        for (var widget : preferences.profile().widgets()) {
            int[] card = card(widget);
            if (mouseX < card[0] || mouseX >= card[0] + card[2] || mouseY < card[1] || mouseY >= card[1] + card[3]) continue;
            Object parameter = cards.actionAt(widget, card[0], card[1], card[2], card[3], mouseX, mouseY);
            if (parameter != null) { cards.execute(widget, parameter); return true; }
            if (!canConfigure()) return false;
            editor.reveal(widget);
            setEditMode(true);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) { return editMode && button == 0 && editor.mouseDragged(mouseX, mouseY); }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return editMode && button == 0 && editor.mouseReleased(mouseX, mouseY); }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (!inside(mouseX, mouseY)) return false;
        if (editMode) return editor.mouseScrolled(mouseX, mouseY, vertical);
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 24, 0, maxScroll());
        return true;
    }

    private int maxScroll() {
        int rows = 0;
        for (var widget : preferences.profile().widgets()) rows = Math.max(rows, widget.y() + widget.height());
        return Math.max(0, preferences.profile().favorites().size() * FAVORITE_ROW + rows * CELL_HEIGHT - Math.max(0, height - overviewHeight()));
    }
    private int overviewHeight() { return height >= 180 && width >= 350 ? 88 : 56; }
    private void statistic(GuiGraphics graphics, int index, int cell, String key, int value, int color) {
        int left = x + index * cell;
        graphics.fill(left, y + 48, left + cell, y + 64, DashboardTheme.SURFACE);
        if (index > 0) graphics.fill(left, y + 51, left + 1, y + 61, DashboardTheme.LINE);
        graphics.fill(left + 6, y + 54, left + 9, y + 57, color);
        text(graphics, value + " " + WidgetCards.label(key).getString(), left + 14, y + 52, cell - 20, DashboardTheme.TEXT);
    }
    private boolean inside(double mouseX, double mouseY) { return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height; }
    private boolean canConfigure() { return state.role().equals("OWNER") || state.role().equals("ADMIN"); }
    private void text(GuiGraphics graphics, String text, int left, int top, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text, Math.max(0, width)), left, top, color, false);
    }
}
