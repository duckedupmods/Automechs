package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.menu.CombustionDynamoMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the {@link CombustionDynamoMenu}. Drawn procedurally in the same industrial dark-metal style
 * as the Robot Builder: a gunmetal plate with riveted corners, a recessed fuel dock, a combustion flame
 * gauge that burns down with the current fuel, and the shared segmented cyan energy gauge.
 */
public class CombustionDynamoScreen extends AbstractContainerScreen<CombustionDynamoMenu> {

    // Flame gauge geometry (above the fuel slot).
    private static final int FLAME_X = 81;
    private static final int FLAME_Y = 18;
    private static final int FLAME_W = 14;
    private static final int FLAME_H = 16;

    // Energy gauge geometry (also used for the hover tooltip hit-test).
    private static final int GAUGE_X = 153;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_W = 11;
    private static final int GAUGE_H = 50;

    public CombustionDynamoScreen(CombustionDynamoMenu menu, Inventory playerInventory, Component title) {
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

        // Title bar strip with an accent underline.
        g.fill(x + 7, y + 14, x + 145, y + 15, GuiHelper.METAL_LO);
        g.fill(x + 7, y + 15, x + 145, y + 16, 0x55FF8A28); // warm accent for the combustion machine

        // Recessed fuel dock behind the single fuel slot.
        GuiHelper.inset(g, x + 73, y + 31, 30, 22, GuiHelper.METAL_INNER);
        for (Slot slot : this.menu.slots) {
            GuiHelper.darkSlot(g, x + slot.x, y + slot.y);
        }

        // Combustion flame gauge above the fuel slot.
        GuiHelper.flameGauge(g, x + FLAME_X, y + FLAME_Y, FLAME_W, FLAME_H,
                this.menu.getBurnTime(), this.menu.getBurnDuration());

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
