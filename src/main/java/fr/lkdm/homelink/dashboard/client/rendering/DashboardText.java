package fr.lkdm.homelink.dashboard.client.rendering;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Localizes presentation only; protocol identifiers and permission checks stay unchanged. */
public final class DashboardText {
    private DashboardText() { }

    public static Component component(String value) {
        if (value == null || value.isEmpty()) return Component.empty();
        return Component.translatableWithFallback("value.homelink_dashboard." + value.toLowerCase(Locale.ROOT), value);
    }
    public static String value(String value) { return component(value).getString(); }

    public static String message(String message) {
        if (message == null || message.isEmpty()) return "";
        int split = message.indexOf(": ");
        return split < 0 ? value(message) : value(message.substring(0, split)) + " : " + value(message.substring(split + 2));
    }
}
