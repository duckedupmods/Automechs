package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.entity.UpgradeStationBlockEntity;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.registry.ModEntities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib block-entity renderer for the Upgrade Station. Draws the tuning-ring pedestal (rings spun by
 * the GeckoLib idle/work animation) and, while a chassis is mounted, renders the mech standing on the
 * pedestal with the rings orbiting it. The render bounds are widened so neither the rings nor the mech
 * (both of which extend above the block) get culled.
 */
public class UpgradeStationRenderer extends GeoBlockRenderer<UpgradeStationBlockEntity> {

    private MiningMech preview;
    private PreviewMechRenderer previewRenderer;

    public UpgradeStationRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        super(new UpgradeStationModel());
    }

    @Override
    public AABB getRenderBoundingBox(UpgradeStationBlockEntity animatable) {
        return new AABB(animatable.getBlockPos()).inflate(1.5D);
    }

    @Override
    public void render(UpgradeStationBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(be, partialTick, pose, buffers, packedLight, packedOverlay);
        renderMountedMech(be, partialTick, pose, buffers, packedLight);
    }

    /** Render the mounted mech standing on the pedestal, slowly turning so the player can inspect it. */
    private void renderMountedMech(UpgradeStationBlockEntity be, float partialTick, PoseStack pose,
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
        this.preview.setDisplayEnergy(1); // keep the mounted mech powered so it never shows the offline slump
        this.previewRenderer.buildFraction = 1.0F; // fully built — this is a finished chassis being tuned

        float spin = ((be.getLevel() != null ? be.getLevel().getGameTime() : 0L) + partialTick) * 0.6F;

        pose.pushPose();
        pose.translate(0.5D, 0.58D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(0.45F, 0.45F, 0.45F);
        try {
            this.previewRenderer.render(this.preview, 0.0F, partialTick, pose, buffers, packedLight);
        } catch (Exception ignored) {
            // A preview render failure must never crash the game; just skip it this frame.
        }
        pose.popPose();
    }
}
