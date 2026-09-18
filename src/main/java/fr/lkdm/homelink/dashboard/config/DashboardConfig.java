package fr.lkdm.homelink.dashboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences only. Authorization and packet budgets remain server-owned. */
public final class DashboardConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue ALERT_LIMIT;
    private static final ModConfigSpec.IntValue UI_INTERVAL;
    static {
        var builder = new ModConfigSpec.Builder();
        ALERT_LIMIT = builder.comment("Maximum events retained while a dashboard is open.")
                .translation("screen.homelink_dashboard.settings_alert_limit")
                .defineInRange("alertHistoryLimit", 128, 1, 512);
        UI_INTERVAL = builder.comment("Ticks between cached UI list updates. Does not change HomeCore delivery or permissions.")
                .translation("screen.homelink_dashboard.settings_ui_interval")
                .defineInRange("uiUpdateIntervalTicks", 2, 1, 20);
        SPEC = builder.build();
    }
    private DashboardConfig() { }
    public static int alertLimit() { return SPEC.isLoaded() ? ALERT_LIMIT.get() : 128; }
    public static int uiInterval() { return SPEC.isLoaded() ? UI_INTERVAL.get() : 2; }
    public static void setAlertLimit(int value) {
        if (!SPEC.isLoaded()) return;
        ALERT_LIMIT.set(Math.clamp(value, 1, 512));
        SPEC.save();
    }
    public static void setUiInterval(int value) {
        if (!SPEC.isLoaded()) return;
        UI_INTERVAL.set(Math.clamp(value, 1, 20));
        SPEC.save();
    }
}
