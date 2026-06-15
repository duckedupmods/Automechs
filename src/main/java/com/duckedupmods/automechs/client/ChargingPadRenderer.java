package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.entity.ChargingPadBlockEntity;

import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib block-entity renderer for the Charging Pad. Draws the pad model with its coil spun by the
 * GeckoLib idle/charging animation. The bounds are widened slightly so the corner posts aren't culled.
 */
public class ChargingPadRenderer extends GeoBlockRenderer<ChargingPadBlockEntity> {

    public ChargingPadRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        super(new ChargingPadModel());
    }

    @Override
    public AABB getRenderBoundingBox(ChargingPadBlockEntity animatable) {
        return new AABB(animatable.getBlockPos()).inflate(0.6D);
    }
}
