package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.ActionType;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homelink.dashboard.client.rendering.MetricRendererRegistry;
import fr.lkdm.homelink.dashboard.client.state.DebugDeviceView;
import fr.lkdm.homelink.dashboard.client.state.DeviceActionView;
import fr.lkdm.homelink.dashboard.client.widget.ActionControlRegistry;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises numeric UI policy against HomeCore's real public validation, without rendering. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ActionControlGameTests {
    @GameTest(template = "empty")
    public static void decimalSlidersMatchHomeCoreValidation(GameTestHelper helper) {
        double[][] ranges = {{0, 1, 0.1}, {-0.3, 0.8, 0.2}, {0.1, 0.95, 0.1}, {16, 128, 16}, {1.0E-8, 1.0E-6, 1.0E-8}};
        for (double[] range : ranges) {
            var schema = schema(range[0], range[1], range[2]);
            var authoritative = action(range[0], range[1], range[2]);
            for (int index = 0; index <= 1000; index++) {
                double parameter = ActionControlRegistry.sliderValue(schema, index / 1000.0);
                helper.assertTrue(authoritative.validate(parameter).isSuccess(), "Every decimal slider output must pass actual HomeCore validation: " + parameter);
            }
        }
        helper.assertTrue(ActionControlRegistry.sliderValue(schema(0.1, 0.95, 0.1), 1) == 0.9,
                "A maximum between steps must select the last legal step instead of the invalid endpoint");
        var decimal = schema(0.0, 1.0, 0.1);
        boolean rejected = false;
        try { ActionControlRegistry.validateNumber(decimal, 0.30000000000000004); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected && !action(0, 1, 0.1).validate(0.30000000000000004).isSuccess(),
                "Client number validation must reject the same decimal step drift as HomeCore");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extremeSlidersStayFiniteAndBounded(GameTestHelper helper) {
        var wide = schema(-Double.MAX_VALUE, Double.MAX_VALUE, null);
        helper.assertTrue(ActionControlRegistry.sliderValue(wide, 0.5) == 0, "Finite extreme bounds must interpolate without overflow");
        helper.assertTrue(ActionControlRegistry.sliderValue(wide, 0) == -Double.MAX_VALUE
                && ActionControlRegistry.sliderValue(wide, 1) == Double.MAX_VALUE, "Extreme endpoints must remain exact");
        helper.assertTrue(ActionControlRegistry.sliderValue(wide, Double.NaN) == -Double.MAX_VALUE, "Invalid pointer fraction must remain finite");
        var precise = schema(-Double.MAX_VALUE, Double.MAX_VALUE, 0.1);
        for (double fraction : new double[]{0, 0.1, 0.3, 0.5, 0.9, 1}) {
            double value = ActionControlRegistry.sliderValue(precise, fraction);
            helper.assertTrue(Double.isFinite(value) && action(-Double.MAX_VALUE, Double.MAX_VALUE, 0.1).validate(value).isSuccess(),
                    "Huge ranges with decimal steps must not submit nonrepresentable/invalid values");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void metricPresentationCacheIsBoundedAndInvalidatable(GameTestHelper helper) {
        MetricRendererRegistry.clearCache();
        for (int index = 0; index < 1500; index++) MetricRendererRegistry.presentation(new DebugDeviceView.Metric(
                "test:counter", "Counter", Integer.toString(index), index, "test:unknown", ""));
        helper.assertTrue(MetricRendererRegistry.cachedPresentationCount() <= 1024, "Changing metrics must not retain unlimited presentation history");
        var metric = new DebugDeviceView.Metric("test:counter", "Counter", "Original", 1, "test:custom_renderer", "");
        helper.assertTrue(MetricRendererRegistry.describe(metric).equals("Original"), "Unknown fallback is cached");
        MetricRendererRegistry.register(ResourceLocation.parse("test:custom_renderer"), ignored -> new MetricRendererRegistry.Presentation("Updated", -1));
        helper.assertTrue(MetricRendererRegistry.describe(metric).equals("Updated"), "Renderer registration must invalidate old cached fallback");
        MetricRendererRegistry.clearCache();
        helper.assertTrue(MetricRendererRegistry.cachedPresentationCount() == 0, "Closing the UI can release presentation cache references");
        helper.succeed();
    }

    private static DeviceActionView schema(double min, double max, Double step) {
        return new DeviceActionView("test:slider", "Slider", "", "SLIDER", "homecore:control", min, max, step, 4096, List.of());
    }
    private static DeviceAction<Double> action(double min, double max, double step) {
        return DeviceAction.builder(ResourceLocation.parse("test:slider"), Component.literal("Slider"), ActionType.SLIDER, Double.class)
                .range(min, max).step(step).handler((context, value) -> ActionResult.success()).build();
    }
}
