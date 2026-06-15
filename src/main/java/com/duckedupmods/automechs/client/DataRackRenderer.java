package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.entity.DataRackBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib block-entity renderer for the Data Rack. After drawing the cabinet model it overlays a live
 * glowing readout on the rack's front screen — the Defrag% and stored item count — so the rack's status is
 * legible at a glance in-world, exactly like a real drive bay's display panel.
 */
public class DataRackRenderer extends GeoBlockRenderer<DataRackBlockEntity> {

    public DataRackRenderer(BlockEntityRendererProvider.Context context) {
        super(new DataRackModel());
    }

    @Override
    public void render(DataRackBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        if (be.getLevel() == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);

        int frag = be.displayFrag();
        int items = be.displayItems();
        int fragColor = frag <= 0 ? 0xFF4BE3A8 : (frag < 40 ? 0xFFB6F5CC : 0xFFFFB040);
        String[] lines = {
                "FRAG " + frag + "%",
                items + " itm"
        };
        int[] colors = { fragColor, 0xFF6FE6F2 };
        BlockReadout.drawFront(poseStack, bufferSource, Minecraft.getInstance().font, facing, 0.60F, lines, colors);

        // AE2-style drive-bay LED strip: lit count tracks how full the rack is, colour tracks fragmentation.
        BlockReadout.drawLedRow(poseStack, bufferSource, Minecraft.getInstance().font, facing, 0.42F,
                fillLeds(be.displayUsed(), be.totalSectors(), frag));
    }

    /** A 7-LED capacity bar: lit ∝ used/total, lit colour green→amber→red as fragmentation rises. */
    private static int[] fillLeds(int used, int total, int frag) {
        final int n = 7;
        int lit = used <= 0 ? 0 : Math.max(1, Math.min(n, Math.round((float) n * used / Math.max(1, total))));
        int litColor = frag < 25 ? 0xFF4BE3A8 : (frag < 55 ? 0xFFFFB040 : 0xFFFF5A3A);
        int off = 0xFF14222C;
        int[] leds = new int[n];
        for (int i = 0; i < n; i++) {
            leds[i] = i < lit ? litColor : off;
        }
        return leds;
    }
}
