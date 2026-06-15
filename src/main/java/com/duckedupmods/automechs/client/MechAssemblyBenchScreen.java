package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.menu.MechAssemblyBenchMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the {@link MechAssemblyBenchMenu}. The whole panel is drawn procedurally as an industrial
 * dark-metal rig (gunmetal plates, riveted corners, a recessed component dock, a segmented cyan energy
 * gauge, and a glowing build-progress bar) so it reads as a real mech fabricator rather than vanilla grey.
 */
public class MechAssemblyBenchScreen extends AbstractContainerScreen<MechAssemblyBenchMenu> {

    // Energy gauge geometry (also used for the hover tooltip hit-test).
    private static final int GAUGE_X = 153;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_W = 11;
    private static final int GAUGE_H = 50;

    public MechAssemblyBenchScreen(MechAssemblyBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Main gunmetal plate with a beveled machined edge.
        GuiHelper.bevelPanel(g, x, y, this.imageWidth, this.imageHeight);
        // Corner rivets.
        GuiHelper.rivet(g, x + 3, y + 3);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + 3);
        GuiHelper.rivet(g, x + 3, y + this.imageHeight - 5);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + this.imageHeight - 5);

        // Title bar strip with an accent underline.
        g.fill(x + 7, y + 16, x + 145, y + 17, GuiHelper.METAL_LO);
        g.fill(x + 7, y + 17, x + 145, y + 18, 0x553CD2E8);

        // Recessed component dock: a labelled header band above the four sockets.
        GuiHelper.inset(g, x + 29, y + 20, 101, 34, GuiHelper.METAL_INNER);
        // Caption header band (the P / C / AI / ◆ labels sit on this recessed strip).
        g.fill(x + 31, y + 21, x + 128, y + 31, 0xFF232A32);
        g.fill(x + 31, y + 31, x + 128, y + 32, 0x553CD2E8); // accent divider under the labels
        // Column ticks splitting the header into one framed cell per socket.
        for (int tick : new int[] {55, 80, 105}) {
            g.fill(x + tick, y + 22, x + tick + 1, y + 31, GuiHelper.METAL_LO);
        }
        for (Slot slot : this.menu.slots) {
            GuiHelper.darkSlot(g, x + slot.x, y + slot.y);
        }

        // Build-progress bar beneath the sockets.
        GuiHelper.progressBar(g, x + 35, y + 60, 91, 6, this.menu.getProgress(), this.menu.getMaxProgress());

        // Energy bolt icon above the gauge.
        GuiHelper.bolt(g, x + GAUGE_X + (GAUGE_W / 2), y + 8, GuiHelper.ACCENT);
        // Segmented cyan energy gauge on the right.
        GuiHelper.energyGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H,
                this.menu.getEnergy(), this.menu.getMaxEnergy());
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Title in cyan accent with a drop shadow so it reads as an illuminated machine label.
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiHelper.ACCENT, true);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                GuiHelper.TEXT_LIGHT, true);
        // Tiny socket captions above the Plates / Core / AI / Circuit slots.
        drawCaption(g, "P", 35);
        drawCaption(g, "C", 60);
        drawCaption(g, "AI", 85);
        drawCaption(g, "◆", 110);
    }

    private void drawCaption(GuiGraphics g, String text, int slotX) {
        int cx = slotX + 8 - this.font.width(text) / 2;
        g.drawString(this.font, text, cx, 22, 0xFFB8C6D2, true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        int gx = this.leftPos + GAUGE_X;
        int gy = this.topPos + GAUGE_Y;
        if (mouseX >= gx && mouseX < gx + GAUGE_W && mouseY >= gy && mouseY < gy + GAUGE_H) {
            g.renderTooltip(this.font,
                    Component.literal(this.menu.getEnergy() + " / " + this.menu.getMaxEnergy() + " FE"),
                    mouseX, mouseY);
        }
    }
}
