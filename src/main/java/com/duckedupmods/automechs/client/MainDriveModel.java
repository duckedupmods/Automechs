package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.entity.MainDriveBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib model for the Main Drive: resolves its geo, texture and idle animation. */
@SuppressWarnings("deprecation")
public class MainDriveModel extends GeoModel<MainDriveBlockEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "geo/block/main_drive.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "textures/block/main_drive.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "animations/block/main_drive.animation.json");

    @Override
    public ResourceLocation getModelResource(MainDriveBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MainDriveBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MainDriveBlockEntity animatable) {
        return ANIMATION;
    }
}
