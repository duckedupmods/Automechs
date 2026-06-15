package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.entity.MiningMech;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renderer for the Mining Mech. The {@link MiningMechModel} resolves the model/texture per
 * chassis tier (and shares one animation file across tiers).
 */
public class MiningMechRenderer extends GeoEntityRenderer<MiningMech> {

    public MiningMechRenderer(EntityRendererProvider.Context context) {
        super(context, new MiningMechModel());
        this.shadowRadius = 0.4F;
    }
}
