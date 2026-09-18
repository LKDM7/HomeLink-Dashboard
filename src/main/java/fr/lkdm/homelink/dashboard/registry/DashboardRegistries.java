package fr.lkdm.homelink.dashboard.registry;

import fr.lkdm.homelink.dashboard.HomeLinkDashboard;
import fr.lkdm.homelink.dashboard.block.HomeServerBlock;
import fr.lkdm.homelink.dashboard.block.DashboardDisplayBlock;
import fr.lkdm.homelink.dashboard.blockentity.HomeServerBlockEntity;
import fr.lkdm.homelink.dashboard.blockentity.DashboardDisplayBlockEntity;
import fr.lkdm.homelink.dashboard.menu.DashboardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DashboardRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomeLinkDashboard.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomeLinkDashboard.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomeLinkDashboard.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HomeLinkDashboard.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeLinkDashboard.MOD_ID);

    public static final DeferredBlock<HomeServerBlock> HOME_SERVER = BLOCKS.register("home_server",
            () -> new HomeServerBlock(properties()));
    public static final DeferredBlock<DashboardDisplayBlock> DASHBOARD_DISPLAY = BLOCKS.register("dashboard_display",
            () -> new DashboardDisplayBlock(properties().noOcclusion()));
    public static final DeferredBlock<fr.lkdm.homelink.dashboard.block.SignalRepeaterBlock> SIGNAL_REPEATER = BLOCKS.register("signal_repeater",
            () -> new fr.lkdm.homelink.dashboard.block.SignalRepeaterBlock(properties().noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<fr.lkdm.homelink.dashboard.blockentity.SignalRepeaterBlockEntity>> REPEATER_ENTITY = BLOCK_ENTITIES.register("signal_repeater",
            () -> BlockEntityType.Builder.of(fr.lkdm.homelink.dashboard.blockentity.SignalRepeaterBlockEntity::new, SIGNAL_REPEATER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HomeServerBlockEntity>> SERVER_ENTITY = BLOCK_ENTITIES.register("home_server",
            () -> BlockEntityType.Builder.of(HomeServerBlockEntity::new, HOME_SERVER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DashboardDisplayBlockEntity>> DISPLAY_ENTITY = BLOCK_ENTITIES.register("dashboard_display",
            () -> BlockEntityType.Builder.of(DashboardDisplayBlockEntity::new, DASHBOARD_DISPLAY.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<DashboardMenu>> DASHBOARD_MENU = MENUS.register("dashboard",
            () -> IMenuTypeExtension.create(DashboardMenu::new));

    private DashboardRegistries() { }
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F).requiresCorrectToolForDrops();
    }

    public static void register(IEventBus bus) {
        ITEMS.registerSimpleBlockItem(HOME_SERVER);
        ITEMS.registerSimpleBlockItem(DASHBOARD_DISPLAY);
        ITEMS.registerSimpleBlockItem(SIGNAL_REPEATER);
        TABS.register("dashboard", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.homelink_dashboard"))
                .icon(() -> HOME_SERVER.get().asItem().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(HOME_SERVER.get());
                    output.accept(DASHBOARD_DISPLAY.get());
                    output.accept(SIGNAL_REPEATER.get());
                }).build());
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }
}
