package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.DeviceActionView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/** A bounded action editor; never executes device handlers or mutates metrics client-side. */
public final class ActionPanel {
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
    private String localError = "";

    public ActionPanel(DashboardClientState state, Font font) { this.state = state; this.font = font; }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        editors.clear();
        previous = DashboardButton.builder(Component.literal("<"), button -> changeAction(-1)).bounds(x, y, 24, DashboardTheme.CONTROL_HEIGHT).build();
        next = DashboardButton.builder(Component.literal(">"), button -> changeAction(1)).bounds(x + width - 24, y, 24, DashboardTheme.CONTROL_HEIGHT).build();
        addWidget.accept(previous);
        addWidget.accept(next);
        for (String type : ActionControlRegistry.TYPES) editors.put(type,
                ActionControlRegistry.create(type, font, x, y + 42, width, addWidget));
        editors.values().forEach(editor -> editor.onExpandedChange(this::updateDropdownControls));
        submit = DashboardButton.builder(label("execute_action"), button -> {
            if (action == null || !editors.containsKey(action.type())) return;
            try { submit(editors.get(action.type()).value()); }
            catch (IllegalArgumentException exception) { localError = "INVALID_PARAMETER: " + exception.getMessage(); }
        }).bounds(x, y + 68, Math.min(160, width), DashboardTheme.CONTROL_HEIGHT).build();
        addWidget.accept(submit);
        action = null;
        tick();
    }

    public void selectDevice(UUID deviceId) {
        this.deviceId = deviceId;
        actionIndex = 0;
        action = null;
        localError = "";
        tick();
    }

    private void changeAction(int direction) {
        if (device == null || device.actions().isEmpty()) return;
        actionIndex = Math.floorMod(actionIndex + direction, device.actions().size());
        action = null;
        localError = "";
        tick();
    }

    public boolean selectAction(String actionId) {
        if (device == null || state.actionPending()) return false;
        for (int index = 0; index < device.actions().size(); index++) {
            if (!device.actions().get(index).id().equals(actionId)) continue;
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
        int count = device == null ? 0 : device.actions().size();
        actionIndex = Math.clamp(actionIndex, 0, Math.max(0, count - 1));
        DeviceActionView updated = count == 0 ? null : device.actions().get(actionIndex);
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
        }
    }

    private boolean dropdownExpanded() { return editors.values().stream().anyMatch(ActionControlRegistry.Editor::expanded); }
    private void updateDropdownControls() {
        if (submit == null) return;
        boolean expanded = dropdownExpanded();
        submit.visible = !expanded;
        int count = device == null ? 0 : device.actions().size();
        previous.active = next.active = !expanded && count > 1 && !state.actionPending();
    }

    private boolean actionable() {
        return action != null && device != null && device.status().equals("ONLINE") && state.canControl(action)
                && action.supported() && editors.containsKey(action.type()) && !state.actionPending();
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
        if (action == null) text(graphics, label(device == null ? "device_removed" : "no_actions").getString(), 28, 0xAFB1AD);
        else {
            graphics.drawString(font, font.plainSubstrByWidth((actionIndex + 1) + "/" + device.actions().size()
                    + " " + action.name(), Math.max(0, width - 60)), x + 30, y + 6, 0xE7E5E0, false);
            String constraints = action.min() == null && action.max() == null ? ""
                    : (action.min() == null ? "−∞" : action.min()) + " .. " + (action.max() == null ? "∞" : action.max())
                            + (action.step() == null ? "" : " / " + action.step());
            String description = action.description().isBlank() ? label("action_hint").getString() : action.description();
            text(graphics, constraints.isEmpty() ? description : constraints + " · " + description, 27, 0xAFB1AD);
        }
        String result = localError.isEmpty() ? DashboardText.value(state.lastActionCode()) + " " + DashboardText.message(state.lastActionMessage()) : DashboardText.message(localError);
        if (state.actionPending()) result = label("action_pending").getString();
        else if (action != null && !action.supported()) result = label("unsupported_action").getString();
        else if (device != null && !device.status().equals("ONLINE")) result = DashboardText.value("DEVICE_OFFLINE");
        else if (action != null && !state.canControl(action)) result = DashboardText.value("DENIED");
        if (!dropdownExpanded()) text(graphics, result, 94, (localError.isEmpty() && state.lastActionCode().equals("SUCCESS") && actionable()) ? 0xA1BD92 : 0xD3B16F);
        graphics.disableScissor();
    }

    private void text(GuiGraphics graphics, String value, int offset, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(0, width)), x, y + offset, color, false);
    }
    private static Component label(String key) { return Component.translatable("screen.homelink_dashboard." + key); }
    public DeviceActionView currentAction() { return action; }
}
