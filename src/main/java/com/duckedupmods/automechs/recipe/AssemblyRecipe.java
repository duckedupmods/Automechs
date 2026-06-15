package com.duckedupmods.automechs.recipe;

import java.util.List;

import com.duckedupmods.automechs.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * A data-driven Assembly Workshop recipe: a set of counted ingredients that combine into one result.
 * Matching is order-independent and count-aware; packs/addons can add their own via JSON.
 */
public class AssemblyRecipe implements Recipe<AssemblyRecipeInput> {

    private final List<SizedIngredient> inputs;
    private final ItemStack result;

    public AssemblyRecipe(List<SizedIngredient> inputs, ItemStack result) {
        this.inputs = inputs;
        this.result = result;
    }

    public List<SizedIngredient> inputs() {
        return this.inputs;
    }

    @Override
    public boolean matches(AssemblyRecipeInput input, Level level) {
        int[] remaining = new int[input.size()];
        for (int i = 0; i < input.size(); i++) {
            remaining[i] = input.getItem(i).getCount();
        }
        for (SizedIngredient ingredient : this.inputs) {
            int need = ingredient.count();
            for (int i = 0; i < input.size() && need > 0; i++) {
                if (remaining[i] > 0 && ingredient.ingredient().test(input.getItem(i))) {
                    int take = Math.min(remaining[i], need);
                    remaining[i] -= take;
                    need -= take;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(AssemblyRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (SizedIngredient ingredient : this.inputs) {
            list.add(ingredient.ingredient());
        }
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ASSEMBLY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.ASSEMBLY_TYPE.get();
    }
}
