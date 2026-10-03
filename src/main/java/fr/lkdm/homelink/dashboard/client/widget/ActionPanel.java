package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;


import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.DeviceActionView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/**
 * A bounded action editor; never executes device handlers or mutates metrics client-side.
 * The top row holds HomeCore's standard power switch and rename field; the machine's own actions follow.
 */
public final class ActionPanel {
    /** Height of the power/rename row above the machine's own actions. */
    private static final int MACHINE_ROW = 24;
    private static final int CONFIRM_WIDTH = 24;
    private final DashboardClientState state;
    private final Font font;
    private final Map<String, ActionControlRegistry.Editor> editors = new HashMap<>();
    private UUID deviceId;
    private DebugDeviceView device;
    private DeviceActionView action;
    private int actionIndex;
    private int x;
    private int y;
    private int width;
    private int height;
    private Button previous;
    private Button next;
    private Button submit;
    private Button power;
    private EditBox rename;
    private Button confirmRename;
    /** Device name the rename field was last filled with, to follow renames the player has not started editing. */
    private String shownName;
    private String localError = "";

    public ActionPanel(DashboardClientState state, Font font) { this.state = state; this.font = font; }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        editors.clear();
        int powerWidth = Math.min(120, Math.max(60, width / 3));
        power = HomeLinkButton.builder(Component.empty(), button -> togglePower())
                .bounds(x, y, powerWidth, HomeLinkTheme.CONTROL_HEIGHT).build();
        rename = HomeLinkUi.input(new EditBox(font, x + powerWidth + 6, y, Math.max(20, width - powerWidth - CONFIRM_WIDTH - 10),
                HomeLinkTheme.CONTROL_HEIGHT, label("rename_hint")));
        rename.setHint(label("rename_hint"));
        rename.setMaxLength(50);
        rename.setResponder(ignored -> updateMachineControls());
        confirmRename = HomeLinkButton.builder(Component.literal("✓"), button -> submitRename())
                .bounds(x + width - CONFIRM_WIDTH, y, CONFIRM_WIDTH, HomeLinkTheme.CONTROL_HEIGHT).build();
        confirmRename.setTooltip(Tooltip.create(label("rename_confirm")));
        addWidget.accept(power);
        addWidget.accept(rename);
        addWidget.accept(confirmRename);
        shownName = null;
        int top = y + MACHINE_ROW;
        previous = HomeLinkButton.builder(Component.literal("<"), button -> changeAction(-1)).bounds(x, top, 24, HomeLinkTheme.CONTROL_HEIGHT).build();
        next = HomeLinkButton.builder(Component.literal(">"), button -> changeAction(1)).bounds(x + width - 24, top, 24, HomeLinkTheme.CONTROL_HEIGHT).build();
        addWidget.accept(previous);
        addWidget.accept(next);
        for (String type : ActionControlRegistry.TYPES) editors.put(type,
                ActionControlRegistry.create(type, font, x, top + 36, width, addWidget));
        editors.values().forEach(editor -> editor.onExpandedChange(this::updateDropdownControls));
        submit = HomeLinkButton.builder(label("execute_action"), button -> {
            if (action == null || !editors.containsKey(action.type())) return;
            try { submit(editors.get(action.type()).value()); }
            catch (IllegalArgumentException exception) { localError = "INVALID_PARAMETER: " + exception.getMessage(); }
        }).bounds(x, top + 60, Math.min(160, width), HomeLinkTheme.CONTROL_HEIGHT).build();
        addWidget.accept(submit);
        action = null;
        tick();
    }

    public void selectDevice(UUID deviceId) {
        this.deviceId = deviceId;
        actionIndex = 0;
        action = null;
        localError = "";
        shownName = null;
        tick();
    }

    /** The machine's own actions; power and rename have their own row. */
    private List<DeviceActionView> actions() {
        return device == null ? List.of() : device.actions().stream().filter(candidate -> !candidate.standard()).toList();
    }

    private void changeAction(int direction) {
        List<DeviceActionView> actions = actions();
        if (actions.isEmpty()) return;
        actionIndex = Math.floorMod(actionIndex + direction, actions.size());
        action = null;
        localError = "";
        tick();
    }

    public boolean selectAction(String actionId) {
        if (device == null || state.actionPending()) return false;
        List<DeviceActionView> actions = actions();
        for (int index = 0; index < actions.size(); index++) {
            if (!actions.get(index).id().equals(actionId)) continue;
            actionIndex = index;
            action = null;
            localError = "";
            tick();
            return true;
        }
        return false;
    }

    public void tick() {
        device = state.devices().stream().filter(candidate -> candidate.id().equals(deviceId)).findFirst().orElse(null);
        List<DeviceActionView> actions = actions();
        int count = actions.size();
        actionIndex = Math.clamp(actionIndex, 0, Math.max(0, count - 1));
        DeviceActionView updated = count == 0 ? null : actions.get(actionIndex);
        if (!java.util.Objects.equals(action, updated)) {
            action = updated;
            if (action != null && action.supported() && editors.containsKey(action.type())) editors.get(action.type()).configure(action);
        }
        boolean enabled = actionable();
        for (var entry : editors.entrySet()) entry.getValue().show(action != null && action.supported() && entry.getKey().equals(action.type()), enabled);
        if (submit != null) {
            submit.active = enabled;
            previous.active = count > 1 && !state.actionPending();
            next.active = count > 1 && !state.actionPending();
            updateDropdownControls();
            updateMachineControls();
        }
    }

    private boolean dropdownExpanded() { return editors.values().stream().anyMatch(ActionControlRegistry.Editor::expanded); }
    private void updateDropdownControls() {
        if (submit == null) return;
        boolean expanded = dropdownExpanded();
        // A machine with only power and rename (a battery, for example) shows no empty action editor.
        submit.visible = !expanded && action != null;
        int count = actions().size();
        previous.active = next.active = !expanded && count > 1 && !state.actionPending();
        previous.visible = next.visible = count > 0;
    }

    private boolean actionable() {
        return usable(action) && editors.containsKey(action.type());
    }

    private boolean usable(DeviceActionView candidate) {
        return candidate != null && device != null && candidate.availableWhen(device.status()) && state.canControl(candidate)
                && candidate.supported() && !state.actionPending();
    }

    private DeviceActionView standard(String id) { return device == null ? null : device.action(id).orElse(null); }

    /** Power label follows the machine; the rename field follows renames made elsewhere until the player edits it. */
    private void updateMachineControls() {
        if (power == null) return;
        DeviceActionView switchAction = standard(DeviceActionView.POWER);
        boolean on = device == null || device.powered() == null || device.powered();
        power.setMessage(label(switchAction == null ? "power_unavailable" : on ? "power_off" : "power_on"));
        power.active = usable(switchAction);
        power.setTooltip(Tooltip.create(label(switchAction == null ? "power_unavailable_hint" : on ? "power_state_on" : "power_state_off")));
        DeviceActionView renameAction = standard(DeviceActionView.RENAME);
        String name = device == null ? "" : device.name();
        if (!name.equals(shownName) && (shownName == null || rename.getValue().strip().equals(shownName))) {
            shownName = name;
            rename.setValue(name);
        }
        boolean renamable = usable(renameAction);
        rename.setEditable(renamable);
        if (renameAction != null) rename.setMaxLength(Math.clamp(renameAction.maxLength(), 1, 50));
        String value = rename.getValue().strip();
        confirmRename.active = renamable && !value.equals(name) && value.indexOf('§') < 0;
        rename.setTooltip(renameAction == null ? Tooltip.create(label("rename_unavailable")) : null);
    }

    private void togglePower() {
        DeviceActionView switchAction = standard(DeviceActionView.POWER);
        if (!usable(switchAction)) return;
        localError = "";
        state.executeAction(deviceId, switchAction, !(device.powered() == null || device.powered()));
        tick();
    }

    /** Enter in the rename field confirms the new name. */
    public boolean keyPressed(int key) {
        if (rename == null || !rename.isFocused() || key != org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && key != org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) return false;
        if (confirmRename.active) submitRename();
        return true;
    }

    /** Smoke tests use the same authoritative HomeCore request path as the rename field. */
    public boolean submitRename(String name) {
        rename.setValue(name);
        return submitRename();
    }

    private boolean submitRename() {
        DeviceActionView renameAction = standard(DeviceActionView.RENAME);
        if (!usable(renameAction)) return false;
        localError = "";
        boolean sent = state.executeAction(deviceId, renameAction, rename.getValue().strip());
        rename.setFocused(false);
        tick();
        return sent;
    }

    /** Smoke tests use the same authoritative HomeCore request path as the visible controls. */
    public boolean submit(Object parameter) {
        if (!actionable()) return false;
        localError = "";
        boolean sent = state.executeAction(deviceId, action, parameter);
        tick();
        return sent;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(x, y, x + width, y + height);
        int top = MACHINE_ROW;
        if (action == null) text(graphics, label(device == null ? "device_removed"
                : device.actions().stream().anyMatch(DeviceActionView::standard) ? "no_other_actions" : "no_actions").getString(), top + 6, HomeLinkTheme.MUTED);
        else {
            graphics.drawString(font, font.plainSubstrByWidth((actionIndex + 1) + "/" + actions().size()
                    + " " + action.name(), Math.max(0, width - 60)), x + 30, y + top + 6, HomeLinkTheme.TEXT, false);
            String constraints = action.min() == null && action.max() == null ? ""
                    : (action.min() == null ? "−∞" : action.min()) + " .. " + (action.max() == null ? "∞" : action.max())
                            + (action.step() == null ? "" : " / " + action.step());
            String description = action.description().isBlank() ? label("action_hint").getString() : action.description();
            text(graphics, constraints.isEmpty() ? description : constraints + " · " + description, top + 24, HomeLinkTheme.MUTED);
        }
        String result = localError.isEmpty() ? DashboardText.value(state.lastActionCode()) + " " + DashboardText.message(state.lastActionMessage()) : DashboardText.message(localError);
        if (state.actionPending()) result = label("action_pending").getString();
        else if (action != null && !action.supported()) result = label("unsupported_action").getString();
        else if (action != null && device != null && !action.availableWhen(device.status())) result = DashboardText.value("DEVICE_OFFLINE");
        else if (action != null && !state.canControl(action)) result = DashboardText.value("DENIED");
        boolean success = localError.isEmpty() && state.lastActionCode().equals("SUCCESS");
        // The result sits beside Execute so that the panel still fits a short window (about 104 px).
        if (!dropdownExpanded()) {
            int left = submit.getWidth() + 6;
            graphics.drawString(font, font.plainSubstrByWidth(result, Math.max(0, width - left)), x + left, y + top + 66,
                    success ? HomeLinkTheme.ONLINE : HomeLinkTheme.WARNING, false);
        }
        graphics.disableScissor();
    }

    private void text(GuiGraphics graphics, String value, int offset, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, width)), x, y + offset, color, false);
    }
    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
    public DeviceActionView currentAction() { return action; }
    public Button powerButton() { return power; }
}
