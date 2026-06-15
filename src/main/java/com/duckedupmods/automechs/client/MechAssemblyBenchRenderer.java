package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.MechAssemblyBenchBlock;
import com.duckedupmods.automechs.block.MechMultiblock;
import com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.registry.ModEntities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib block-entity renderer for the Robot Builder. Draws the printing-capsule model (auto-rotated by
 * {@code HORIZONTAL_FACING}) and, while parts are loaded, renders the mech being printed on the bed —
 * revealed bottom-up part-by-part as the build progresses (see {@link PreviewMechRenderer}). The render
 * bounding box is widened to the whole multiblock so neither the model nor the preview gets culled.
 */
public class MechAssemblyBenchRenderer extends GeoBlockRenderer<MechAssemblyBenchBlockEntity> {

    private MiningMech preview;
    private PreviewMechRenderer previewRenderer;

    public MechAssemblyBenchRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        super(new MechAssemblyBenchModel());
    }

    @Override
    public AABB getRenderBoundingBox(MechAssemblyBenchBlockEntity animatable) {
        return new AABB(animatable.getBlockPos()).inflate(MechMultiblock.RENDER_PAD);
    }

    @Override
    public void render(MechAssemblyBenchBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(be, partialTick, pose, buffers, packedLight, packedOverlay);
        renderAssemblingMech(be, partialTick, pose, buffers, packedLight);
    }

    /** Render the mech being printed, centred on the bed, revealed part-by-part as the build progresses. */
    private void renderAssemblingMech(MechAssemblyBenchBlockEntity be, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int packedLight) {
        if (!be.isPreviewActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (this.preview == null || this.preview.level() != level) {
            this.preview = ModEntities.MINING_MECH.get().create(level);
            if (this.preview == null) {
                return;
            }
        }
        if (this.previewRenderer == null) {
            this.previewRenderer = new PreviewMechRenderer(new EntityRendererProvider.Context(
                    mc.getEntityRenderDispatcher(), mc.getItemRenderer(), mc.getBlockRenderer(),
                    mc.getEntityRenderDispatcher().getItemInHandRenderer(), mc.getResourceManager(),
                    mc.getEntityModels(), mc.font));
        }
        this.preview.setRole(be.getPreviewRole());
        this.preview.setDisplayEnergy(1); // keep the preview powered so it never shows the offline slump
        this.previewRenderer.buildFraction = Mth.clamp(be.getBuildFraction(), 0.0F, 1.0F);

        // Centre on the print bed. The footprint extends backward from the controller, so the bed sits half
        // a block toward the back — offset by the facing's back vector so it's centred for every rotation.
        Direction back = be.getBlockState().getValue(MechAssemblyBenchBlock.FACING).getOpposite();
        double cx = 0.5D + back.getStepX() * 0.5D;
        double cz = 0.5D + back.getStepZ() * 0.5D;
        float spin = ((be.getLevel() != null ? be.getLevel().getGameTime() : 0L) + partialTick) * 1.2F;
        float scale = 0.72F;

        pose.pushPose();
        pose.translate(cx, 0.32D, cz);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(scale, scale, scale);
        try {
            this.previewRenderer.render(this.preview, 0.0F, partialTick, pose, buffers, packedLight);
        } catch (Exception ignored) {
            // A preview render failure must never crash the game; just skip it this frame.
        }
        pose.popPose();
    }
}
