package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.entity.ChargingPadBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for the Charging Pad. Resolves the pad geo, texture, and the idle/charging coil-spin
 * animation file.
 */
@SuppressWarnings("deprecation")
public class ChargingPadModel extends GeoModel<ChargingPadBlockEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "geo/block/charging_pad.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "textures/block/charging_pad.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "animations/block/charging_pad.animation.json");

    @Override
    public ResourceLocation getModelResource(ChargingPadBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ChargingPadBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ChargingPadBlockEntity animatable) {
        return ANIMATION;
    }
}
