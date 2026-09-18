package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Position;
import fr.lkdm.homecore.api.transport.WireValue;
import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Tests pure presentation data; never constructs client rendering objects on a dedicated server. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MetricRendererGameTests {
    @GameTest(template = "empty")
    public static void genericMetricFormats(GameTestHelper helper) {
        helper.assertTrue(MetricRendererRegistry.describe(metric("boolean", true, "")).equals("ON"), "Boolean must display ON");
        helper.assertTrue(MetricRendererRegistry.describe(metric("boolean", false, "")).equals("OFF"), "Boolean must display OFF");
        helper.assertTrue(MetricRendererRegistry.describe(metric("integer", 42, "items")).equals("42 items"), "Integer must respect schema unit");
        helper.assertTrue(MetricRendererRegistry.describe(metric("long", Long.MAX_VALUE, "")).equals(Long.toString(Long.MAX_VALUE)),
                "Integer telemetry must preserve all 64 bits");
        helper.assertTrue(MetricRendererRegistry.describe(metric("double", 22.5, "°C")).equals("22.5 °C"),
                "Temperature must render through the public numeric value and unit without an invented type");
        helper.assertTrue(MetricRendererRegistry.describe(metric("position", new Position(124, 64, -382), "")).equals("124 / 64 / -382"),
                "Position must retain negative coordinates");
        helper.assertTrue(MetricRendererRegistry.describe(metric("energy", new Energy(4_200_000, 10_000_000), "FE")).equals("4.2M / 10M FE"),
                "Energy must use readable compact amounts and its public FE contract");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressBoundsAndZeroCapacity(GameTestHelper helper) {
        var percentage = metric("percentage", new Percentage(73), "%");
        helper.assertTrue(MetricRendererRegistry.describe(percentage).equals("73%"), "Percentage must not duplicate unit");
        helper.assertTrue(Math.abs(MetricRendererRegistry.fraction(percentage) - 0.73) < 0.000001, "Percentage must map to fraction");
        helper.assertTrue(MetricRendererRegistry.fraction(metric("energy", new Energy(0, 0), "")) == 0,
                "Empty capacity must avoid division by zero");
        helper.assertTrue(MetricRendererRegistry.clampFraction(-5) == 0 && MetricRendererRegistry.clampFraction(50) == 1,
                "Progress must never exceed the row bounds");
        helper.assertTrue(MetricRendererRegistry.clampFraction(Double.NaN) == 0
                        && MetricRendererRegistry.clampFraction(Double.POSITIVE_INFINITY) == 0,
                "Invalid numeric metadata must not enter rendering geometry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownAndBrokenMetricFallback(GameTestHelper helper) {
        var unknown = new DebugDeviceView.Metric("third_party:data", "Unknown", "Readable fallback", 0, "third_party:new_type", "", null);
        helper.assertTrue(MetricRendererRegistry.describe(unknown).equals("Readable fallback") && MetricRendererRegistry.fraction(unknown) < 0,
                "Unknown metric types must preserve fallback text without inventing a bar");
        var missing = new DebugDeviceView.Metric("third_party:data", "Missing", "?", 0, "homecore:percentage", "", null);
        helper.assertTrue(MetricRendererRegistry.describe(missing).equals("?"), "Missing known-type value must remain safe");
        var mismatch = new DebugDeviceView.Metric("third_party:data", "Mismatch", "Mismatched value", 0, "homecore:percentage", "", WireValue.from("not a number"));
        helper.assertTrue(MetricRendererRegistry.describe(mismatch).equals("Mismatched value"), "Incompatible values must use the fallback");
        var longText = new DebugDeviceView.Metric("third_party:data", "Long", "x".repeat(1000) + "\n", 0, "malformed type", "", null);
        helper.assertTrue(MetricRendererRegistry.describe(longText).length() <= 256, "Unknown fallback text must remain bounded");
        helper.assertTrue(MetricRendererRegistry.describe(null).equals("?"), "Missing metric must remain safe");
        ResourceLocation broken = ResourceLocation.fromNamespaceAndPath("homelink_dashboard_validation", "broken_renderer");
        MetricRendererRegistry.register(broken, metric -> { throw new IllegalArgumentException("Deliberately broken extension"); });
        var extension = new DebugDeviceView.Metric("third_party:data", "Extension", "Still readable", 0, broken.toString(), "", null);
        helper.assertTrue(MetricRendererRegistry.describe(extension).equals("Still readable"), "A failing renderer extension must not crash the dashboard");
        helper.succeed();
    }

    private static DebugDeviceView.Metric metric(String type, Object value, String unit) {
        return new DebugDeviceView.Metric("verification:value", "Metric", "fallback", 0, "homecore:" + type, unit, WireValue.from(value));
    }
}
