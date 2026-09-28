package fr.lkdm.homelink.dashboard.client.rendering;

import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Position;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Generic metric presentation chosen by public schema type, independent of the device's mod. */
public final class MetricRendererRegistry {
    public static final int HEIGHT = 32;
    private static final Map<ResourceLocation, MetricRenderer> RENDERERS = new HashMap<>();
    private static final int CACHE_LIMIT = 1024;
    private static final Map<DebugDeviceView.Metric, Presentation> PRESENTATIONS = new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<DebugDeviceView.Metric, Presentation> eldest) { return size() > CACHE_LIMIT; }
    };

    /** Pure presentation extension; all GUI drawing stays inside the dashboard. */
    @FunctionalInterface
    public interface MetricRenderer {
        Presentation describe(DebugDeviceView.Metric metric);
    }

    /** A negative fraction suppresses the progress bar. */
    public record Presentation(String value, double fraction) {
        public Presentation {
            value = clean(value);
            fraction = !Double.isFinite(fraction) || fraction < 0 ? -1 : clampFraction(fraction);
        }
    }

    static {
        register(id("boolean"), metric -> value(metric) instanceof Boolean enabled
                ? text(enabled ? "ON" : "OFF") : fallback(metric));
        register(id("percentage"), metric -> value(metric) instanceof Percentage percent
                ? new Presentation(decimal(percent.value()) + "%", percent.value() / 100) : fallback(metric));
        register(id("integer"), MetricRendererRegistry::number);
        register(id("long"), MetricRendererRegistry::number);
        register(id("double"), MetricRendererRegistry::number);
        register(id("energy"), metric -> value(metric) instanceof Energy energy
                ? new Presentation(compact(energy.stored()) + " / " + compact(energy.capacity()) + " FE",
                        energy.capacity() == 0 ? 0 : (double) energy.stored() / energy.capacity()) : fallback(metric));
        register(id("position"), metric -> value(metric) instanceof Position position
                ? text(position.x() + " / " + position.y() + " / " + position.z()) : fallback(metric));
    }

    private MetricRendererRegistry() { }

    /** Client setup extension point; unknown schema types always retain readable fallback values. */
    public static void register(ResourceLocation type, MetricRenderer renderer) {
        RENDERERS.put(Objects.requireNonNull(type), Objects.requireNonNull(renderer));
        PRESENTATIONS.clear();
    }

    public static Presentation presentation(DebugDeviceView.Metric metric) {
        if (metric == null) return text("?");
        Presentation cached = PRESENTATIONS.get(metric);
        if (cached != null) return cached;
        Presentation result = describeUncached(metric);
        PRESENTATIONS.put(metric, result);
        return result;
    }

    private static Presentation describeUncached(DebugDeviceView.Metric metric) {
        try {
            ResourceLocation type = metric.type() == null ? null : ResourceLocation.tryParse(metric.type());
            MetricRenderer renderer = type == null ? null : RENDERERS.get(type);
            Presentation result = renderer == null ? fallback(metric) : renderer.describe(metric);
            return result == null ? fallback(metric) : result;
        } catch (RuntimeException exception) {
            return fallback(metric);
        }
    }

    public static void clearCache() { PRESENTATIONS.clear(); }
    public static int cachedPresentationCount() { return PRESENTATIONS.size(); }

    public static String describe(DebugDeviceView.Metric metric) { return presentation(metric).value(); }
    public static String localizedValue(DebugDeviceView.Metric metric) {
        return metric != null && value(metric) instanceof Boolean enabled
                ? DashboardText.value(enabled ? "ON" : "OFF") : describe(metric);
    }
    public static double fraction(DebugDeviceView.Metric metric) { return presentation(metric).fraction(); }

    /** One clipped, fixed-height row; callers virtualize rows outside the visible viewport. */
    public static void render(GuiGraphics graphics, Font font, DebugDeviceView.Metric metric, int x, int y, int width) {
        if (width <= 0) return;
        Presentation view = presentation(metric);
        graphics.drawString(font, fit(font, metric == null ? "?" : clean(metric.name()), width), x, y, 0xAFB1AD, false);
        graphics.drawString(font, fit(font, localizedValue(metric), width), x, y + 11, 0xE7E5E0, false);
        if (view.fraction() >= 0) {
            graphics.fill(x, y + 24, x + width, y + 27, DashboardTheme.LINE);
            graphics.fill(x, y + 24, x + (int) Math.round(width * view.fraction()), y + 27, DashboardTheme.ACCENT);
        }
    }

    public static double clampFraction(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    private static Presentation number(DebugDeviceView.Metric metric) {
        Object value = value(metric);
        if (!(value instanceof Number number)) return fallback(metric);
        String rendered = number instanceof Double || number instanceof Float ? decimal(number.doubleValue()) : number.toString();
        String unit = metric.unit() == null ? "" : clean(metric.unit());
        return text(rendered + (unit.isBlank() ? "" : " " + unit));
    }

    private static Object value(DebugDeviceView.Metric metric) {
        return metric.value() == null ? null : metric.value().value();
    }

    private static Presentation fallback(DebugDeviceView.Metric metric) {
        return text(metric == null ? "?" : metric.displayValue());
    }

    private static Presentation text(String value) { return new Presentation(value, -1); }

    /** At most two decimals, without trailing zeros, for every value the Dashboard shows. */
    public static String decimal(double value) {
        if (!Double.isFinite(value)) return "?";
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        long divisor;
        String suffix;
        if (value >= 1_000_000_000_000L) { divisor = 1_000_000_000_000L; suffix = "T"; }
        else if (value >= 1_000_000_000L) { divisor = 1_000_000_000L; suffix = "B"; }
        else if (value >= 1_000_000L) { divisor = 1_000_000L; suffix = "M"; }
        else { divisor = 1_000; suffix = "k"; }
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(divisor), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + suffix;
    }

    private static String clean(String text) {
        if (text == null) return "?";
        String safe = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replace('\u00a7', '?');
        return safe.length() <= 256 ? safe : safe.substring(0, 253) + "...";
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String suffix = "...";
        if (font.width(suffix) >= width) return font.plainSubstrByWidth(text, width);
        return font.plainSubstrByWidth(text, width - font.width(suffix)) + suffix;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("homecore", path); }
}
