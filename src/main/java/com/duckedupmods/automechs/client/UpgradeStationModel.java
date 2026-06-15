package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.entity.UpgradeStationBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for the Upgrade Station pedestal. Resolves the tuning-ring geo, texture, and the
 * idle/work ring-spin animation file.
 */
@SuppressWarnings("deprecation")
public class UpgradeStationModel extends GeoModel<UpgradeStationBlockEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "geo/block/upgrade_station.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "textures/block/upgrade_station.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "animations/block/upgrade_station.animation.json");

    @Override
    public ResourceLocation getModelResource(UpgradeStationBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(UpgradeStationBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(UpgradeStationBlockEntity animatable) {
        return ANIMATION;
    }
}
