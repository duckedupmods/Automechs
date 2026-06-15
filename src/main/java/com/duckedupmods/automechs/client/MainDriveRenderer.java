package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.entity.MainDriveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib block-entity renderer for the Main Drive — the network's "computer". Its screen runs a live
 * terminal: a status header plus streams of scrolling green hex code (Matrix-style), so the controller
 * reads as a working server console. The code animates off the world game-time; the header turns red and
 * the stream stalls when the network loses power.
 */
public class MainDriveRenderer extends GeoBlockRenderer<MainDriveBlockEntity> {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final int GREEN = 0xFF44FF6A;
    private static final int GREEN_DIM = 0xFF1FB048;
    private static final int RED = 0xFFFF5A3A;

    public MainDriveRenderer(BlockEntityRendererProvider.Context context) {
        super(new MainDriveModel());
    }

    @Override
    public void render(MainDriveBlockEntity be, float partialTick, PoseStack poseStack,
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

        boolean online = be.displayOnline();
        // Scroll the code by stepping the seed with game-time (frozen when offline, so the stream stalls).
        long tick = online ? be.getLevel().getGameTime() / 3L : 0L;

        String[] lines = {
                online ? "> RUN " + be.displayItems() : "> HALT",
                codeLine(tick, 1),
                codeLine(tick, 2),
                codeLine(tick, 3),
        };
        int[] colors = {
                online ? GREEN : RED,
                GREEN, GREEN_DIM, GREEN,
        };
        BlockReadout.drawFront(poseStack, bufferSource, Minecraft.getInstance().font, facing, 0.56F, lines, colors);
    }

    /** A row of pseudo-random hex, seeded by tick + row so it scrolls/churns like running code. */
    private static String codeLine(long tick, int row) {
        long s = (tick + row * 92821L) * 6364136223846793005L + 1442695040888963407L;
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            sb.append(HEX[(int) ((s >>> 40) & 0xF)]);
        }
        return sb.toString();
    }
}
