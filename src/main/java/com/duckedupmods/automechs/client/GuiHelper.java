package com.duckedupmods.automechs.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Small helpers for drawing the Automechs container UIs procedurally — an industrial dark-metal look
 * (gunmetal plates, beveled frames, rivets, and a segmented energy gauge) that matches the printing
 * capsule's hull, rather than the flat vanilla grey.
 */
public final class GuiHelper {

    private GuiHelper() {}

    // Industrial gunmetal palette (kept in sync with the capsule hull tones).
    public static final int METAL_BASE = 0xFF3A424C;
    public static final int METAL_HI   = 0xFF5C6772;
    public static final int METAL_LO   = 0xFF20262E;
    public static final int METAL_INNER = 0xFF2C333B;
    public static final int RIVET      = 0xFF161A1F;
    public static final int RIVET_HI   = 0xFF6E7B8A;
    public static final int ACCENT     = 0xFF3CD2E8;
    public static final int TEXT_LIGHT = 0xFFC4D2DE;
    public static final int TEXT_DIM   = 0xFF7E8C99;

    /**
     * Draws a recessed 18x18 slot well at the given inner-slot top-left, matching vanilla's look:
     * dark top/left edges, light bottom/right edges, neutral grey interior.
     */
    public static void slot(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF8B8B8B); // interior
        g.fill(sx - 1, sy - 1, sx + 17, sy, 0xFF373737);      // top (dark)
        g.fill(sx - 1, sy - 1, sx, sy + 17, 0xFF373737);      // left (dark)
        g.fill(sx - 1, sy + 16, sx + 17, sy + 17, 0xFFFFFFFF); // bottom (light)
        g.fill(sx + 16, sy - 1, sx + 17, sy + 17, 0xFFFFFFFF); // right (light)
    }

    /**
     * A steel-themed recessed slot well (18x18) for the dark industrial panels: a muted gunmetal
     * interior with dark top/left edges and a brushed-steel highlight on the bottom/right, so the dock
     * reads as machined metal rather than the bright vanilla grey.
     */
    public static void darkSlot(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF646E79); // steel interior
        g.fill(sx - 1, sy - 1, sx + 17, sy, 0xFF181C22);      // top (deep shadow)
        g.fill(sx - 1, sy - 1, sx, sy + 17, 0xFF181C22);      // left (deep shadow)
        g.fill(sx - 1, sy + 16, sx + 17, sy + 17, 0xFF99A6B3); // bottom (steel highlight)
        g.fill(sx + 16, sy - 1, sx + 17, sy + 17, 0xFF99A6B3); // right (steel highlight)
    }

    /**
     * A raised metal panel: solid base with a bright top/left bevel and a dark bottom/right bevel,
     * giving the plate a tactile, machined edge.
     */
    public static void bevelPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, METAL_BASE);
        g.fill(x, y, x + w, y + 1, METAL_HI);              // top highlight
        g.fill(x, y, x + 1, y + h, METAL_HI);              // left highlight
        g.fill(x + w - 1, y, x + w, y + h, METAL_LO);      // right shadow
        g.fill(x, y + h - 1, x + w, y + h, METAL_LO);      // bottom shadow
    }

    /**
     * A recessed inset (the inverse of {@link #bevelPanel}) — dark top/left, light bottom/right —
     * used to sink a region (gauge wells, sub-panels) into the plate.
     */
    public static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, METAL_LO);              // top shadow
        g.fill(x, y, x + 1, y + h, METAL_LO);              // left shadow
        g.fill(x + w - 1, y, x + w, y + h, METAL_HI);      // right highlight
        g.fill(x, y + h - 1, x + w, y + h, METAL_HI);      // bottom highlight
    }

    /** A 2x2 rivet with a single-pixel highlight, for panel corners. */
    public static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, RIVET);
        g.fill(x, y, x + 1, y + 1, RIVET_HI);
    }

    /** A small 5x7 lightning bolt glyph centred on (cx, top), in the given colour. */
    public static void bolt(GuiGraphics g, int cx, int top, int colour) {
        g.fill(cx, top, cx + 2, top + 1, colour);
        g.fill(cx - 1, top + 1, cx + 2, top + 2, colour);
        g.fill(cx - 1, top + 2, cx + 1, top + 3, colour);
        g.fill(cx - 2, top + 3, cx + 3, top + 4, colour);
        g.fill(cx, top + 4, cx + 2, top + 5, colour);
        g.fill(cx - 1, top + 5, cx + 1, top + 6, colour);
        g.fill(cx - 1, top + 6, cx, top + 7, colour);
    }

    /**
     * A vertical combustion flame gauge: a recessed warm well with a hot orange-to-amber gradient that
     * fills from the bottom and tapers to a flame tip at the top of the lit portion, plus a brighter
     * yellow core. {@code burn}/{@code maxBurn} drive how much of the flame is lit.
     */
    public static void flameGauge(GuiGraphics g, int x, int y, int w, int h, int burn, int maxBurn) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, RIVET);
        int well = 0xFF1B1410;
        inset(g, x - 1, y - 1, w + 2, h + 2, well);

        int max = maxBurn <= 0 ? 1 : maxBurn;
        int lit = (int) ((long) h * Math.max(0, Math.min(burn, max)) / max);
        if (lit <= 0) {
            return;
        }
        int top = y + h - lit;
        // Outer flame body: amber at the base, bright orange at the tip.
        g.fillGradient(x, top, x + w, y + h, 0xFFFFB42E, 0xFFD24417);
        // Taper the top into a flame tip by carving the two top corners back to the well colour.
        g.fill(x, top, x + 2, top + 2, well);
        g.fill(x + w - 2, top, x + w, top + 2, well);
        g.fill(x, top + 1, x + 1, top + 3, well);
        g.fill(x + w - 1, top + 1, x + w, top + 3, well);
        // Inner yellow core, inset and a touch shorter than the body.
        int coreTop = Math.min(y + h, top + 3);
        if (coreTop < y + h) {
            g.fillGradient(x + 3, coreTop, x + w - 3, y + h, 0xFFFFE873, 0xFFFF9A2A);
        }
    }

    /**
     * A vertical segmented energy gauge. Drawn as a recessed well with a hot cyan gradient fill
     * (bright at the top of the charge, deeper toward the bottom), broken into 3px segments by thin
     * separator lines, with a left-edge highlight and a soft top glow on the charge line.
     */
    public static void energyGauge(GuiGraphics g, int x, int y, int w, int h, int energy, int maxEnergy) {
        // Frame + dark well.
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, RIVET);
        inset(g, x - 1, y - 1, w + 2, h + 2, 0xFF0C1517);

        int max = maxEnergy <= 0 ? 1 : maxEnergy;
        int fillH = (int) ((long) h * Math.max(0, Math.min(energy, max)) / max);
        if (fillH <= 0) {
            return;
        }
        int top = y + h - fillH;
        // Hot gradient charge: bright cyan at the charge line, deep teal at the base.
        g.fillGradient(x, top, x + w, y + h, 0xFF9CF4FF, 0xFF137A88);
        // Left-edge highlight column.
        g.fill(x, top, x + 1, y + h, 0x66FFFFFF);
        // Segment separators every 3px.
        for (int sy = y + h - 3; sy > top; sy -= 3) {
            g.fill(x, sy, x + w, sy + 1, 0x44000000);
        }
        // Soft glow line at the top of the charge.
        g.fill(x, top, x + w, top + 1, 0xCCFFFFFF);
    }

    /**
     * A left-to-right progress arrow (shaft + arrowhead) that fills with cyan as {@code progress} climbs,
     * over a dark unfilled silhouette — the "input flows to output" indicator for crafting machines.
     */
    public static void progressArrow(GuiGraphics g, int x, int y, int w, int h, int progress, int maxProgress) {
        int max = maxProgress <= 0 ? 1 : maxProgress;
        int fillW = (int) ((long) w * Math.max(0, Math.min(progress, max)) / max);
        int yc = y + h / 2;
        int shaftW = w - h; // the arrowhead is h wide and tapers to a point
        for (int c = 0; c < w; c++) {
            int top;
            int bot;
            if (c < shaftW) {
                top = yc - 2;
                bot = yc + 2;
            } else {
                int half = (h / 2) * (h - (c - shaftW)) / h; // taper to the tip
                top = yc - half;
                bot = yc + half + 1;
            }
            int col = c < fillW ? ACCENT : 0xFF101A1C;
            if (c == fillW - 1) {
                col = 0xFFCFFFFF; // bright leading edge
            }
            g.fill(x + c, top, x + c + 1, bot, col);
        }
    }

    /**
     * A horizontal industrial progress bar — recessed well with a cyan gradient fill and a bright
     * leading edge, used for the build-progress readout.
     */
    public static void progressBar(GuiGraphics g, int x, int y, int w, int h, int progress, int maxProgress) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, RIVET);
        inset(g, x - 1, y - 1, w + 2, h + 2, 0xFF101A1C);

        int max = maxProgress <= 0 ? 1 : maxProgress;
        int fillW = (int) ((long) w * Math.max(0, Math.min(progress, max)) / max);
        if (fillW <= 0) {
            return;
        }
        g.fillGradient(x, y, x + fillW, y + h, 0xFF6FE6F2, 0xFF1C97A6);
        // Top highlight + bright leading edge.
        g.fill(x, y, x + fillW, y + 1, 0x55FFFFFF);
        if (fillW < w) {
            g.fill(x + fillW - 1, y, x + fillW, y + h, 0xCCFFFFFF);
        }
    }
}
