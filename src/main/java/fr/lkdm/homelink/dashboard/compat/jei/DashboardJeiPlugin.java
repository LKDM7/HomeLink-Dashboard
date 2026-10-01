package fr.lkdm.homelink.dashboard.compat.jei;

import fr.lkdm.homelink.dashboard.compat.ViewerInfo;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional JEI integration, loaded by JEI only: information pages for every HomeLink Dashboard item. */
@JeiPlugin
public final class DashboardJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("homelink_dashboard", "jei");

    @Override public ResourceLocation getPluginUid() { return ID; }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        ViewerInfo.pages().forEach((item, lines) -> registration.addItemStackInfo(new ItemStack(item), lines.toArray(Component[]::new)));
    }
}
