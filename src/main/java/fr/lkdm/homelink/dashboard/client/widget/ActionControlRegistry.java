package fr.lkdm.homelink.dashboard.client.widget;

import fr.lkdm.homecore.api.action.Unit;
import fr.lkdm.homelink.dashboard.client.state.DeviceActionView;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardText;

/** Generic controls for HomeCore's public action types. Values only leave through explicit submission. */
public final class ActionControlRegistry {
    public static final List<String> TYPES = List.of("BUTTON", "TOGGLE", "INTEGER", "DOUBLE", "SLIDER", "SELECT", "TEXT", "POSITION");
    private ActionControlRegistry() { }

    public static Editor create(String type, Font font, int x, int y, int width, Consumer<AbstractWidget> addWidget) {
        return new Editor(type, font, x, y, width, addWidget);
    }

    public static final class Editor {
        private final String type;
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final List<EditBox> inputs = new ArrayList<>();
        private DeviceActionView action;
        private Button choice;
        private ParameterSlider slider;
        private ActionDropdown dropdown;
        private boolean toggle;

        private Editor(String type, Font font, int x, int y, int width, Consumer<AbstractWidget> addWidget) {
            this.type = type;
            switch (type) {
                case "INTEGER", "DOUBLE", "TEXT" -> input(font, x, y, width, type);
                case "POSITION" -> {
                    int part = Math.max(16, (width - 8) / 3);
                    input(font, x, y, part, "X");
                    input(font, x + part + 4, y, part, "Y");
                    input(font, x + (part + 4) * 2, y, part, "Z");
                }
                case "TOGGLE" -> {
                    choice = DashboardButton.builder(Component.empty(), button -> {
                        toggle = !toggle;
                        choiceLabel();
                    }).bounds(x, y, width, 20).build();
                    widgets.add(choice);
                }
                case "SELECT" -> { dropdown = new ActionDropdown(font, x, y, width); widgets.add(dropdown); }
                case "SLIDER" -> { slider = new ParameterSlider(x, y, width); widgets.add(slider); }
                default -> { }
            }
            for (AbstractWidget widget : widgets) {
                widget.visible = false;
                widget.active = false;
                addWidget.accept(widget);
            }
        }

        private void input(Font font, int x, int y, int width, String name) {
            EditBox input = new EditBox(font, x, y, width, 20, DashboardText.component(name));
            input.setHint(DashboardText.component(name));
            inputs.add(input);
            widgets.add(input);
        }

        public void configure(DeviceActionView action) {
            this.action = action;
            toggle = false;
            for (EditBox input : inputs) {
                input.setMaxLength(type.equals("TEXT") ? Math.clamp(action.maxLength(), 0, 4096) : 32);
                input.setValue(type.equals("TEXT") ? "" : type.equals("POSITION") ? "0" : defaultNumber(action));
            }
            if (slider != null) slider.configure(action);
            if (dropdown != null) dropdown.configure(action.options());
            choiceLabel();
        }

        private String defaultNumber(DeviceActionView action) {
            double start = action.min() == null ? 0 : action.min();
            if (action.max() != null) start = Math.min(start, action.max());
            return type.equals("INTEGER") ? Integer.toString((int) Math.ceil(start)) : Double.toString(start);
        }

        private void choiceLabel() {
            if (choice == null) return;
            choice.setMessage(DashboardText.component(toggle ? "ON" : "OFF"));
        }

        public void show(boolean visible, boolean enabled) {
            if (dropdown != null && (!visible || !enabled)) dropdown.close();
            for (AbstractWidget widget : widgets) {
                widget.visible = visible;
                widget.active = visible && enabled;
                if (!visible) widget.setFocused(false);
            }
        }

        public boolean expanded() { return dropdown != null && dropdown.expanded(); }
        public void onExpandedChange(Runnable callback) { if (dropdown != null) dropdown.onExpandedChange(callback); }

        public Object value() {
            if (action == null) throw new IllegalArgumentException("ACTION_UNAVAILABLE");
            try {
                return switch (type) {
                    case "BUTTON" -> Unit.INSTANCE;
                    case "TOGGLE" -> toggle;
                    case "INTEGER" -> { int value = Integer.parseInt(inputs.getFirst().getValue().strip()); validateNumber(action, value); yield value; }
                    case "DOUBLE" -> { double value = Double.parseDouble(inputs.getFirst().getValue().strip()); validateNumber(action, value); yield value; }
                    case "SLIDER" -> { double value = slider.numericValue(); validateNumber(action, value); yield value; }
                    case "SELECT" -> {
                        if (action.options().isEmpty()) throw new IllegalArgumentException("NO_OPTIONS");
                        yield dropdown.value();
                    }
                    case "TEXT" -> inputs.getFirst().getValue();
                    case "POSITION" -> new BlockPos(Integer.parseInt(inputs.get(0).getValue().strip()),
                            Integer.parseInt(inputs.get(1).getValue().strip()), Integer.parseInt(inputs.get(2).getValue().strip()));
                    default -> throw new IllegalArgumentException("UNSUPPORTED_ACTION");
                };
            } catch (NumberFormatException exception) { throw new IllegalArgumentException("INVALID_NUMBER"); }
        }
    }

    public static void validateNumber(DeviceActionView action, double value) {
        if (!Double.isFinite(value) || action.min() != null && value < action.min()
                || action.max() != null && value > action.max()) throw new IllegalArgumentException("OUT_OF_RANGE");
        if (action.step() != null && action.step() > 0) {
            BigDecimal offset = BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(action.min() == null ? 0 : action.min()));
            if (offset.remainder(BigDecimal.valueOf(action.step())).signum() != 0) throw new IllegalArgumentException("INVALID_STEP");
        }
    }

    /** Quantizes in decimal space, matching HomeCore's exact BigDecimal step contract. */
    public static double sliderValue(DeviceActionView action, double fraction) {
        if (!action.supported() || action.min() == null || action.max() == null) throw new IllegalArgumentException("MISSING_BOUNDS");
        BigDecimal minimum = BigDecimal.valueOf(action.min());
        BigDecimal span = BigDecimal.valueOf(action.max()).subtract(minimum);
        BigDecimal offset = span.multiply(BigDecimal.valueOf(Double.isFinite(fraction) ? Math.clamp(fraction, 0, 1) : 0));
        if (action.step() != null) {
            BigDecimal step = BigDecimal.valueOf(action.step());
            BigDecimal steps = offset.divide(step, 0, RoundingMode.HALF_UP).min(span.divide(step, 0, RoundingMode.DOWN));
            offset = steps.multiply(step);
        }
        double candidate = minimum.add(offset).doubleValue();
        try { validateNumber(action, candidate); return candidate; }
        catch (IllegalArgumentException exception) {
            // Very large offsets with tiny steps can have no nearby representable double.
            // The declared minimum is always a legal, finite step origin.
            return action.min();
        }
    }

    private static final class ParameterSlider extends AbstractSliderButton {
        private DeviceActionView action;
        private ParameterSlider(int x, int y, int width) { super(x, y, width, 20, Component.empty(), 0); }
        private void configure(DeviceActionView action) { this.action = action; value = 0; updateMessage(); }
        private double numericValue() {
            return sliderValue(action, value);
        }
        @Override protected void updateMessage() {
            setMessage(Component.literal(action == null ? "" : Double.toString(numericValue())));
        }
        @Override protected void applyValue() { }
    }
}
