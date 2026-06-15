package com.duckedupmods.automechs.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * Codec-based (de)serializer for {@link AssemblyRecipe} — JSON form is
 * {@code { "ingredients": [ {item/tag, count}... ], "result": {...} }}.
 */
public class AssemblyRecipeSerializer implements RecipeSerializer<AssemblyRecipe> {

    public static final MapCodec<AssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SizedIngredient.FLAT_CODEC.listOf().fieldOf("ingredients").forGetter(AssemblyRecipe::inputs),
            ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.getResultItem(null))
    ).apply(instance, AssemblyRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblyRecipe::inputs,
            ItemStack.STREAM_CODEC, recipe -> recipe.getResultItem(null),
            AssemblyRecipe::new);

    @Override
    public MapCodec<AssemblyRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, AssemblyRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
