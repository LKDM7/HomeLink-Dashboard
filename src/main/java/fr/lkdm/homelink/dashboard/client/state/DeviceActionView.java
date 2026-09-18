package fr.lkdm.homelink.dashboard.client.state;

import fr.lkdm.homecore.api.action.ActionType;
import fr.lkdm.homecore.api.transport.WireValue;
import java.util.List;

/** Immutable, bounded action metadata decoded from HomeCore's public snapshot contract. */
public record DeviceActionView(String id, String name, String description, String type, String permission,
                               Double min, Double max, Double step, int maxLength, List<WireValue> options) {
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
}
