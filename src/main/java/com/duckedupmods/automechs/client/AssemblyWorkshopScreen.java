package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.menu.AssemblyWorkshopMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the {@link AssemblyWorkshopMenu}. Drawn procedurally in the shared industrial dark-metal
 * style: a gunmetal plate with riveted corners, a recessed 3×3 input dock, a framed output slot, an
 * "input flows to output" progress arrow, and the segmented cyan energy gauge.
 */
public class AssemblyWorkshopScreen extends AbstractContainerScreen<AssemblyWorkshopMenu> {

    // Energy gauge geometry (also used for the hover tooltip hit-test).
    private static final int GAUGE_X = 153;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_W = 11;
    private static final int GAUGE_H = 50;

    public AssemblyWorkshopScreen(AssemblyWorkshopMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Main gunmetal plate with a beveled machined edge + corner rivets.
        GuiHelper.bevelPanel(g, x, y, this.imageWidth, this.imageHeight);
        GuiHelper.rivet(g, x + 3, y + 3);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + 3);
        GuiHelper.rivet(g, x + 3, y + this.imageHeight - 5);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + this.imageHeight - 5);

        // Recessed input dock behind the 3×3 grid, and a framed output well.
        GuiHelper.inset(g, x + 28, y + 16, 56, 56, GuiHelper.METAL_INNER);
        GuiHelper.inset(g, x + 121, y + 24, 22, 22, GuiHelper.METAL_INNER);

        for (Slot slot : this.menu.slots) {
            GuiHelper.darkSlot(g, x + slot.x, y + slot.y);
        }

        // "Input flows to output" progress arrow between the grid and the output slot.
        GuiHelper.progressArrow(g, x + 90, y + 31, 24, 8, this.menu.getProgress(), this.menu.getMaxProgress());

        // Energy bolt + segmented cyan energy gauge on the right.
        GuiHelper.bolt(g, x + GAUGE_X + (GAUGE_W / 2), y + 8, GuiHelper.ACCENT);
        GuiHelper.energyGauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H,
                this.menu.getEnergy(), this.menu.getMaxEnergy());
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        int gx = this.leftPos + GAUGE_X;
        int gy = this.topPos + GAUGE_Y;
        if (mouseX >= gx && mouseX < gx + GAUGE_W && mouseY >= gy && mouseY < gy + GAUGE_H) {
            g.renderTooltip(this.font,
                    Component.literal(this.menu.getEnergy() + " / " + this.menu.getMaxEnergy() + " FE"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiHelper.ACCENT, true);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                GuiHelper.TEXT_LIGHT, true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
