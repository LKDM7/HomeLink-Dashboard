package fr.lkdm.homelink.dashboard.client;

import fr.lkdm.homelink.dashboard.HomeLinkDashboard;
import fr.lkdm.homelink.dashboard.client.rendering.DashboardDisplayRenderer;
import fr.lkdm.homelink.dashboard.client.screen.DashboardScreen;
import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HomeLinkDashboard.MOD_ID, value = Dist.CLIENT)
public final class DashboardClient {
    private DashboardClient() { }
    @SubscribeEvent public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(DashboardRegistries.DASHBOARD_MENU.get(), DashboardScreen::new);
    }
    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DashboardRegistries.DISPLAY_ENTITY.get(), DashboardDisplayRenderer::new);
    }
}
