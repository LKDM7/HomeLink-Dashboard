package fr.lkdm.homelink.dashboard.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server balance: HomeLink Energy (HE) each Dashboard block uses per minute (1200 ticks) while it is
 * active. Without it the block stops: no radio signal, no dashboard. For scale: a Solar Panel I
 * averages 100 HE per minute over a day. 0 lets a block run for free.
 */
public final class DashboardEnergyConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue SERVER_ENERGY;
    public static final ModConfigSpec.IntValue REPEATER_ENERGY;
    public static final ModConfigSpec.IntValue DISPLAY_ENERGY;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("HomeLink Energy (HE) per minute while active; without it the block stops. 0 = free.").push("energy");
        SERVER_ENERGY = builder.comment("Home Server: hosts the network and emits its radio signal.")
                .translation("config.homelink_dashboard.serverEnergyPerMinute")
                .defineInRange("serverEnergyPerMinute", 60, 0, 1_000_000);
        REPEATER_ENERGY = builder.comment("Signal Repeater: relays the radio signal.")
                .translation("config.homelink_dashboard.repeaterEnergyPerMinute")
                .defineInRange("repeaterEnergyPerMinute", 20, 0, 1_000_000);
        DISPLAY_ENERGY = builder.comment("Dashboard Display: shows the dashboard.")
                .translation("config.homelink_dashboard.displayEnergyPerMinute")
                .defineInRange("displayEnergyPerMinute", 10, 0, 1_000_000);
        builder.pop();
        SPEC = builder.build();
    }

    private DashboardEnergyConfig() { }

    /** @return the configured cost, or 0 before the server config is loaded */
    public static long get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : 0;
    }
}
