package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.entity.MiningMech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A throwaway renderer used only for the "mech being printed" preview inside the Robot Builder. It reveals
 * the mech's bones bottom-up as the build progresses (feet → torso → arms → head → ears → antenna), so the
 * robot assembles part-by-part like a 3D print instead of squashing or popping in whole. Drive it by setting
 * {@link #buildFraction} (0..1) before calling {@code render}.
 */
public class PreviewMechRenderer extends GeoEntityRenderer<MiningMech> {

    public float buildFraction = 1.0F;

    public PreviewMechRenderer(EntityRendererProvider.Context context) {
        super(context, new MiningMechModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, MiningMech animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay, int colour) {
        float f = this.buildFraction;
        vis(model, "left_leg", true);
        vis(model, "right_leg", true);
        vis(model, "body", f >= 0.15F);
        vis(model, "left_arm", f >= 0.30F);
        vis(model, "right_arm", f >= 0.30F);
        vis(model, "head", f >= 0.50F);
        vis(model, "left_ear", f >= 0.65F);
        vis(model, "right_ear", f >= 0.65F);
        vis(model, "antenna", f >= 0.80F);
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }

    private static void vis(BakedGeoModel model, String bone, boolean visible) {
        model.getBone(bone).ifPresent(b -> b.setHidden(!visible));
    }
}
