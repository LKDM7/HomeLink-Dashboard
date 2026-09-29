package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homecore.api.action.ActionType;
import fr.lkdm.homecore.api.action.StandardActions;
import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;

/** Immutable, bounded action metadata decoded from HomeCore's public snapshot contract. */
public record DeviceActionView(String id, String name, String description, String type, String permission,
                               Double min, Double max, Double step, int maxLength, List<WireValue> options) {
    public static final String POWER = StandardActions.POWER.toString();
    public static final String RENAME = StandardActions.RENAME.toString();

    public DeviceActionView { options = List.copyOf(options); }

    public boolean supported() {
        try { ActionType.valueOf(type); } catch (IllegalArgumentException exception) { return false; }
        if (min != null && !Double.isFinite(min) || max != null && !Double.isFinite(max)
                || step != null && (!Double.isFinite(step) || step <= 0)
                || min != null && max != null && min > max || maxLength < 0 || maxLength > 4096) return false;
        // Slider arithmetic uses BigDecimal; finite endpoints may have a span larger than Double.MAX_VALUE.
        if (type.equals("SLIDER") && (min == null || max == null)) return false;
        return !type.equals("SELECT") || !options.isEmpty();
    }

    /** HomeCore's power switch and rename field, shown apart from the machine's own actions. */
    public boolean standard() { return id.equals(POWER) || id.equals(RENAME); }

    /** Mirrors HomeCore: switching on and renaming stay possible while a machine is off or in a warning state. */
    public boolean availableWhen(String deviceStatus) {
        return standard() ? !deviceStatus.equals("OFFLINE") : deviceStatus.equals("ONLINE");
    }
}
