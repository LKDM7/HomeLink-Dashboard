package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import fr.lkdm.homelink.dashboard.network.DiscoveryPayloads;
import fr.lkdm.homelink.dashboard.network.MachineListing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Machines inside the network's radio area (server and relays), as listed by the server for this player,
 * with an add button per machine. The server re-checks every right; this view never changes a binding itself.
 */
public final class DiscoveryView {
    private static final int ROW_HEIGHT = 30;
    private static final int LIST_TOP = 36;
    private final Font font;
    private final int containerId;
    private List<MachineListing.Entry> entries = List.of();
    private ActionResult.Code listingCode;
    private String message = "";
    private int total;
    private boolean pending;
    private boolean requested;
    private Button addAll;
    private Runnable rebuild = () -> { };
    private int scroll;
    private int x;
    private int y;
    private int width;
    private int height;

    public DiscoveryView(Font font, int containerId) {
        this.font = font;
        this.containerId = containerId;
    }

    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> addWidget, Runnable rebuild) {
        this.x = x;
        this.y = y;
        this.width = Math.max(64, width);
        this.height = Math.max(40, height);
        this.rebuild = rebuild;
        var label = Component.translatable("screen.homelink_dashboard.discover_add_all");
        addAll = DashboardButton.builder(label, ignored -> send(DiscoveryPayloads.Operation.ADD_ALL, Optional.empty()))
                .bounds(x + this.width - 110, y, 110, DashboardTheme.CONTROL_HEIGHT).build();
        addAll.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label));
        addWidget.accept(addAll);
        if (!requested) refresh();
        updateButtons();
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    public void refresh() { send(DiscoveryPayloads.Operation.LIST, Optional.empty()); }
    /** Same request as the row's button. */
    public void add(UUID machine) { send(DiscoveryPayloads.Operation.ADD, Optional.of(machine)); }
    public boolean pending() { return pending; }
    public List<MachineListing.Entry> entries() { return entries; }

    private void send(DiscoveryPayloads.Operation operation, Optional<UUID> device) {
        if (pending) return;
        pending = true;
        requested = true;
        if (operation != DiscoveryPayloads.Operation.LIST) message = "";
        updateButtons();
        PacketDistributor.sendToServer(new DiscoveryPayloads.Request(containerId, operation, device));
    }

    public void receive(DiscoveryPayloads.Response response) {
        if (response.containerId() != containerId) return;
        pending = false;
        listingCode = response.listing().code();
        entries = response.listing().entries();
        total = response.listing().total();
        if (response.operation() != DiscoveryPayloads.Operation.LIST)
            message = response.result() == ActionResult.Code.SUCCESS ? "discover_added" : "discover_failed_" + response.result().name().toLowerCase(java.util.Locale.ROOT);
        scroll = Math.clamp(scroll, 0, maxScroll());
        rebuild.run();
    }

    private void updateButtons() {
        if (addAll != null) addAll.active = !pending && entries.stream().anyMatch(MachineListing.Entry::canAdd);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        text(graphics, heading(), x, y + 5, width - 118, DashboardTheme.TEXT);
        if (!message.isEmpty())
            text(graphics, Component.translatable("screen.homelink_dashboard." + message).getString(), x, y + 22, width,
                    message.equals("discover_added") ? DashboardTheme.ONLINE : DashboardTheme.WARNING);
        int top = y + LIST_TOP;
        if (listingCode != null && listingCode != ActionResult.Code.SUCCESS || entries.isEmpty()) {
            DashboardTheme.panel(graphics, x, top, width, 44);
            String key = listingCode == null || pending ? "discover_loading"
                    : listingCode == ActionResult.Code.SUCCESS ? "discover_empty" : "discover_denied_" + listingCode.name().toLowerCase(java.util.Locale.ROOT);
            int lineY = top + 8;
            for (var line : font.split(Component.translatable("screen.homelink_dashboard." + key), Math.max(16, width - 16))) {
                graphics.drawString(font, line, x + 8, lineY, DashboardTheme.MUTED, false);
                lineY += 11;
            }
            return;
        }
        graphics.enableScissor(x, top, x + width, y + height);
        for (int index = 0; index < entries.size(); index++) {
            int rowY = top + index * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < top) continue;
            if (rowY > y + height) break;
            renderRow(graphics, entries.get(index), rowY, mouseX, mouseY);
        }
        graphics.disableScissor();
        int maximum = maxScroll();
        if (maximum > 0) {
            int available = y + height - top;
            int thumb = Math.max(6, available * available / (available + maximum));
            int thumbY = top + (available - thumb) * scroll / maximum;
            graphics.fill(x + width - 2, top, x + width, y + height, 0xFF202224);
            graphics.fill(x + width - 2, thumbY, x + width, thumbY + thumb, 0xFF858982);
        }
    }

    private String heading() {
        if (listingCode != ActionResult.Code.SUCCESS) return Component.translatable("screen.homelink_dashboard.discover_title").getString();
        long addable = entries.stream().filter(MachineListing.Entry::canAdd).count();
        return Component.translatable("screen.homelink_dashboard.discover_heading", total, addable).getString();
    }

    private void renderRow(GuiGraphics graphics, MachineListing.Entry entry, int top, int mouseX, int mouseY) {
        int rowWidth = width - 4, rowHeight = ROW_HEIGHT - 2;
        DashboardTheme.panel(graphics, x, top, rowWidth, rowHeight);
        int color = switch (entry.state()) {
            case FREE -> DashboardTheme.ACCENT;
            case IN_NETWORK -> DashboardTheme.ONLINE;
            case OTHER_NETWORK -> DashboardTheme.WARNING;
            case UNSUPPORTED -> DashboardTheme.MUTED;
        };
        graphics.fill(x + 5, top + 5, x + 11, top + 11, 0xFF1D1F20);
        graphics.fill(x + 6, top + 6, x + 10, top + 10, color);
        int buttonWidth = 86;
        int textWidth = rowWidth - buttonWidth - 26;
        text(graphics, entry.name(), x + 15, top + 5, textWidth, DashboardTheme.TEXT);
        String details = MachinesView.typeLabel(entry.type()).getString() + "  ·  "
                + Component.translatable("screen.homelink_dashboard.discover_distance", entry.distance()).getString() + "  ·  " + stateLabel(entry);
        text(graphics, details, x + 15, top + 16, textWidth, DashboardTheme.MUTED);
        int buttonX = x + rowWidth - buttonWidth - 5, buttonY = top + 5;
        if (entry.canAdd()) {
            boolean hover = !pending && mouseX >= buttonX && mouseX < buttonX + buttonWidth && mouseY >= buttonY && mouseY < buttonY + DashboardTheme.CONTROL_HEIGHT;
            graphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + DashboardTheme.CONTROL_HEIGHT, 0xFF181A1B);
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + DashboardTheme.CONTROL_HEIGHT - 1,
                    pending ? 0xFF36383A : hover ? 0xFF5A5D60 : 0xFF474A4D);
            graphics.fill(buttonX + 1, buttonY + 1, buttonX + buttonWidth - 1, buttonY + 2, 0xFF74787A);
            String label = Component.translatable("screen.homelink_dashboard."
                    + (entry.state() == MachineListing.State.OTHER_NETWORK ? "discover_move" : "discover_add")).getString();
            label = font.plainSubstrByWidth(label, buttonWidth - 8);
            graphics.drawString(font, label, buttonX + (buttonWidth - font.width(label)) / 2, buttonY + 5,
                    pending ? 0xFF91948F : DashboardTheme.TEXT, false);
        } else if (entry.state() == MachineListing.State.FREE || entry.state() == MachineListing.State.OTHER_NETWORK) {
            String locked = Component.translatable("screen.homelink_dashboard.discover_locked").getString();
            text(graphics, locked, x + rowWidth - Math.min(buttonWidth, font.width(locked)) - 5, top + 10, buttonWidth, DashboardTheme.MUTED);
        }
    }

    private static String stateLabel(MachineListing.Entry entry) {
        return switch (entry.state()) {
            case FREE -> Component.translatable("screen.homelink_dashboard.discover_state_free").getString();
            case IN_NETWORK -> Component.translatable("screen.homelink_dashboard.discover_state_member").getString();
            case UNSUPPORTED -> Component.translatable("screen.homelink_dashboard.discover_state_unsupported").getString();
            case OTHER_NETWORK -> entry.networkName().isEmpty()
                    ? Component.translatable("screen.homelink_dashboard.discover_state_foreign").getString()
                    : Component.translatable("screen.homelink_dashboard.discover_state_other", entry.networkName()).getString();
        };
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = y + LIST_TOP;
        if (button != 0 || pending || mouseX < x || mouseX >= x + width || mouseY < top || mouseY >= y + height) return false;
        int index = (int) Math.floor((mouseY - top + scroll) / ROW_HEIGHT);
        if (index < 0 || index >= entries.size()) return false;
        var entry = entries.get(index);
        int rowTop = top + index * ROW_HEIGHT - scroll;
        int buttonX = x + width - 4 - 86 - 5;
        if (!entry.canAdd() || mouseX < buttonX || mouseX >= buttonX + 86 || mouseY < rowTop + 5 || mouseY >= rowTop + 5 + DashboardTheme.CONTROL_HEIGHT) return false;
        send(DiscoveryPayloads.Operation.ADD, Optional.of(entry.id()));
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < x || mouseX >= x + width || mouseY < y + LIST_TOP || mouseY >= y + height) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 24, 0, maxScroll());
        return true;
    }

    private int maxScroll() { return Math.max(0, entries.size() * ROW_HEIGHT - Math.max(0, height - LIST_TOP)); }

    private void text(GuiGraphics graphics, String value, int left, int top, int available, int color) {
        if (available <= 0) return;
        String fitted = value;
        if (font.width(fitted) > available) fitted = font.plainSubstrByWidth(value, Math.max(0, available - font.width("…"))) + "…";
        graphics.drawString(font, fitted, left, top, color, false);
    }
}
