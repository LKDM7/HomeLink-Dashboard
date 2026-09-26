package fr.lkdm.homelink.dashboard.verification;

import fr.lkdm.homelink.dashboard.registry.DashboardRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Uses the server's loaded JSON recipes, advancements and loot tables. */
@GameTestHolder(DashboardValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {
    @GameTest(template = "empty")
    public static void homeServerSurvivalRecipe(GameTestHelper helper) {
        verify(helper, "home_server", DashboardRegistries.HOME_SERVER.get(), List.of(
                Items.IRON_INGOT, homeCore("homelink_microprocessor"), Items.IRON_INGOT,
                Items.REDSTONE, homeCore("homelink_circuit_board"), Items.REDSTONE,
                Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT));
    }

    @GameTest(template = "empty")
    public static void dashboardDisplaySurvivalRecipe(GameTestHelper helper) {
        verify(helper, "dashboard_display", DashboardRegistries.DASHBOARD_DISPLAY.get(), List.of(
                Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
                Items.GLASS, Items.GLASS, Items.GLASS,
                Items.IRON_INGOT, homeCore("homelink_circuit_board"), Items.IRON_INGOT));
    }

    @GameTest(template = "empty")
    public static void signalRepeaterSurvivalRecipe(GameTestHelper helper) {
        verify(helper, "signal_repeater", DashboardRegistries.SIGNAL_REPEATER.get(), List.of(
                Items.IRON_INGOT, Items.AIR, Items.IRON_INGOT,
                Items.AIR, Items.REPEATER, Items.AIR,
                Items.IRON_INGOT, homeCore("homelink_circuit_board"), Items.IRON_INGOT));
    }

    @GameTest(template = "empty")
    public static void formerRecipesNeedHomeCoreComponents(GameTestHelper helper) {
        var level = helper.getLevel();
        var formerRepeater = CraftingInput.of(3, 3, List.of(
                Items.IRON_INGOT, Items.AIR, Items.IRON_INGOT,
                Items.AIR, Items.REPEATER, Items.AIR,
                Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT).stream().map(ItemStack::new).toList());
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, formerRepeater, level).isEmpty(),
                "Redstone must no longer replace the HomeLink Circuit Board");
        helper.succeed();
    }

    private static Item homeCore(String path) {
        var id = ResourceLocation.fromNamespaceAndPath("homecore", path);
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            throw new IllegalStateException("HomeCore component missing: " + id);
        }
        return BuiltInRegistries.ITEM.get(id);
    }

    private static void verify(GameTestHelper helper, String path, Block block, List<Item> ingredients) {
        var level = helper.getLevel();
        var recipeId = ResourceLocation.fromNamespaceAndPath("homelink_dashboard", path);
        var stacks = ingredients.stream().map(ItemStack::new).toList();
        var input = CraftingInput.of(3, 3, stacks);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        helper.assertTrue(recipe.id().equals(recipeId), "Expected the packaged HomeLink shaped recipe");
        var crafted = recipe.value().assemble(input, level.registryAccess());
        helper.assertTrue(crafted.is(block.asItem()) && crafted.getCount() == 1, "Shaped recipe must produce exactly one correct block");
        var invalid = new ArrayList<>(stacks);
        invalid.set(4, new ItemStack(Items.DIRT));
        helper.assertTrue(!recipe.value().matches(CraftingInput.of(3, 3, invalid), level), "Missing key component must not craft a control block");
        var advancement = ResourceLocation.fromNamespaceAndPath("homelink_dashboard", "recipes/redstone/" + path);
        helper.assertTrue(level.getServer().getAdvancements().get(advancement) != null, "Recipe unlock advancement must be loaded");
        var location = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(location, block.defaultBlockState(), 3);
        helper.assertTrue(block.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "Physical device must use the pickaxe mining tag");
        var drops = Block.getDrops(block.defaultBlockState(), level, location, level.getBlockEntity(location), null, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                "Packaged loot table must return exactly one device item");
        helper.succeed();
    }
}
