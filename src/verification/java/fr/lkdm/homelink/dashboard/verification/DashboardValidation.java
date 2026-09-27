package fr.lkdm.homelink.dashboard.verification;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@Mod(DashboardValidation.MOD_ID)
public final class DashboardValidation {
    public static final String MOD_ID = "homelink_dashboard_validation";

    public DashboardValidation() {
        // The checks build networks without any power source; EnergyGameTests covers the HE requirement.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> EnergyGameTests.free());
    }
}
