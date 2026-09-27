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
        bus.addListener(fr.lkdm.homelink.dashboard.network.NetworkNamePayloads::register);
        bus.addListener(fr.lkdm.homelink.dashboard.network.DisplaySummaryPayloads::register);
        bus.addListener(fr.lkdm.homelink.dashboard.network.DiscoveryPayloads::register);
        container.registerConfig(ModConfig.Type.CLIENT, DashboardConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, fr.lkdm.homelink.dashboard.config.DashboardEnergyConfig.SPEC);
        // HomeLink Energy enters every Dashboard block on all faces through the shared HomeCore capability.
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            var port = fr.lkdm.homecore.api.energy.EnergyApi.BLOCK;
            event.registerBlockEntity(port, DashboardRegistries.SERVER_ENTITY.get(), (entity, side) -> entity.energyPort());
            event.registerBlockEntity(port, DashboardRegistries.REPEATER_ENTITY.get(), (entity, side) -> entity.energyPort());
            // Every cell of a multi-block display feeds the master's buffer, so a cable may touch any part.
            event.registerBlock(port, (level, pos, state, entity, side) -> {
                var master = fr.lkdm.homelink.dashboard.block.DashboardDisplayBlock.masterPos(state, pos);
                return level.getBlockEntity(master) instanceof fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity display
                        ? display.energyPort() : null;
            }, DashboardRegistries.DASHBOARD_DISPLAY.get());
        });
    }
}
