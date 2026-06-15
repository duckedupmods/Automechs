package com.duckedupmods.automechs.datagen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.recipe.AssemblyRecipe;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Crafting + Fabricator recipes. Vanilla crafting builds the machines and the role circuits/upgrade
 * modules; the Fabricator's data-driven {@link AssemblyRecipe}s turn raw materials + power into the
 * Head/Chest parts that the Robot Builder consumes.
 */
public class AutomechsRecipeProvider extends RecipeProvider {

    public AutomechsRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        // ----- Machines (vanilla crafting) -----
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.ASSEMBLY_WORKSHOP.get())
                .pattern("III")
                .pattern("ICI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('C', Blocks.CRAFTING_TABLE)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CHARGING_PAD.get())
                .pattern("CRC")
                .pattern("RIR")
                .pattern("III")
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        // Data Rack — a server cabinet: iron frame, glass viewport, a redstone data bus around a chest core.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DATA_RACK.get())
                .pattern("IGI")
                .pattern("RCR")
                .pattern("IGI")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE)
                .define('C', Blocks.CHEST)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        // Main Drive — the network controller: a diamond core in a redstone/gold logic frame.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.MAIN_DRIVE.get())
                .pattern("IGI")
                .pattern("RDR")
                .pattern("ICI")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('R', Items.REDSTONE)
                .define('D', Items.DIAMOND)
                .define('C', ModItems.AI_CHIP.get())
                .unlockedBy("has_ai_chip", has(ModItems.AI_CHIP.get()))
                .save(recipeOutput);

        // Data Cable — cheap network conduit: redstone wrapped in iron nuggets + glass. Makes a handful.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DATA_CABLE.get(), 8)
                .pattern("NNN")
                .pattern("RGR")
                .pattern("NNN")
                .define('N', Items.IRON_NUGGET)
                .define('R', Items.REDSTONE)
                .define('G', Items.GLASS_PANE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        // Storage Terminal — the network access console: a glass screen over an AI chip, iron-framed.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.STORAGE_TERMINAL.get())
                .pattern("IGI")
                .pattern("GCG")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('C', ModItems.AI_CHIP.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_ai_chip", has(ModItems.AI_CHIP.get()))
                .save(recipeOutput);

        // Cache Crawler — a small defrag spider-bot: an AI-chip body with redstone and iron-nugget legs.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.CACHE_CRAWLER_SPAWN_EGG.get())
                .pattern("N N")
                .pattern("RCR")
                .pattern("N N")
                .define('N', Items.IRON_NUGGET)
                .define('R', Items.REDSTONE)
                .define('C', ModItems.AI_CHIP.get())
                .unlockedBy("has_ai_chip", has(ModItems.AI_CHIP.get()))
                .save(recipeOutput);

        // Combustion Dynamo — a furnace core wrapped in iron, with copper coils and a redstone regulator.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.COMBUSTION_DYNAMO.get())
                .pattern("ICI")
                .pattern("IFI")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COPPER_INGOT)
                .define('F', Blocks.FURNACE)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_furnace", has(Blocks.FURNACE))
                .save(recipeOutput);

        // Power Conduit — cheap cable: a redstone core sheathed in iron nuggets. Makes a handful.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.POWER_CONDUIT.get(), 6)
                .pattern("NNN")
                .pattern("RRR")
                .pattern("NNN")
                .define('N', Items.IRON_NUGGET)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        // The Robot Builder: the animated fabrication rig where mechs are assembled.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.MECH_ASSEMBLY_BENCH.get())
                .pattern("ICI")
                .pattern("RBR")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('B', ModBlocks.ASSEMBLY_WORKSHOP.get())
                .unlockedBy("has_assembly_workshop", has(ModBlocks.ASSEMBLY_WORKSHOP.get()))
                .save(recipeOutput);

        // The Upgrade Station: an anvil-cored bench that stamps upgrades onto a chassis.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.UPGRADE_STATION.get())
                .pattern("ICI")
                .pattern("RAR")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('A', Blocks.ANVIL)
                .unlockedBy("has_anvil", has(Blocks.ANVIL))
                .save(recipeOutput);

        // The Mech Tablet: a handheld command terminal — an AI chip on a glass screen over a redstone core.
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.MECH_TABLET.get())
                .pattern("IGI")
                .pattern("ICI")
                .pattern("IRI")
                .define('I', Items.IRON_INGOT)
                .define('G', Items.GLASS_PANE)
                .define('C', ModItems.AI_CHIP.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_ai_chip", has(ModItems.AI_CHIP.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.MECH_LINKER.get())
                .pattern(" R ")
                .pattern(" I ")
                .pattern(" I ")
                .define('R', Items.REDSTONE)
                .define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(recipeOutput);

        // The Holo-Guide: a book + a glowing redstone projector — cheap, so players can read it early.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, ModItems.HOLO_GUIDE.get())
                .requires(Items.BOOK)
                .requires(Items.REDSTONE)
                .requires(Items.GLOWSTONE_DUST)
                .unlockedBy("has_book", has(Items.BOOK))
                .save(recipeOutput);

        // ----- Upgrade modules (vanilla crafting): a copper frame around a thematic catalyst -----
        // Core stats
        upgradeModule(recipeOutput, UpgradeType.SPEED, Items.SUGAR);
        upgradeModule(recipeOutput, UpgradeType.CAPACITY, Items.REDSTONE_BLOCK);
        upgradeModule(recipeOutput, UpgradeType.RANGE, Items.ENDER_PEARL);
        upgradeModule(recipeOutput, UpgradeType.EFFICIENCY, Items.GOLD_BLOCK);
        // Mining
        upgradeModule(recipeOutput, UpgradeType.FORTUNE, Items.DIAMOND);
        upgradeModule(recipeOutput, UpgradeType.SMELTER, Blocks.FURNACE);
        upgradeModule(recipeOutput, UpgradeType.SILK_TOUCH, Items.STRING);
        upgradeModule(recipeOutput, UpgradeType.HAZARD_SEAL, Items.BUCKET);
        // Farming
        upgradeModule(recipeOutput, UpgradeType.FERTILIZER, Items.BONE_BLOCK);
        // Combat
        upgradeModule(recipeOutput, UpgradeType.SHARPNESS, Items.IRON_SWORD);
        upgradeModule(recipeOutput, UpgradeType.LOOTING, Items.EMERALD);
        upgradeModule(recipeOutput, UpgradeType.FIRE_ASPECT, Items.BLAZE_POWDER);
        upgradeModule(recipeOutput, UpgradeType.KNOCKBACK, Blocks.PISTON);
        upgradeModule(recipeOutput, UpgradeType.ARMOR, Items.IRON_BLOCK);
        upgradeModule(recipeOutput, UpgradeType.SOUL_TANK, Items.EXPERIENCE_BOTTLE);
        // Utility
        upgradeModule(recipeOutput, UpgradeType.MAGNET, Blocks.HOPPER);
        upgradeModule(recipeOutput, UpgradeType.SOLAR, Blocks.DAYLIGHT_DETECTOR);
        upgradeModule(recipeOutput, UpgradeType.ENDER_LINK, Blocks.ENDER_CHEST);

        // ----- Fabricator (powered, data-driven) recipes: raw materials + power -> mech parts -----
        fab(recipeOutput, "mech_plates", new ItemStack(ModItems.MECH_PLATES.get()),
                SizedIngredient.of(Items.IRON_INGOT, 3),
                SizedIngredient.of(Items.COPPER_INGOT, 2));
        fab(recipeOutput, "mech_core", new ItemStack(ModItems.MECH_CORE.get()),
                SizedIngredient.of(Items.REDSTONE, 3),
                SizedIngredient.of(Items.COPPER_INGOT, 1),
                SizedIngredient.of(Items.DIAMOND, 1));
        fab(recipeOutput, "ai_chip", new ItemStack(ModItems.AI_CHIP.get()),
                SizedIngredient.of(Items.GOLD_INGOT, 2),
                SizedIngredient.of(Items.LAPIS_LAZULI, 2),
                SizedIngredient.of(Items.REDSTONE, 2));

        // Role circuits are fabricated too — gold + redstone around the role-defining tool.
        circuit(recipeOutput, MechRole.MINING, Items.IRON_PICKAXE);
        circuit(recipeOutput, MechRole.FARMING, Items.IRON_HOE);
        circuit(recipeOutput, MechRole.BUILDING, Blocks.PISTON);
        circuit(recipeOutput, MechRole.COMBAT, Items.IRON_SWORD);
    }

    /** A role circuit, fabricated: gold + redstone + the role-defining tool. */
    private void circuit(RecipeOutput out, MechRole role, ItemLike tool) {
        fab(out, role.id() + "_circuit", new ItemStack(ModItems.circuit(role).get()),
                SizedIngredient.of(Items.GOLD_INGOT, 2),
                SizedIngredient.of(Items.REDSTONE, 2),
                SizedIngredient.of(tool, 1));
    }

    /** An upgrade module: a copper/redstone frame around a stat catalyst (vanilla crafting). */
    private void upgradeModule(RecipeOutput out, UpgradeType type, ItemLike catalyst) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.upgrade(type).get())
                .pattern("RCR")
                .pattern("CXC")
                .pattern("RCR")
                .define('C', Items.COPPER_INGOT)
                .define('R', Items.REDSTONE)
                .define('X', catalyst)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(out);
    }

    /** Emit a Fabricator (AssemblyRecipe) recipe producing {@code result}. */
    private void fab(RecipeOutput out, String name, ItemStack result, SizedIngredient... inputs) {
        out.accept(
                ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "fabricator/" + name),
                new AssemblyRecipe(List.of(inputs), result),
                null);
    }
}
