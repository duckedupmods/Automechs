package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.menu.UpgradeStationMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the {@link UpgradeStationMenu}. Drawn procedurally in the shared industrial dark-metal style:
 * a gunmetal plate with riveted corners, a recessed chassis→module dock, an apply-progress arrow, and the
 * segmented cyan energy gauge.
 */
public class UpgradeStationScreen extends AbstractContainerScreen<UpgradeStationMenu> {

    // Energy gauge geometry (also used for the hover tooltip hit-test).
    private static final int GAUGE_X = 153;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_W = 11;
    private static final int GAUGE_H = 50;

    public UpgradeStationScreen(UpgradeStationMenu menu, Inventory playerInventory, Component title) {
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

        // Recessed dock behind the chassis + module slots.
        GuiHelper.inset(g, x + 38, y + 29, 60, 26, GuiHelper.METAL_INNER);
        for (Slot slot : this.menu.slots) {
            GuiHelper.darkSlot(g, x + slot.x, y + slot.y);
        }

        // Apply-progress arrow from chassis to module.
        GuiHelper.progressArrow(g, x + 64, y + 39, 14, 8, this.menu.getProgress(), this.menu.getMaxProgress());

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

        // Tiny captions over the dock so the install direction is obvious: the module (left) is consumed
        // INTO the mech (right). Centred over each 18px slot well (module slot x=44, chassis slot x=80).
        caption(g, "Module", 44 + 9, 19, GuiHelper.TEXT_DIM);
        caption(g, "Mech", 80 + 9, 19, GuiHelper.ACCENT);
    }

    /** Draw a short caption horizontally centred on {@code cx} at row {@code y}. */
    private void caption(GuiGraphics g, String text, int cx, int y, int colour) {
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, colour, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
