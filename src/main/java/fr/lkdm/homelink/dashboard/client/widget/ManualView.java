package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homelink.dashboard.client.rendering.DashboardTheme;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Local reference pages, independent of network access and device integrations. */
public final class ManualView {
    private static final String[] CHAPTERS = {"start", "devices", "home", "alerts", "trouble"};
    private final Font font;
    private int chapter, offset, x, y, width, height;
    private record Line(FormattedCharSequence text, boolean heading) { }
    private List<Line> lines = List.of();

    public ManualView(Font font) { this.font = font; }
    public int chapter() { return chapter; }
    public int offset() { return offset; }
    private Component text(String suffix) {
        return Component.translatable("manual.homelink_dashboard." + CHAPTERS[chapter] + "." + suffix);
    }
    public void init(int x, int y, int width, int height, Consumer<AbstractWidget> add, Runnable rebuild) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        List<Line> wrapped = new ArrayList<>();
        for (String paragraph : text("body").getString().split("\n\n")) {
            if (!wrapped.isEmpty()) wrapped.add(new Line(FormattedCharSequence.EMPTY, false));
            for (String row : paragraph.split("\n")) {
                boolean heading = row.startsWith("# ");
                MutableComponent content = Component.empty();
                String[] spans = (heading ? row.substring(2) : row).split("\\*\\*", -1);
                for (int i = 0; i < spans.length; i++) {
                    boolean emphasized = heading || i % 2 == 1;
                    content.append(Component.literal(spans[i]).withStyle(style -> style
                            .withColor(emphasized ? DashboardTheme.ACCENT : DashboardTheme.TEXT)
                            .withBold(heading)));
                }
                for (FormattedCharSequence line : font.split(content, width - 28)) wrapped.add(new Line(line, heading));
            }
        }
        lines = List.copyOf(wrapped);
        offset = Math.min(offset, maxOffset());
        var previous = DashboardButton.builder(Component.literal("<"), ignored -> {
            chapter--; offset = 0; rebuild.run();
        }).bounds(x, y, 24, 20).build();
        previous.active = chapter > 0;
        previous.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("manual.homelink_dashboard.previous")));
        add.accept(previous);
        var next = DashboardButton.builder(Component.literal(">"), ignored -> {
            chapter++; offset = 0; rebuild.run();
        }).bounds(x + width - 24, y, 24, 20).build();
        next.active = chapter < CHAPTERS.length - 1;
        next.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("manual.homelink_dashboard.next")));
        add.accept(next);
        var up = DashboardButton.builder(Component.translatable("manual.homelink_dashboard.up"), ignored -> offset = Math.max(0, offset - visibleLines()))
                .bounds(x, y + height - 20, (width - 4) / 2, 20).build();
        var down = DashboardButton.builder(Component.translatable("manual.homelink_dashboard.down"), ignored -> offset = Math.min(maxOffset(), offset + visibleLines()))
                .bounds(x + (width - 4) / 2 + 4, y + height - 20, (width - 4) / 2, 20).build();
        add.accept(up); add.accept(down);
    }
    private int visibleLines() { return Math.max(1, (height - 56) / 14); }
    private int maxOffset() { return Math.max(0, lines.size() - visibleLines()); }
    public void render(GuiGraphics graphics) {
        String heading = (chapter + 1) + "/" + CHAPTERS.length + "  " + text("title").getString();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(heading, width - 60), x + width / 2, y + 6, DashboardTheme.ACCENT);
        DashboardTheme.panel(graphics, x, y + 25, width, height - 49);
        graphics.enableScissor(x + 4, y + 28, x + width - 4, y + height - 25);
        for (int i = offset; i < Math.min(lines.size(), offset + visibleLines()); i++) {
            Line line = lines.get(i);
            int lineY = y + 30 + (i - offset) * 14;
            if (line.heading()) {
                graphics.fill(x + 6, lineY - 2, x + width - 9, lineY + 11, DashboardTheme.HEADER);
                graphics.fill(x + 6, lineY - 2, x + 8, lineY + 11, DashboardTheme.ACCENT);
            }
            graphics.drawString(font, line.text(), x + 12, lineY, DashboardTheme.TEXT, false);
        }
        graphics.disableScissor();
        if (maxOffset() > 0) {
            int track = Math.max(1, height - 59);
            int thumb = Math.max(6, track * visibleLines() / lines.size());
            int top = y + 30 + (track - thumb) * offset / maxOffset();
            graphics.fill(x + width - 5, top, x + width - 3, top + thumb, DashboardTheme.ACCENT);
        }
    }
    public boolean mouseScrolled(double mx, double my, double vertical) {
        if (mx < x || mx >= x + width || my < y + 25 || my >= y + height - 24) return false;
        offset = Math.max(0, Math.min(maxOffset(), offset - (int) (vertical * 3)));
        return true;
    }
}
