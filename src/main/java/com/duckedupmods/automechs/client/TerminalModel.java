package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.entity.TerminalBlockEntity;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib model for the Storage Terminal: resolves its geo, texture and idle animation. */
@SuppressWarnings("deprecation")
public class TerminalModel extends GeoModel<TerminalBlockEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "geo/block/storage_terminal.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "textures/block/storage_terminal.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "animations/block/storage_terminal.animation.json");

    @Override
    public ResourceLocation getModelResource(TerminalBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TerminalBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(TerminalBlockEntity animatable) {
        return ANIMATION;
    }
}
