package com.duckedupmods.automechs.registry;

import java.util.function.Supplier;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.recipe.AssemblyRecipe;
import com.duckedupmods.automechs.recipe.AssemblyRecipeSerializer;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Recipe types and serializers registered by Automechs.
 */
public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Automechs.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Automechs.MODID);

    public static final Supplier<RecipeType<AssemblyRecipe>> ASSEMBLY_TYPE =
            TYPES.register("assembly", () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "assembly")));

    public static final Supplier<AssemblyRecipeSerializer> ASSEMBLY_SERIALIZER =
            SERIALIZERS.register("assembly", AssemblyRecipeSerializer::new);

    private ModRecipes() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        SERIALIZERS.register(bus);
    }
}
