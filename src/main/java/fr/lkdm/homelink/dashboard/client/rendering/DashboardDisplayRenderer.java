package fr.lkdm.homelink.dashboard.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lkdm.homelink.dashboard.block.AccessPointStatus;
import fr.lkdm.homelink.dashboard.block.DashboardDisplayBlock;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.client.state.DashboardClientState;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.DisplaySummaryClient;
import fr.lkdm.homelink.dashboard.network.DisplaySummary;
import fr.lkdm.homelink.dashboard.dashboard.widget.DashboardWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** A single, depth-tested surface spanning the display, with recipient-specific favorite values. */
public final class DashboardDisplayRenderer implements BlockEntityRenderer<DashboardDisplayBlockEntity> {
    private static final float PIXELS_PER_BLOCK = 128;
    private static final int BACKGROUND = 0xFF181C1D;
    private static final int RULE = 0xFF394142;
    private static final int TILE = 0xFF232829;
    /** Height in pixels of one dashboard grid row: a three-row widget holds a name and two values. */
    private static final int WIDGET_ROW = 12;
    private final Font font;

    public DashboardDisplayRenderer(BlockEntityRendererProvider.Context context) {
        font = context.getFont();
    }

    @Override public int getViewDistance() { return 16; }

    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(DashboardDisplayBlockEntity display) {
        var state = display.getBlockState();
        var size = state.getValue(DashboardDisplayBlock.SIZE);
        var pos = display.getBlockPos();
        var opposite = pos.relative(state.getValue(DashboardDisplayBlock.FACING).getCounterClockWise(), size.width() - 1)
                .above(size.height() - 1);
        return new net.minecraft.world.phys.AABB(pos).minmax(new net.minecraft.world.phys.AABB(opposite));
    }

    @Override public boolean shouldRender(DashboardDisplayBlockEntity display, Vec3 camera) {
        if (!BlockEntityRenderer.super.shouldRender(display, camera)) return false;
        var facing = display.getBlockState().getValue(DashboardDisplayBlock.FACING);
        // Never show mirrored text on the back of a freestanding screen.
        return camera.subtract(Vec3.atCenterOf(display.getBlockPos())).dot(Vec3.atLowerCornerOf(facing.getNormal())) > -0.3048;
    }

    @Override public void render(DashboardDisplayBlockEntity display, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var state = display.getBlockState();
        if (state.getValue(DashboardDisplayBlock.PART) != DashboardDisplayBlock.Part.BOTTOM_LEFT) return;
        var size = state.getValue(DashboardDisplayBlock.SIZE);
        float width = (size.width() - 0.1F) * PIXELS_PER_BLOCK;
        float height = (size.height() - 0.36875F) * PIXELS_PER_BLOCK;
        var summary = DisplaySummaryClient.get(display);
        if (summary != null && summary.mode() == DisplaySummary.Mode.LIVE
                && state.getValue(DashboardDisplayBlock.STATUS) != AccessPointStatus.ONLINE)
            summary = DisplaySummary.empty(DisplaySummary.Mode.OFFLINE);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        float rotation = switch (state.getValue(DashboardDisplayBlock.FACING)) {
            case NORTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> 0;
        };
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        // Local X follows facing.counterClockWise; the panel sits just ahead of the model's decoration.
        pose.translate(-0.45, size.height() - 0.675, -0.3048);
        pose.scale(1 / PIXELS_PER_BLOCK, -1 / PIXELS_PER_BLOCK, 1 / PIXELS_PER_BLOCK);
        fill(pose, buffers, 0, 0, width, height, 0, BACKGROUND);
        fill(pose, buffers, 6, 27, width - 6, 28, 0.02F, RULE);
        boolean widgets = summary != null && summary.mode() == DisplaySummary.Mode.LIVE && !summary.widgets().isEmpty();
        text(pose, buffers, tr(widgets ? "title_home" : "title"), 6, 5, width - 12, DashboardTheme.ACCENT);

        if (summary == null || summary.mode() != DisplaySummary.Mode.LIVE) {
            inactive(pose, buffers, summary, width, height);
        } else if (widgets) {
            text(pose, buffers, summary.networkName(), 6, 16, width - 12, DashboardTheme.TEXT);
            widgets(pose, buffers, summary, width, height);
        } else {
            text(pose, buffers, summary.networkName(), 6, 16, width - 12, DashboardTheme.TEXT);
            if (summary.devices().isEmpty()) {
                wrapped(pose, buffers, tr("empty"), 6, 34, width - 12, 2, DashboardTheme.TEXT);
                wrapped(pose, buffers, tr("choose_favorites"), 6, 57, width - 12, 2, DashboardTheme.MUTED);
            } else {
                int count = Math.min(summary.devices().size(), size == DashboardDisplayBlock.Size.SINGLE ? 1
                        : size == DashboardDisplayBlock.Size.WIDE ? 2 : 4);
                for (int index = 0; index < count; index++) {
                    float x = size == DashboardDisplayBlock.Size.WIDE ? 6 + index * (width / 2) : 6;
                    float y = size == DashboardDisplayBlock.Size.LARGE ? 36 + index * 38 : 34;
                    float rowWidth = size == DashboardDisplayBlock.Size.WIDE ? width / 2 - 12 : width - 12;
                    favorite(pose, buffers, summary.devices().get(index), x, y, rowWidth,
                            size == DashboardDisplayBlock.Size.LARGE);
                }
                if (size == DashboardDisplayBlock.Size.WIDE)
                    fill(pose, buffers, width / 2 - 1, 34, width / 2, 66, 0.02F, RULE);
                int remaining = summary.total() - count;
                String footer = remaining > 0 ? Component.translatable("display.homelink_dashboard.more", remaining).getString() : tr("open");
                text(pose, buffers, footer, 6, height - 11, width - 12, DashboardTheme.MUTED);
            }
        }
        pose.popPose();
    }

    /**
     * The viewer's Home widgets at their saved grid places: the twelve columns span the screen width and each
     * grid row is {@link #WIDGET_ROW} pixels high. Tiles below the screen are counted in the footer.
     */
    private void widgets(PoseStack pose, MultiBufferSource buffers, DisplaySummary summary, float width, float height) {
        float left = 6, top = 32, span = width - 12, bottom = height - 13;
        int hidden = 0;
        for (var tile : summary.widgets()) {
            float x = left + tile.x() * span / DashboardWidget.COLUMNS;
            float y = top + tile.y() * WIDGET_ROW;
            float w = tile.width() * span / DashboardWidget.COLUMNS - 3;
            float h = tile.height() * WIDGET_ROW - 3;
            if (y + h > bottom) { hidden++; continue; }
            fill(pose, buffers, x, y, x + w, y + h, 0.01F, TILE);
            var device = tile.device();
            boolean available = !device.name().isEmpty();
            fill(pose, buffers, x + 3, y + 5, x + 6, y + 8, 0.02F, DashboardTheme.status(device.status()));
            text(pose, buffers, available ? device.name() : tr("unavailable"), x + 9, y + 3, w - 12, DashboardTheme.TEXT);
            if (!available) continue;
            if (tile.metric()) {
                if (device.metrics().isEmpty()) { text(pose, buffers, tr("metric_unavailable"), x + 3, y + 14, w - 6, DashboardTheme.MUTED); continue; }
                var view = view(device.metrics().getFirst());
                text(pose, buffers, device.metrics().getFirst().name(), x + 3, y + 14, w - 6, DashboardTheme.MUTED);
                text(pose, buffers, MetricRendererRegistry.localizedValue(view), x + 3, y + 24, w - 6, DashboardTheme.TEXT);
                double fraction = MetricRendererRegistry.fraction(view);
                if (fraction >= 0 && h >= 34) {
                    fill(pose, buffers, x + 3, y + h - 4, x + w - 3, y + h - 2, 0.02F, RULE);
                    fill(pose, buffers, x + 3, y + h - 4, x + 3 + (float) ((w - 6) * Math.min(1, fraction)), y + h - 2, 0.03F, DashboardTheme.ACCENT);
                }
            } else if (device.metrics().isEmpty()) {
                text(pose, buffers, DashboardText.value(device.status()), x + 3, y + 14, w - 6, DashboardTheme.status(device.status()));
            } else {
                for (int index = 0; index < Math.min(2, device.metrics().size()); index++) {
                    var metric = device.metrics().get(index);
                    String value = MetricRendererRegistry.localizedValue(view(metric));
                    int valueWidth = Math.min(font.width(value), (int) w - 6);
                    float metricY = y + 14 + index * 10;
                    float labelWidth = w - 6 - valueWidth - 4;
                    if (labelWidth >= 18) text(pose, buffers, metric.name(), x + 3, metricY, labelWidth, DashboardTheme.MUTED);
                    text(pose, buffers, value, x + w - 3 - valueWidth, metricY, valueWidth, DashboardTheme.TEXT);
                }
            }
        }
        String footer = hidden > 0 ? Component.translatable("display.homelink_dashboard.more_widgets", hidden).getString() : tr("open");
        text(pose, buffers, footer, 6, height - 11, width - 12, DashboardTheme.MUTED);
    }

    private static DebugDeviceView.Metric view(DisplaySummary.MetricLine metric) {
        return new DebugDeviceView.Metric("", metric.name(), DashboardClientState.format(metric.value(), metric.unit()),
                0, metric.type(), metric.unit(), metric.value());
    }

    private void favorite(PoseStack pose, MultiBufferSource buffers, DisplaySummary.DeviceLine device,
            float x, float y, float width, boolean large) {
        fill(pose, buffers, x, y + 2, x + 3, y + 5, 0.02F, DashboardTheme.status(device.status()));
        if (large) {
            String status = DashboardText.value(device.status());
            int statusWidth = Math.min(font.width(status), (int) width / 3);
            text(pose, buffers, status, x + width - statusWidth, y, statusWidth, DashboardTheme.status(device.status()));
            text(pose, buffers, device.name(), x + 7, y, width - statusWidth - 13, DashboardTheme.TEXT);
        } else {
            text(pose, buffers, device.name(), x + 7, y, width - 7, DashboardTheme.TEXT);
        }
        if (device.metrics().isEmpty()) {
            text(pose, buffers, DashboardText.value(device.status()), x, y + 13, width, DashboardTheme.MUTED);
        }
        for (int index = 0; index < Math.min(2, device.metrics().size()); index++) {
            var metric = device.metrics().get(index);
            var view = new DebugDeviceView.Metric("", metric.name(), DashboardClientState.format(metric.value(), metric.unit()),
                    0, metric.type(), metric.unit(), metric.value());
            String value = MetricRendererRegistry.localizedValue(view);
            float metricY = y + 12 + index * 11;
            if (large) {
                int valueWidth = Math.min(font.width(value), (int) width * 2 / 3);
                text(pose, buffers, metric.name(), x, metricY, width - valueWidth - 8, DashboardTheme.MUTED);
                text(pose, buffers, value, x + width - valueWidth, metricY, valueWidth, DashboardTheme.TEXT);
            } else {
                // Preserve the value first; a verbose metric label must not hide the useful reading.
                int valueWidth = Math.min(font.width(value), (int) width);
                float labelWidth = width - valueWidth - 5;
                if (labelWidth >= 18) text(pose, buffers, metric.name(), x, metricY, labelWidth, DashboardTheme.MUTED);
                text(pose, buffers, value, x + width - valueWidth, metricY, valueWidth, DashboardTheme.TEXT);
            }
        }
        if (large) fill(pose, buffers, x, y + 34, x + width, y + 35, 0.02F, RULE);
    }

    private void inactive(PoseStack pose, MultiBufferSource buffers, DisplaySummary summary, float width, float height) {
        String key = summary == null ? "loading" : switch (summary.mode()) {
            case UNBOUND -> "unbound";
            case OFFLINE -> "offline";
            case RESTRICTED -> "restricted";
            default -> "loading";
        };
        wrapped(pose, buffers, tr(key), 6, 36, width - 12, 2, DashboardTheme.MUTED);
        if (summary != null && summary.mode() == DisplaySummary.Mode.UNBOUND)
            text(pose, buffers, tr("connect"), 6, height - 11, width - 12, DashboardTheme.ACCENT);
    }

    private void wrapped(PoseStack pose, MultiBufferSource buffers, String value, float x, float y,
            float width, int lines, int color) {
        var wrapped = font.split(Component.literal(clean(value)), Math.max(1, (int) width));
        for (int i = 0; i < Math.min(lines, wrapped.size()); i++) {
            pose.pushPose();
            pose.translate(0, 0, 0.08);
            font.drawInBatch(wrapped.get(i), x, y + i * 10, color, false, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }

    private void text(PoseStack pose, MultiBufferSource buffers, String value, float x, float y, float width, int color) {
        String safe = clean(value);
        int limit = Math.max(0, (int) width);
        if (font.width(safe) > limit) {
            String suffix = "...";
            safe = limit < font.width(suffix) ? font.plainSubstrByWidth(safe, limit)
                    : font.plainSubstrByWidth(safe, limit - font.width(suffix)) + suffix;
        }
        pose.pushPose();
        pose.translate(0, 0, 0.08);
        font.drawInBatch(safe, x, y, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void fill(PoseStack pose, MultiBufferSource buffers, float left, float top,
            float right, float bottom, float z, int color) {
        var vertices = buffers.getBuffer(RenderType.textBackground());
        var matrix = pose.last().pose();
        vertices.addVertex(matrix, left, top, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, left, bottom, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, bottom, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        vertices.addVertex(matrix, right, top, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
    }

    private static String clean(String text) {
        return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace('\u00a7', '?');
    }

    private static String tr(String key) {
        return Component.translatable("display.homelink_dashboard." + key).getString();
    }
}
