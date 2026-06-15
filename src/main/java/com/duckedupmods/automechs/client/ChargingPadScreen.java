package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.entity.ChargingPadBlockEntity;
import com.duckedupmods.automechs.menu.ChargingPadMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the {@link ChargingPadMenu}. Industrial dark-metal panel: a tall segmented FE gauge (so you
 * can read stored vs. capacity), a Range upgrade slot and a Capacity upgrade slot, drawn procedurally.
 */
public class ChargingPadScreen extends AbstractContainerScreen<ChargingPadMenu> {

    private static final int GAUGE_X = 153;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_W = 11;
    private static final int GAUGE_H = 50;

    public ChargingPadScreen(ChargingPadMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        GuiHelper.bevelPanel(g, x, y, this.imageWidth, this.imageHeight);
        GuiHelper.rivet(g, x + 3, y + 3);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + 3);
        GuiHelper.rivet(g, x + 3, y + this.imageHeight - 5);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + this.imageHeight - 5);

        // Recessed upgrade dock behind the two slots.
        GuiHelper.inset(g, x + 56, y + 29, 62, 26, GuiHelper.METAL_INNER);
        for (Slot slot : this.menu.slots) {
            GuiHelper.darkSlot(g, x + slot.x, y + slot.y);
        }

        // Energy bolt + segmented cyan FE gauge on the right.
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
        // Captions over the Range / Capacity slots.
        drawCaption(g, "Range", 62);
        drawCaption(g, "Cap", 98);
    }

    private void drawCaption(GuiGraphics g, String text, int slotX) {
        int cx = slotX + 8 - this.font.width(text) / 2;
        g.drawString(this.font, text, cx, 24, 0xFF9FB0BD, true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
