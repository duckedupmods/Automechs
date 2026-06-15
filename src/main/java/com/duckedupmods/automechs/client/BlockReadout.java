package com.duckedupmods.automechs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

/**
 * Draws a glowing readout (text lines + an LED status row) <b>flat on the front screen</b> of a horizontal
 * block — like a real monitor, not floating in front of it. Called from a block-entity renderer AFTER the
 * GeckoLib model is drawn.
 *
 * <p>This mirrors vanilla {@code SignRenderer} exactly, which is the canonical "text on a block face":
 * rotate to the block's facing, step out to the glass, then {@code scale(f, -f, f)} — note the
 * <b>positive X, negative Y</b>. The {@code -Y} flips the font's Y-down to upright AND inverts the quad
 * winding so the text faces outward; negating X as well (the old bug) cancels that flip and the text
 * back-faces → culled → invisible. Drawn with {@link Font.DisplayMode#POLYGON_OFFSET} so it renders on the
 * screen surface without z-fighting.
 */
public final class BlockReadout {

    private BlockReadout() {}

    /** Distance from block centre to just clear of the screen surface (screens protrude ~0.53 from centre). */
    private static final float FRONT_OUT = 0.55F;
    /** World-units per text pixel — tuned so a short line spans the screen panel. */
    private static final float SCALE = 0.011F;
    /** Pixels between stacked text lines. */
    private static final float LINE_STEP = 9.0F;
    /** POLYGON_OFFSET keeps the text on the glass surface (depth-tested, but nudged forward, no z-fight). */
    private static final Font.DisplayMode MODE = Font.DisplayMode.POLYGON_OFFSET;

    /**
     * @param yCenter height (0..1) on the block face to centre the block of lines on
     * @param lines   the text lines, top to bottom
     * @param colors  ARGB colour per line (parallel to {@code lines})
     */
    public static void drawFront(PoseStack ps, MultiBufferSource buffers, Font font, Direction facing,
                                 float yCenter, String[] lines, int[] colors) {
        ps.pushPose();
        anchor(ps, facing, yCenter);

        float top = -((lines.length - 1) * LINE_STEP) / 2.0F;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float w = font.width(line);
            font.drawInBatch(line, -w / 2.0F, top + i * LINE_STEP, colors[i], false,
                    ps.last().pose(), buffers, MODE, 0, LightTexture.FULL_BRIGHT);
        }
        ps.popPose();
    }

    /** The solid full-block glyph, drawn small + glowing to fake an LED bay light. */
    private static final String LED = "█";
    /** Glyph shrink for an LED relative to the text scale (so they read as small square lights). */
    private static final float LED_SCALE = 0.62F;
    /** Horizontal spacing between LED centres, in pre-scale text pixels. */
    private static final float LED_STEP = 5.4F;

    /**
     * Draws a horizontal strip of glowing LED lights flat on the screen — the AE2 drive-bay look. Each entry
     * in {@code colors} is one LED (ARGB); a dark colour reads as an unlit socket.
     *
     * @param yCenter height (0..1) on the block face for the LED row's centre line
     * @param colors  ARGB colour per LED, left to right
     */
    public static void drawLedRow(PoseStack ps, MultiBufferSource buffers, Font font, Direction facing,
                                  float yCenter, int[] colors) {
        if (colors.length == 0) {
            return;
        }
        ps.pushPose();
        anchor(ps, facing, yCenter);

        float start = -((colors.length - 1) * LED_STEP) / 2.0F;
        for (int i = 0; i < colors.length; i++) {
            ps.pushPose();
            ps.translate(start + i * LED_STEP, 0.0D, 0.0D);
            ps.scale(LED_SCALE, LED_SCALE, 1.0F);
            // Centre the ~6x8 block glyph on its slot.
            font.drawInBatch(LED, -3.0F, -4.0F, colors[i], false,
                    ps.last().pose(), buffers, MODE, 0, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
        ps.popPose();
    }

    /**
     * Move to the screen surface, rotate flat onto the block's front face, and apply the vanilla
     * sign-text {@code (+X, -Y, +Z)} scale (the {@code -Y} both un-flips the font and faces it outward).
     */
    private static void anchor(PoseStack ps, Direction facing, float yCenter) {
        ps.translate(0.5D, yCenter, 0.5D);
        ps.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        ps.translate(0.0D, 0.0D, FRONT_OUT);
        ps.scale(SCALE, -SCALE, SCALE);
    }
}
