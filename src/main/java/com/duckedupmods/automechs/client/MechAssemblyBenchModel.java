package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for the Mech Assembly Bench block entity. Resolves the gantry geo, texture, and the
 * idle/work animation file.
 *
 * <p>(GeckoLib 4.7 left the single-arg resource overrides abstract-but-deprecated; see the
 * {@code deprecation} suppression — same transitional-API situation as the mech model.)
 */
@SuppressWarnings("deprecation")
public class MechAssemblyBenchModel extends GeoModel<MechAssemblyBenchBlockEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "geo/block/mech_assembly_bench.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "textures/block/mech_assembly_bench.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "animations/block/mech_assembly_bench.animation.json");

    @Override
    public ResourceLocation getModelResource(MechAssemblyBenchBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MechAssemblyBenchBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MechAssemblyBenchBlockEntity animatable) {
        return ANIMATION;
    }
}
