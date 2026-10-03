package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;

import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/** Public HomeCore network metadata and physical network guidance. */
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
    private final Consumer<String> submitName;
    private final Runnable rebuild;
    private boolean editing;
    private boolean pending;
    private String draft = "";
    private String result = "";
    private Button renameButton;
    private Button saveButton;

    public NetworkView(DashboardClientState state, Font font, Consumer<String> submitName, Runnable rebuild) {
        this.state = state; this.font = font; this.submitName = submitName; this.rebuild = rebuild;
    }
    public void nameResult(fr.lkdm.homecore.api.action.ActionResult.Code code) { pending = false; result = code.name(); }
    private boolean canRename() { return state.role().equals("OWNER") || state.role().equals("ADMIN"); }
    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        renameButton = null; saveButton = null;
        if (editing) {
            var input = HomeLinkUi.input(new net.minecraft.client.gui.components.EditBox(font, x, y + 26, width,
                    HomeLinkTheme.CONTROL_HEIGHT, Component.translatable("screen.homelink_dashboard.network_name")));
            input.setMaxLength(128); input.setValue(draft);
            input.setResponder(value -> draft = value);
            addWidget.accept(input);
            saveButton = HomeLinkButton.builder(Component.translatable("screen.homelink_dashboard.save_name"), button -> {
                pending = true; result = ""; submitName.accept(draft);
            }).bounds(x, y + 54, (width - 4) / 2, HomeLinkTheme.CONTROL_HEIGHT).build();
            addWidget.accept(saveButton);
            addWidget.accept(HomeLinkButton.builder(Component.translatable("screen.homelink_dashboard.cancel_name"), button -> {
                editing = false; rebuild.run();
            }).bounds(x + (width + 4) / 2, y + 54, (width - 4) / 2, HomeLinkTheme.CONTROL_HEIGHT).build());
            tick();
            return;
        }
        renameButton = HomeLinkButton.builder(Component.translatable("screen.homelink_dashboard.rename"), button -> {
            editing = true; draft = state.networkName(); result = ""; rebuild.run();
        }).bounds(x + Math.max(0, width - 184), y, 90, HomeLinkTheme.CONTROL_HEIGHT).build();
        addWidget.accept(renameButton);
        addWidget.accept(HomeLinkButton.builder(Component.translatable("screen.homelink_dashboard.network_advanced"), button -> advanced = !advanced)
                .bounds(x + Math.max(0, width - 90), y, Math.min(90, width), HomeLinkTheme.CONTROL_HEIGHT).build());
        tick();
    }
    public void tick() {
        if (renameButton != null) renameButton.active = canRename();
        if (saveButton != null) saveButton.active = canRename() && !pending
                && fr.lkdm.homelink.dashboard.network.NetworkNames.isValid(draft);
        if (revision == state.revision()) return;
        revision = state.revision();
        online = warning = offline = 0;
        for (var device : state.devices()) {
            switch (device.status()) { case "ONLINE" -> online++; case "WARNING" -> warning++; default -> offline++; }
        }
    }
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (height <= 0) return;
        if (editing) {
            text(graphics, Component.translatable("screen.homelink_dashboard.network_name"), 6, HomeLinkTheme.TEXT);
            text(graphics, Component.translatable("screen.homelink_dashboard.name_hint"), 103, HomeLinkTheme.MUTED);
            if (!result.isEmpty()) text(graphics, DashboardText.component(result), 83,
                    result.equals("SUCCESS") ? HomeLinkTheme.ONLINE : HomeLinkTheme.WARNING);
            return;
        }
        graphics.enableScissor(x, y, x + width, y + height);
        HomeLinkUi.panel(graphics, x, y + 21, width, 72);
        graphics.fill(x, y + 21, x + 2, y + 93, HomeLinkTheme.statusColor(state.connectionStatus()));
        graphics.fill(x + 8, y + 36, x + width - 8, y + 37, HomeLinkTheme.LINE);
        graphics.drawString(font, font.plainSubstrByWidth(state.networkName(), Math.max(0, width - 192)), x, y + 5, HomeLinkTheme.TEXT, false);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_connection", DashboardText.component(state.connectionStatus())), 25,
                state.connectionStatus().equals("CONNECTED") ? HomeLinkTheme.ONLINE : HomeLinkTheme.WARNING);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_owner", state.ownerId().map(Object::toString).orElse("?")), 39, HomeLinkTheme.MUTED);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_members", state.memberCount(), DashboardText.component(state.role())), 53, HomeLinkTheme.MUTED);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_devices", state.totalDeviceCount(), state.devices().size()), 67, HomeLinkTheme.MUTED);
        text(graphics, Component.translatable("screen.homelink_dashboard.network_states", online, warning, offline), 81, HomeLinkTheme.MUTED);
        if (advanced) text(graphics, Component.literal("UUID: " + state.networkId()), 101, HomeLinkTheme.MUTED);
        else text(graphics, Component.translatable("screen.homelink_dashboard.network_live_scope"), 101, HomeLinkTheme.MUTED);
        graphics.disableScissor();
    }
    private void text(GuiGraphics graphics, Component text, int offset, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(0, width - 16)), x + 8, y + offset, color, false);
    }
    public void showAdvanced(boolean value) { advanced = value; }
}
