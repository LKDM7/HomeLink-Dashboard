package fr.lkdm.homelink.dashboard.client.screen;

import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.widget.DeviceExplorerView;
import fr.lkdm.homelink.dashboard.client.widget.ActionPanel;
import fr.lkdm.homelink.dashboard.client.widget.HomeDashboardView;
import fr.lkdm.homelink.dashboard.client.widget.AlertCenterView;
import fr.lkdm.homelink.dashboard.client.widget.NetworkView;
import fr.lkdm.homelink.dashboard.client.widget.SettingsView;
import fr.lkdm.homelink.dashboard.client.widget.DashboardButton;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.config.DashboardConfig;
import fr.lkdm.homelink.dashboard.client.state.DashboardPreferencesClient;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;
import net.minecraft.world.entity.player.Inventory;

/** Navigation shell; page views and transport have separate lifecycles. */
public final class DashboardScreen extends AbstractContainerScreen<DashboardMenu> {
    private fr.lkdm.homelink.dashboard.client.widget.ManualView manual;
    private boolean manualOpen;
    private String newNetworkName;
    private String nameResult = "";
    private final java.util.function.Consumer<fr.lkdm.homelink.dashboard.network.NetworkNamePayloads.Response> nameListener = this::receiveNameResult;
    private void receiveNameResult(fr.lkdm.homelink.dashboard.network.NetworkNamePayloads.Response response) {
        if (response.containerId() != menu.containerId) return;
        nameResult = response.result().name();
        if (network != null) network.nameResult(response.result());
    }
    private void sendName(boolean create, String name) {
        nameResult = "";
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new fr.lkdm.homelink.dashboard.network.NetworkNamePayloads.Request(menu.containerId, create, name));
    }
    public boolean manualOpen() { return manualOpen; }
    public fr.lkdm.homelink.dashboard.client.widget.ManualView manual() { return manual; }
    private void toggleManual() { manualOpen = !manualOpen; rebuild(); }
    private DashboardClientState state;
    private DeviceExplorerView explorer;
    private ActionPanel actions;
    private boolean actionMode;
    private Button actionButton;
    private Button favoriteButton;
    private String favoriteSymbol = "";
    private DashboardPreferencesClient preferences;
    private HomeDashboardView home;
    private enum Page { HOME, DEVICES, ALERTS, NETWORK, SETTINGS }
    private Page page = Page.HOME;
    private AlertCenterView alerts;
    private NetworkView network;
    private SettingsView settings;
    private int uiTicks;
    private int selectedNetwork;
    private Button previous;
    private Button next;
    private Button refresh;
    public DashboardScreen(DashboardMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    public DashboardClientState state() { return state; }
    public DeviceExplorerView explorer() { return explorer; }
    public ActionPanel actions() { return actions; }
    public HomeDashboardView home() { return home; }
    public DashboardPreferencesClient preferences() { return preferences; }
    public AlertCenterView alerts() { return alerts; }
    public NetworkView network() { return network; }
    public void showHome() { show(Page.HOME); }
    public void showDevices() { show(Page.DEVICES); }
    public void showAlerts() { show(Page.ALERTS); }
    public void showNetwork() { show(Page.NETWORK); }
    public void showSettings() { show(Page.SETTINGS); }
    private void show(Page target) {
        if (page != target || actionMode) { page = target; actionMode = false; rebuild(); }
    }
    private void openDevice(java.util.UUID id) {
        if (state.devices().stream().noneMatch(device -> device.id().equals(id))) return;
        showDevices();
        explorer.select(id);
    }
    private void rebuild() { clearWidgets(); init(); }
    private void refreshData() { state.refresh(); if (preferences != null) preferences.refresh(); }
    public void openActions() {
        if (explorer == null || explorer.selectedDeviceId().isEmpty()) return;
        actions = new ActionPanel(state, font);
        actions.selectDevice(explorer.selectedDeviceId().orElseThrow());
        actionMode = true;
        page = Page.DEVICES;
        rebuild();
    }
    @Override protected void init() {
        fr.lkdm.homelink.dashboard.network.NetworkNamePayloads.listen(nameListener);
        previous = null; next = null; refresh = null; actionButton = null;
        favoriteButton = null;
        favoriteSymbol = "";
        imageWidth = Math.min(520, width - 16);
        imageHeight = Math.min(340, height - 16);
        super.init();
        if (state == null && menu.session().networkId().isPresent()) {
            state = new DashboardClientState(menu.session().networkId().orElseThrow());
            state.setAlertLimit(DashboardConfig.alertLimit());
            state.start();
        }
        button("close", imageWidth - 80, footerY(), 68, this::onClose);
        var help = ((DashboardButton) button("manual", imageWidth - 34, 6, 20, this::toggleManual)).selected(manualOpen);
        help.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("manual.homelink_dashboard.title")));
        if (manualOpen) {
            if (manual == null) manual = new fr.lkdm.homelink.dashboard.client.widget.ManualView(font);
            manual.init(leftPos + 12, topPos + 62, imageWidth - 24, imageHeight - 96, this::addRenderableWidget, this::rebuild);
            button("manual_back", 12, footerY(), Math.min(150, imageWidth - 104), this::toggleManual);
            return;
        }
        if (state == null) {
            if (menu.session().canCreate()) {
                if (newNetworkName == null) newNetworkName = "HomeLink · " + minecraft.player.getGameProfile().getName();
                var input = DashboardTheme.input(new net.minecraft.client.gui.components.EditBox(font, leftPos + 12, topPos + 66,
                        imageWidth - 132, DashboardTheme.CONTROL_HEIGHT, Component.translatable("screen.homelink_dashboard.network_name")));
                input.setMaxLength(128); input.setValue(newNetworkName);
                var create = button("create", imageWidth - 114, 66, 102, () -> sendName(true, newNetworkName));
                create.active = fr.lkdm.homelink.dashboard.network.NetworkNames.isValid(newNetworkName);
                input.setResponder(value -> { newNetworkName = value; create.active = fr.lkdm.homelink.dashboard.network.NetworkNames.isValid(value); });
                addRenderableWidget(input);
            }
            if (!menu.session().choices().isEmpty()) {
                button("previous", 12, 110, 38, () -> { selectedNetwork = Math.floorMod(selectedNetwork - 1, menu.session().choices().size()); rebuild(); });
                button("next", imageWidth - 50, 110, 38, () -> { selectedNetwork = (selectedNetwork + 1) % menu.session().choices().size(); rebuild(); });
                button("bind", 58, 110, imageWidth - 116, () -> send(DashboardMenu.BIND_NETWORK_BASE + selectedNetwork))
                        .active = menu.session().canCreate() || menu.session().choices().get(selectedNetwork).inRange();
            }
            if (menu.session().directoryOffset() > 0) button("previous", 12, 143, 48, () -> send(DashboardMenu.PREVIOUS_NETWORKS));
            if (menu.session().hasNext()) button("more_networks", 68, 143, imageWidth - 80, () -> send(DashboardMenu.NEXT_NETWORKS));
        } else {
            if (explorer == null) explorer = new DeviceExplorerView(state, font);
            if (preferences == null) { preferences = new DashboardPreferencesClient(menu); preferences.start(); }
            if (home == null) home = new HomeDashboardView(state, preferences, font);
            if (alerts == null) alerts = new AlertCenterView(state, font, this::openDevice);
            if (network == null) network = new NetworkView(state, font, name -> sendName(false, name), this::rebuild);
            if (settings == null) settings = new SettingsView(font, () -> state.setAlertLimit(DashboardConfig.alertLimit()));
            navigation();
            if (actionMode) {
                actions.init(leftPos + 12, topPos + 82, imageWidth - 24, imageHeight - 116, this::addRenderableWidget);
                button("back_devices", 12, footerY(), Math.min(140, imageWidth - 110), () -> { actionMode = false; clearWidgets(); init(); });
                previous = null; next = null; refresh = null; actionButton = null;
            } else {
                int contentX = leftPos + 12, contentY = topPos + 62;
                int contentWidth = imageWidth - 24, contentHeight = imageHeight - 96;
                switch (page) {
                    case HOME -> home.init(contentX, contentY, contentWidth, contentHeight, this::addRenderableWidget, this::rebuild);
                    case DEVICES -> explorer.init(contentX, contentY + 20, contentWidth, contentHeight - 20, this::addRenderableWidget);
                    case ALERTS -> alerts.init(contentX, contentY, contentWidth, contentHeight, this::addRenderableWidget);
                    case NETWORK -> network.init(contentX, contentY, contentWidth, contentHeight, this::addRenderableWidget);
                    case SETTINGS -> settings.init(contentX, contentY, contentWidth, contentHeight, this::addRenderableWidget);
                }
                previous = null; next = null; refresh = null; actionButton = null;
                if (page == Page.HOME || page == Page.DEVICES) {
                    if (!state.isNetworkWatch()) {
                        previous = button("previous", 12, footerY(), 28, () -> state.requestPage(Math.max(0, state.offset() - HomeCorePayloads.PAGE_SIZE)));
                        next = button("next", 44, footerY(), 28, () -> state.requestPage(state.nextOffset()));
                    }
                    int refreshX = state.isNetworkWatch() ? 12 : 78;
                    refresh = button("refresh", refreshX, footerY(), 72, this::refreshData);
                    if (page == Page.DEVICES) actionButton = button("actions", refreshX + 78, footerY(),
                            Math.max(24, Math.min(78, Math.min(174, imageWidth - 156) - refreshX - 82)), this::openActions);
                    if (page == Page.DEVICES && state.isNetworkWatch()) {
                        favoriteButton = addRenderableWidget(DashboardButton.builder(Component.literal("☆"), ignored -> {
                            explorer.selectedDeviceId().ifPresent(id -> { preferences.toggleFavorite(id); home.selectDevice(id); });
                        }).bounds(leftPos + Math.min(174, imageWidth - 156), topPos + footerY(), 36, DashboardTheme.CONTROL_HEIGHT).build());
                    }
                } else if (page != Page.SETTINGS) refresh = button("refresh", 12, footerY(), 90, this::refreshData);
            }
            updateControls();
        }
    }
    /** Bottom button row, below the frame's footer rule. */
    private int footerY() { return imageHeight - 26; }
    private Button button(String key, int x, int y, int width, Runnable action) {
        var label = Component.translatable("screen.homelink_dashboard." + key);
        Button control = addRenderableWidget(DashboardButton.builder(label, ignored -> action.run())
                .bounds(leftPos + x, topPos + y, width, DashboardTheme.CONTROL_HEIGHT).build());
        control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        return control;
    }
    private void navigation() {
        String[] keys = {"home", "devices", "alerts_tab", "network_tab", "settings_tab"};
        Page[] pages = Page.values();
        int[] natural = new int[keys.length];
        int total = 0;
        for (int index = 0; index < keys.length; index++) {
            natural[index] = font.width(Component.translatable("screen.homelink_dashboard." + keys[index])) + 8;
            total += natural[index];
        }
        int usable = imageWidth - 40;
        int cursor = 12;
        for (int index = 0; index < keys.length; index++) {
            Page target = pages[index];
            int tabWidth = total <= usable ? natural[index] + (usable - total) / keys.length : natural[index] * usable / total;
            if (index == keys.length - 1) tabWidth = imageWidth - 12 - cursor;
            var tab = ((DashboardButton) button(keys[index], cursor, 36, tabWidth, () -> show(target)))
                    .navigation(page == target && !actionMode);
            tab.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tab.getMessage()));
            cursor += tabWidth + 4;
        }
    }
    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (state != null) {
            state.tick(); if (preferences != null) preferences.tick();
            if (manualOpen) return;
            if (actionMode) actions.tick();
            if (++uiTicks % DashboardConfig.uiInterval() == 0) {
                explorer.tick();
                if (!actionMode) switch (page) {
                case HOME -> home.tick();
                case ALERTS -> alerts.tick();
                case NETWORK -> network.tick();
                default -> { }
                }
            }
            updateControls();
        }
    }
    private void updateControls() {
        if (previous != null) previous.active = !state.loading() && state.offset() > 0;
        if (next != null) next.active = !state.loading() && state.nextOffset() >= 0;
        if (refresh != null) refresh.active = !state.loading();
        if (actionButton != null) actionButton.active = explorer.selectedDeviceId().isPresent();
        if (favoriteButton != null) {
            favoriteButton.active = explorer.selectedDeviceId().isPresent() && preferences.ready() && !preferences.pending();
            boolean favored = explorer.selectedDeviceId().filter(preferences.profile().favorites()::contains).isPresent();
            String symbol = favored ? "★" : "☆";
            if (!symbol.equals(favoriteSymbol)) {
                favoriteSymbol = symbol;
                favoriteButton.setMessage(Component.literal(symbol));
                favoriteButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
                        "screen.homelink_dashboard." + (favored ? "unfavorite" : "favorite"))));
            }
        }
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (manualOpen && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { toggleManual(); return true; }
        if (manualOpen && manual.keyPressed(key)) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void removed() {
        fr.lkdm.homelink.dashboard.network.NetworkNamePayloads.stopListening(nameListener);
        if (state != null) state.close();
        if (preferences != null) preferences.close();
        fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry.clearCache();
        super.removed();
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        DashboardTheme.frame(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 10, topPos + 57, leftPos + imageWidth - 10, topPos + 58, DashboardTheme.LINE);
        if (manualOpen) manual.render(graphics);
        else if (actionMode) actions.render(graphics, mouseX, mouseY, partialTick);
        else if (state != null) switch (page) {
            case HOME -> home.render(graphics, mouseX, mouseY, partialTick);
            case DEVICES -> explorer.render(graphics, mouseX, mouseY, partialTick);
            case ALERTS -> alerts.render(graphics, mouseX, mouseY, partialTick);
            case NETWORK -> network.render(graphics, mouseX, mouseY, partialTick);
            case SETTINGS -> settings.render(graphics, mouseX, mouseY, partialTick);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Header, right to left: manual button, connection label, status indicator (HomeLink Storage layout).
        String status = font.plainSubstrByWidth(DashboardText.value(state == null ? "OFFLINE" : state.connectionStatus()), 62);
        int statusX = imageWidth - 40 - font.width(status);
        int statusColor = state == null ? DashboardTheme.MUTED : DashboardTheme.status(state.connectionStatus());
        graphics.fill(statusX - 12, 11, statusX - 4, 19, 0xFF1D1F20);
        graphics.fill(statusX - 10, 13, statusX - 6, 17, statusColor);
        graphics.drawString(font, status, statusX, 11, DashboardTheme.MUTED, false);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(0, statusX - 32)), 14, 11, DashboardTheme.TEXT, false);
        if (manualOpen) {
            text(graphics, Component.translatable("manual.homelink_dashboard.title"), 42, DashboardTheme.ACCENT);
        } else if (state == null) {
            text(graphics, Component.translatable("screen.homelink_dashboard.setup"), 42, 0xAFB1AD);
            if (!menu.session().choices().isEmpty()) {
                var choice = menu.session().choices().get(selectedNetwork);
                text(graphics, Component.literal("[" + choice.id().toString().substring(0, 8) + "] " + choice.name()), 96, DashboardTheme.TEXT);
                text(graphics, Component.translatable("screen.homelink_dashboard." + (choice.inRange() ? "signal_available" : "signal_unavailable")),
                        132, choice.inRange() ? DashboardTheme.ONLINE : DashboardTheme.WARNING);
            }
            else text(graphics, Component.translatable("screen.homelink_dashboard.no_networks"), 98, 0xAFB1AD);
            if (!nameResult.isEmpty()) text(graphics, DashboardText.component(nameResult), 169, DashboardTheme.WARNING);
        } else if (page == Page.DEVICES) {
            text(graphics, Component.literal(state.networkName() + "  ·  " + DashboardText.value(state.role())), 60, 0xE7E5E0);
            if (!state.error().isEmpty()) text(graphics, Component.translatable("screen.homelink_dashboard.error", DashboardText.component(state.error())), 71, 0xD3B16F);
            else if (state.loading()) text(graphics, Component.translatable("screen.homelink_dashboard.loading"), 71, 0xAFB1AD);
            else text(graphics, Component.translatable("screen.homelink_dashboard." + (state.truncated() ? "watch_truncated" : "watch_counts"),
                        state.devices().size(), state.totalDeviceCount()), 71, 0xAFB1AD);
        }
    }
    private void text(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), imageWidth - 24), 12, y, color, false);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (manualOpen) return manual.mouseScrolled(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        if (!actionMode && page == Page.HOME && home != null && home.mouseScrolled(mouseX, mouseY, horizontal, vertical)) return true;
        if (!actionMode && page == Page.DEVICES && explorer != null && explorer.mouseScrolled(mouseX, mouseY, horizontal, vertical)) return true;
        if (!actionMode && page == Page.ALERTS && alerts != null && alerts.mouseScrolled(mouseX, mouseY, horizontal, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (manualOpen) return super.mouseClicked(mouseX, mouseY, button);
        // Container screens consume otherwise unhandled clicks even when there are no slots.
        // Route custom page regions first; each view excludes its native button/input area.
        if (!actionMode && page == Page.HOME && home != null && home.mouseClicked(mouseX, mouseY, button)) return true;
        if (!actionMode && page == Page.ALERTS && alerts != null && alerts.mouseClicked(mouseX, mouseY, button)) return true;
        if (!actionMode && page == Page.DEVICES && explorer != null && explorer.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
