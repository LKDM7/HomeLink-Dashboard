package fr.lkdm.homelink.dashboard;

import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import fr.lkdm.homelink.dashboard.network.PreferencesPayloads;
import fr.lkdm.homelink.dashboard.config.DashboardConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(HomeLinkDashboard.MOD_ID)
public final class HomeLinkDashboard {
    public static final String MOD_ID = "homelink_dashboard";

    public HomeLinkDashboard(IEventBus bus, ModContainer container) {
        DashboardRegistries.register(bus);
        bus.addListener(PreferencesPayloads::register);
        container.registerConfig(ModConfig.Type.CLIENT, DashboardConfig.SPEC);
    }
}
