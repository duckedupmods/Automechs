package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.menu.MechMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Screen for the {@link MechMenu}, drawn procedurally in the mod's industrial dark-metal style. The
 * header shows the mech's role, a status LED and live energy; toggle buttons control Work/Follow; an
 * area button starts an in-world placement (Quarry box for mining, flat field for farming); and a Range
 * dropdown picks the working size for any mech with the Range upgrade.
 */
public class MechScreen extends AbstractContainerScreen<MechMenu> {

    private static final int TEXT = 0xFFC4D2DE;

    private static final int DROPDOWN_X = 88;
    private static final int DROPDOWN_Y = 84;
    private static final int DROPDOWN_W = 80;
    private static final int DROPDOWN_H = 14;
    private static final int OPTION_H = 13;

    // Combat Soul Tank: a vertical XP tank in the wide panel's right margin. Click it to drain to yourself.
    private static final int TANK_W = 9;
    private static final int TANK_TOP = 28; // from topPos
    private static final int TANK_H = 72;

    // Work-area outline eye toggle (mining/farming): sits just right of the narrowed action button.
    private static final int EYE_X = 66;
    private static final int EYE_Y = DROPDOWN_Y;
    private static final int EYE_W = 16;
    private static final int EYE_H = 14;

    private static final int MAT_PANEL_W = 164;
    private static final int MAT_PANEL_H = 174;
    private static final int MAT_ROW_H = 18;
    private static final int MAT_VISIBLE = 7;

    private Button workButton;
    private Button followButton;
    private Button attackModeButton;

    private boolean rangeOpen;
    private boolean materialsOpen;
    private int materialsScroll;

    public MechScreen(MechMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Combat mechs with a Soul Tank get a wider panel so a vertical XP tank fits in the right margin
        // (clearly separate from the FE readout, which previously sat right above the XP bar).
        MiningMech mech = menu.getMech();
        boolean tank = mech != null && mech.getRole() == MechRole.COMBAT && menu.getSoulCapacity() > 0;
        this.imageWidth = tank ? 198 : 176;
        this.imageHeight = 198;
        this.inventoryLabelY = 104;
    }

    @Override
    protected void init() {
        super.init();
        this.followButton = Button.builder(followLabel(), button ->
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1))
                .bounds(this.leftPos + 8, this.topPos + 27, 76, 14)
                .build();
        addRenderableWidget(this.followButton);

        this.workButton = Button.builder(workLabel(), button ->
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0))
                .bounds(this.leftPos + 92, this.topPos + 27, 76, 14)
                .build();
        addRenderableWidget(this.workButton);

        // Area placement button: Quarry (mining) / Set Area (farming). Closes the screen and starts the
        // in-world overlay at the currently-selected Range tier.
        MiningMech mech = this.menu.getMech();
        MechRole role = mech != null ? mech.getRole() : null;
        if (mech != null && (role == MechRole.MINING || role == MechRole.FARMING)) {
            String label = role == MechRole.MINING ? "Quarry" : "Set Area";
            // Narrowed to leave room for the area-outline eye toggle at its right (see EYE_X).
            addRenderableWidget(Button.builder(Component.literal(label), button -> {
                        if (role == MechRole.MINING) {
                            QuarryPlacer.beginQuarry(mech, this.menu.getSelectedTier());
                        } else {
                            QuarryPlacer.beginFarm(mech, this.menu.getSelectedTier());
                        }
                        this.onClose();
                    })
                    .bounds(this.leftPos + 8, this.topPos + DROPDOWN_Y, 56, 14)
                    .build());
        }

        // Builder mechs: pick a schematic, place its ghost, and view the bill of materials.
        if (mech != null && role == MechRole.BUILDING) {
            net.minecraft.client.gui.components.Tooltip status = buildTooltip();
            addRenderableWidget(Button.builder(Component.literal("Schem."), button ->
                            SchematicScreen.open(mech.getUUID()))
                    .bounds(this.leftPos + 8, this.topPos + DROPDOWN_Y, 48, 14)
                    .tooltip(status)
                    .build());
            Button place = Button.builder(Component.literal("Place"), button -> {
                        if (this.menu.hasBuildPlan()) {
                            QuarryPlacer.beginBuild(mech, this.menu.getBuildSizeX(), this.menu.getBuildSizeY(),
                                    this.menu.getBuildSizeZ(), this.menu.getBuildName());
                            this.onClose();
                        }
                    })
                    .bounds(this.leftPos + 58, this.topPos + DROPDOWN_Y, 42, 14)
                    .tooltip(status)
                    .build();
            place.active = this.menu.hasBuildPlan();
            addRenderableWidget(place);
            Button materials = Button.builder(Component.literal("Materials"), button ->
                            this.materialsOpen = !this.materialsOpen)
                    .bounds(this.leftPos + 102, this.topPos + DROPDOWN_Y, 66, 14)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("Show the blocks this build needs")))
                    .build();
            materials.active = this.menu.hasBuildPlan();
            addRenderableWidget(materials);
        }

        // Combat mechs: a target-mode toggle (hostiles only / all mobs, for mob farms).
        if (mech != null && role == MechRole.COMBAT) {
            this.attackModeButton = Button.builder(attackLabel(), button ->
                            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2))
                    .bounds(this.leftPos + 8, this.topPos + DROPDOWN_Y, 76, 14)
                    .build();
            addRenderableWidget(this.attackModeButton);
        }
    }

    /** Tooltip describing the loaded schematic (or a prompt to choose one) for the builder buttons. */
    private net.minecraft.client.gui.components.Tooltip buildTooltip() {
        if (this.menu.hasBuildPlan()) {
            String dims = this.menu.getBuildSizeX() + "×" + this.menu.getBuildSizeY() + "×" + this.menu.getBuildSizeZ();
            return net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                    this.menu.getBuildName() + "\n" + dims + " · " + this.menu.getBuildBlocks() + " blocks"
                            + "\nPlace, then press R to rotate."));
        }
        return net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("No schematic. Click to choose a .litematic / .nbt file."));
    }

    private Component attackLabel() {
        MiningMech mech = this.menu.getMech();
        boolean all = mech != null && mech.isAttackAll();
        return Component.literal(all ? "Attacks: All" : "Attacks: Foes");
    }

    private Component workLabel() {
        MiningMech mech = this.menu.getMech();
        boolean on = mech != null && mech.isWorkEnabled();
        return Component.literal(on ? "Work: ON" : "Work: OFF");
    }

    private Component followLabel() {
        MiningMech mech = this.menu.getMech();
        boolean on = mech != null && mech.isFollowEnabled();
        return Component.literal(on ? "Follow: ON" : "Follow: OFF");
    }

    // --- Range dropdown ------------------------------------------------------

    private boolean rangeVisible() {
        MiningMech mech = this.menu.getMech();
        if (mech == null || this.menu.getRangeMax() <= 0) {
            return false;
        }
        MechRole role = mech.getRole();
        return role == MechRole.FARMING || role == MechRole.MINING || role == MechRole.COMBAT;
    }

    private int ddX() {
        return this.leftPos + DROPDOWN_X;
    }

    private int ddY() {
        return this.topPos + DROPDOWN_Y;
    }

    /** Human label for a size tier, phrased for the mech's role. */
    private String tierLabel(int tier) {
        MiningMech mech = this.menu.getMech();
        MechRole role = mech != null ? mech.getRole() : null;
        if (role == MechRole.FARMING) {
            int side = com.duckedupmods.automechs.entity.ai.MechFarmGoal.farmSide(tier); // 2×2 … 8×8
            return side + "×" + side;
        }
        if (role == MechRole.COMBAT) {
            return com.duckedupmods.automechs.entity.ai.MechCombatGoal.guardRadius(tier) + " blocks";
        }
        return tier == 0 ? "Base" : ("+" + tier);
    }

    private void selectTier(int tier) {
        this.menu.setSelectedTier(tier);
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                MechMenu.RANGE_BUTTON_BASE + tier);
        playClick();
    }

    private void playClick() {
        this.minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // --- Work-area outline eye toggle (mining / farming) ----------------------

    /** The eye toggle is shown only for the roles that draw an in-world outline (mining quarry / farm field). */
    private boolean areaToggleVisible() {
        MiningMech mech = this.menu.getMech();
        if (mech == null) {
            return false;
        }
        MechRole role = mech.getRole();
        return role == MechRole.MINING || role == MechRole.FARMING;
    }

    private boolean areaShown() {
        MiningMech mech = this.menu.getMech();
        return mech != null && mech.isAreaVisible();
    }

    private int eyeX() {
        return this.leftPos + EYE_X;
    }

    private int eyeY() {
        return this.topPos + EYE_Y;
    }

    // --- Combat Soul Tank gauge (header) + drain ------------------------------

    /** The Soul Tank gauge/drain shows only for a combat mech that actually has the Soul Tank module. */
    private boolean combatTank() {
        MiningMech mech = this.menu.getMech();
        return mech != null && mech.getRole() == MechRole.COMBAT && this.menu.getSoulCapacity() > 0;
    }

    private int tankX() {
        return this.leftPos + this.imageWidth - 14;
    }

    private int tankY() {
        return this.topPos + TANK_TOP;
    }

    /** Whether the mouse is over the tank's (slightly inflated) clickable region. */
    private boolean overTank(double mx, double my) {
        return inRect(mx, my, tankX() - 2, tankY() - 2, TANK_W + 4, TANK_H + 4);
    }

    /** A vertical green experience tank (distinct from the cyan energy bar), brightening on hover. */
    private static void drawSoulTank(GuiGraphics g, int x, int y, int w, int h, int val, int max, boolean hover) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, GuiHelper.RIVET);
        GuiHelper.inset(g, x - 1, y - 1, w + 2, h + 2, 0xFF112108);
        int m = max <= 0 ? 1 : max;
        int fillH = (int) ((long) h * Math.max(0, Math.min(val, m)) / m);
        if (fillH > 0) {
            int top = y + h - fillH;
            g.fillGradient(x, top, x + w, y + h, 0xFF9CFF7A, 0xFF227019); // bright top, deep green base
            g.fill(x, top, x + 1, y + h, 0x66FFFFFF);                     // left highlight
            for (int sy = y + h - 3; sy > top; sy -= 3) {
                g.fill(x, sy, x + w, sy + 1, 0x44000000);                 // segment separators
            }
            g.fill(x, top, x + w, top + 1, 0xCCFFFFFF);                   // glow line at the charge top
        }
        if (hover) {
            g.fill(x, y, x + w, y + h, 0x22FFFFFF);
        }
    }

    /** A small eye icon button: open eye = outline shown, slashed eye = hidden. */
    private void drawEyeToggle(GuiGraphics g, int px, int py, boolean on, boolean hover) {
        GuiHelper.bevelPanel(g, px, py, EYE_W, EYE_H);
        if (hover) {
            g.fill(px + 1, py + 1, px + EYE_W - 1, py + EYE_H - 1, 0x33FFFFFF);
        }
        int cx = px + EYE_W / 2;
        int cy = py + EYE_H / 2;
        int sclera = on ? 0xFFE8F0F4 : GuiHelper.TEXT_DIM;
        // Almond eye shape.
        g.fill(cx - 5, cy, cx + 5, cy + 1, sclera);
        g.fill(cx - 4, cy - 1, cx + 4, cy, sclera);
        g.fill(cx - 4, cy + 1, cx + 4, cy + 2, sclera);
        g.fill(cx - 2, cy - 2, cx + 2, cy - 1, sclera);
        g.fill(cx - 2, cy + 2, cx + 2, cy + 3, sclera);
        if (on) {
            g.fill(cx - 1, cy - 1, cx + 1, cy + 2, GuiHelper.ACCENT); // pupil
        } else {
            // Slash across the eye to read as "hidden".
            for (int i = -5; i <= 5; i++) {
                g.fill(cx + i, cy - 3 + (i + 5) * 6 / 11, cx + i + 1, cy - 2 + (i + 5) * 6 / 11, 0xFFCB5B5B);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // While the materials panel is up it owns the screen: clicks inside do nothing, clicks outside close it.
        if (this.materialsOpen) {
            if (!inRect(mx, my, matPanelX(), matPanelY(), MAT_PANEL_W, MAT_PANEL_H)) {
                this.materialsOpen = false;
                playClick();
            }
            return true;
        }
        // Eye toggle: show/hide this mech's in-world work-area outline.
        if (areaToggleVisible() && button == 0 && inRect(mx, my, eyeX(), eyeY(), EYE_W, EYE_H)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 3);
            playClick();
            return true;
        }
        // Soul Tank: click the XP tank to drain the stored XP into yourself.
        if (combatTank() && button == 0 && overTank(mx, my)) {
            MiningMech mech = this.menu.getMech();
            if (mech != null && mech.getSoulXp() > 0) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 4);
                playClick();
            }
            return true;
        }
        if (rangeVisible() && button == 0) {
            int x = ddX();
            int y = ddY();
            if (this.rangeOpen) {
                for (int i = 0; i <= this.menu.getRangeMax(); i++) {
                    int oy = y + DROPDOWN_H + i * OPTION_H;
                    if (inRect(mx, my, x, oy, DROPDOWN_W, OPTION_H)) {
                        selectTier(i);
                        this.rangeOpen = false;
                        return true;
                    }
                }
                this.rangeOpen = false; // any other click closes the dropdown
                return true;
            }
            if (inRect(mx, my, x, y, DROPDOWN_W, DROPDOWN_H)) {
                this.rangeOpen = true;
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (this.materialsOpen) {
            int max = Math.max(0, this.menu.getMaterials().size() - MAT_VISIBLE);
            this.materialsScroll = Mth.clamp(this.materialsScroll - (int) Math.signum(dy), 0, max);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void renderRangeDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (!rangeVisible()) {
            return;
        }
        int x = ddX();
        int y = ddY();
        boolean hoverHeader = inRect(mouseX, mouseY, x, y, DROPDOWN_W, DROPDOWN_H);

        // Elevate well above item icons AND their stack-count text (which sit a few hundred deep) so the
        // open menu isn't pierced by the sprites/numbers in the slots beneath it.
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);

        GuiHelper.bevelPanel(g, x, y, DROPDOWN_W, DROPDOWN_H);
        g.fill(x + 1, y + 1, x + 3, y + DROPDOWN_H - 1, GuiHelper.ACCENT);
        g.drawString(this.font, tierLabel(this.menu.getSelectedTier()), x + 7, y + 3,
                hoverHeader ? 0xFFFFFFFF : GuiHelper.TEXT_LIGHT, false);
        drawArrow(g, x + DROPDOWN_W - 11, y + 5, this.rangeOpen);

        if (this.rangeOpen) {
            for (int i = 0; i <= this.menu.getRangeMax(); i++) {
                int oy = y + DROPDOWN_H + i * OPTION_H;
                boolean hov = inRect(mouseX, mouseY, x, oy, DROPDOWN_W, OPTION_H);
                boolean sel = i == this.menu.getSelectedTier();
                g.fill(x, oy, x + DROPDOWN_W, oy + OPTION_H, hov ? 0xFF2C333B : 0xFF12161B);
                g.fill(x, oy, x + DROPDOWN_W, oy + 1, GuiHelper.METAL_LO);
                g.fill(x, oy, x + 1, oy + OPTION_H, GuiHelper.METAL_LO);
                g.fill(x + DROPDOWN_W - 1, oy, x + DROPDOWN_W, oy + OPTION_H, GuiHelper.METAL_LO);
                if (sel) {
                    g.fill(x + 1, oy + 1, x + 3, oy + OPTION_H - 1, GuiHelper.ACCENT);
                }
                g.drawString(this.font, tierLabel(i), x + 7, oy + 3,
                        (hov || sel) ? 0xFFFFFFFF : GuiHelper.TEXT_LIGHT, false);
            }
        }
        g.pose().popPose();
    }

    /** A small 5px triangle: pointing down when collapsed, up when open. */
    private static void drawArrow(GuiGraphics g, int cx, int top, boolean up) {
        int c = GuiHelper.TEXT_DIM;
        if (up) {
            g.fill(cx, top, cx + 1, top + 1, c);
            g.fill(cx - 1, top + 1, cx + 2, top + 2, c);
            g.fill(cx - 2, top + 2, cx + 3, top + 3, c);
        } else {
            g.fill(cx - 2, top, cx + 3, top + 1, c);
            g.fill(cx - 1, top + 1, cx + 2, top + 2, c);
            g.fill(cx, top + 2, cx + 1, top + 3, c);
        }
    }

    // --- Materials panel -----------------------------------------------------

    private int matPanelX() {
        return this.leftPos + 6;
    }

    private int matPanelY() {
        return this.topPos + 18;
    }

    /** Overlay listing the build's bill of materials (icon + name + count), scrollable, drawn above slots. */
    private void renderMaterialsPanel(GuiGraphics g) {
        if (!this.materialsOpen) {
            return;
        }
        java.util.List<MechMenu.Material> mats = this.menu.getMaterials();
        int px = matPanelX();
        int py = matPanelY();

        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // above slots + their item sprites

        GuiHelper.bevelPanel(g, px, py, MAT_PANEL_W, MAT_PANEL_H);
        g.drawString(this.font, Component.literal("Materials Needed"), px + 6, py + 5, GuiHelper.TEXT_LIGHT, false);
        // Page indicator, right-aligned in the title row (only when there's more than one page).
        if (mats.size() > MAT_VISIBLE) {
            String pos = (this.materialsScroll + 1) + "-"
                    + Math.min(mats.size(), this.materialsScroll + MAT_VISIBLE) + "/" + mats.size();
            g.drawString(this.font, pos, px + MAT_PANEL_W - 6 - this.font.width(pos), py + 5, GuiHelper.TEXT_DIM, false);
        }
        g.fill(px + 6, py + 15, px + MAT_PANEL_W - 6, py + 16, GuiHelper.ACCENT);

        if (mats.isEmpty()) {
            g.drawString(this.font, Component.literal("No schematic loaded"), px + 8, py + 24, GuiHelper.TEXT_DIM, false);
        } else {
            int listTop = py + 21;
            for (int i = 0; i < MAT_VISIBLE && i + this.materialsScroll < mats.size(); i++) {
                MechMenu.Material mat = mats.get(i + this.materialsScroll);
                int ry = listTop + i * MAT_ROW_H;
                g.renderItem(mat.icon(), px + 8, ry);
                g.drawString(this.font, mat.icon().getHoverName(), px + 28, ry + 4, GuiHelper.TEXT_LIGHT, false);
                String count = "x " + mat.count();
                g.drawString(this.font, count, px + MAT_PANEL_W - 8 - this.font.width(count), ry + 4,
                        GuiHelper.ACCENT, false);
            }
        }
        g.drawString(this.font, Component.literal("click outside to close"),
                px + 6, py + MAT_PANEL_H - 11, GuiHelper.TEXT_DIM, false);
        g.pose().popPose();
    }

    // --- Role identity (colour + emblem) -------------------------------------

    /** Each role's signature colour: used for the header underline, identity stripe, glyph and LED ring. */
    private static int roleColor(MechRole role) {
        return switch (role) {
            case MINING -> 0xFFE8A33C;   // amber
            case FARMING -> 0xFF5BD46A;  // green
            case BUILDING -> 0xFF4D9BE8; // blue
            case COMBAT -> 0xFFE85050;   // red
        };
    }

    private static final int GLYPH_DARK = 0xFF1A1E24; // mortar / shading for the emblems

    /** Draw the role's emblem in a 9×9 box at (gx, gy): pickaxe / sprout / crate / bricks / sword. */
    private static void drawRoleGlyph(GuiGraphics g, MechRole role, int gx, int gy, int c) {
        switch (role) {
            case MINING -> { // pickaxe: curved head + handle
                px(g, gx, gy + 2, c); run(g, gx + 1, gy + 1, 2, c); run(g, gx + 3, gy + 2, 3, c);
                run(g, gx + 6, gy + 1, 2, c); px(g, gx + 8, gy + 2, c);
                col(g, gx + 4, gy + 3, 6, c);
            }
            case FARMING -> { // sprout: stem with two leaves and a bud
                col(g, gx + 4, gy + 4, 5, c);
                run(g, gx + 1, gy + 4, 3, c); run(g, gx + 2, gy + 3, 2, c);
                run(g, gx + 5, gy + 4, 3, c); run(g, gx + 5, gy + 3, 2, c);
                col(g, gx + 4, gy + 2, 2, c);
            }
            case BUILDING -> { // brick wall: filled block carved with mortar lines
                for (int dy = 2; dy <= 7; dy++) {
                    run(g, gx + 1, gy + dy, 7, c);
                }
                run(g, gx + 1, gy + 4, 7, GLYPH_DARK);      // horizontal mortar
                col(g, gx + 4, gy + 2, 2, GLYPH_DARK);      // top course joint
                col(g, gx + 2, gy + 5, 3, GLYPH_DARK);      // bottom course joints (offset)
                col(g, gx + 6, gy + 5, 3, GLYPH_DARK);
            }
            case COMBAT -> { // sword: blade, crossguard, grip
                col(g, gx + 4, gy, 5, c);
                run(g, gx + 2, gy + 5, 5, c);
                col(g, gx + 4, gy + 6, 3, c);
            }
        }
    }

    private static void px(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    /** A horizontal run of {@code len} pixels starting at (x, y). */
    private static void run(GuiGraphics g, int x, int y, int len, int c) {
        g.fill(x, y, x + len, y + 1, c);
    }

    /** A vertical run of {@code len} pixels starting at (x, y). */
    private static void col(GuiGraphics g, int x, int y, int len, int c) {
        g.fill(x, y, x + 1, y + len, c);
    }

    // --- Background / labels -------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Industrial hull with beveled edge + corner rivets.
        GuiHelper.bevelPanel(g, x, y, this.imageWidth, this.imageHeight);
        GuiHelper.rivet(g, x + 3, y + 3);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + 3);
        GuiHelper.rivet(g, x + 3, y + this.imageHeight - 5);
        GuiHelper.rivet(g, x + this.imageWidth - 5, y + this.imageHeight - 5);

        // Header strip with a role-colored underline + a left identity stripe (the mech's signature
        // colour), so each job reads at a glance. Energy stays cyan; the role colour is identity only.
        MiningMech headerMech = this.menu.getMech();
        int rc = headerMech != null ? roleColor(headerMech.getRole()) : GuiHelper.ACCENT;
        GuiHelper.inset(g, x + 6, y + 5, this.imageWidth - 12, 18, GuiHelper.METAL_INNER);
        g.fill(x + 6, y + 6, x + 8, y + 41, rc);
        g.fill(x + 6, y + 42, x + this.imageWidth - 6, y + 43, rc);

        // Mech cargo: steel slot wells; player inventory: lighter vanilla wells.
        for (Slot slot : this.menu.slots) {
            if (slot.index < MechMenu.MECH_SLOTS) {
                GuiHelper.darkSlot(g, x + slot.x, y + slot.y);
            } else {
                GuiHelper.slot(g, x + slot.x, y + slot.y);
            }
        }

        // Header: full-width energy gauge (the FE readout sits above it). The combat XP tank is a separate
        // vertical gauge in the right margin, so the FE number is no longer mistaken for the XP value.
        MiningMech mech = this.menu.getMech();
        int energy = mech != null ? mech.getDisplayEnergy() : 0;
        // The energy bar stops short of the tank on the combat layout so the two don't overlap.
        int barW = combatTank() ? this.imageWidth - 30 : this.imageWidth - 20;
        GuiHelper.progressBar(g, x + 10, y + 17, barW, 4, energy, this.menu.getMaxEnergy());

        // Combat Soul Tank: vertical XP tank in the right margin, with a small "XP" cap label above it.
        if (combatTank() && mech != null) {
            drawSoulTank(g, tankX(), tankY(), TANK_W, TANK_H, mech.getSoulXp(), this.menu.getSoulCapacity(),
                    overTank(mouseX, mouseY));
            String cap = "XP";
            g.drawString(this.font, cap, tankX() + TANK_W / 2 - this.font.width(cap) / 2,
                    tankY() - 10, 0xFF8CF06A, false);
        }

        // Work-area outline eye toggle (mining / farming only).
        if (areaToggleVisible()) {
            drawEyeToggle(g, x + EYE_X, y + EYE_Y, areaShown(),
                    inRect(mouseX, mouseY, x + EYE_X, y + EYE_Y, EYE_W, EYE_H));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                GuiHelper.TEXT_DIM, false);

        MiningMech mech = this.menu.getMech();
        if (mech == null) {
            return;
        }

        // Nameplate: a role emblem + the role name + a status LED (green = working, dark = idle) inside a
        // role-colored ring. The emblem + colour make the job obvious without reading the text.
        MechRole role = mech.getRole();
        int rc = roleColor(role);
        drawRoleGlyph(g, role, 10, 7, rc);
        Component name = Component.translatable(role.translationKey());
        int nameX = 22;
        g.drawString(this.font, name, nameX, 9, TEXT, false);
        int ledX = nameX + this.font.width(name) + 6;
        g.fill(ledX - 1, 8, ledX + 5, 14, rc); // role-colored ring around the work LED
        int led = mech.isWorkEnabled() ? 0xFF49E07A : 0xFF20262E;
        g.fill(ledX, 9, ledX + 4, 13, led);
        g.fill(ledX, 9, ledX + 1, 10, 0x88FFFFFF);

        // Energy readout, right-aligned in the header.
        Component fe = Component.literal(String.format("%,d FE", mech.getDisplayEnergy()));
        g.drawString(this.font, fe, this.imageWidth - 12 - this.font.width(fe), 9, GuiHelper.ACCENT, false);

    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // super.render already draws the hovered-slot tooltip; the dropdown is drawn afterwards at a high z
        // so it covers that tooltip when open (no second renderTooltip call — that would draw over it).
        super.render(g, mouseX, mouseY, partialTick);
        renderRangeDropdown(g, mouseX, mouseY);
        renderMaterialsPanel(g);
        if (areaToggleVisible() && !this.materialsOpen && inRect(mouseX, mouseY, eyeX(), eyeY(), EYE_W, EYE_H)) {
            // Lift above the range dropdown header (drawn at z 600) so the tooltip isn't painted over by it.
            g.pose().pushPose();
            g.pose().translate(0, 0, 700);
            g.renderTooltip(this.font, Component.literal(areaShown() ? "Hide work area" : "Show work area"),
                    mouseX, mouseY);
            g.pose().popPose();
        }
        if (combatTank() && !this.materialsOpen && overTank(mouseX, mouseY)) {
            MiningMech mech = this.menu.getMech();
            int xp = mech != null ? mech.getSoulXp() : 0;
            java.util.List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.literal("Soul Tank: " + xp + " / " + this.menu.getSoulCapacity() + " XP"));
            lines.add(Component.literal(xp > 0 ? "§7Click to drain to yourself" : "§8Empty"));
            g.pose().pushPose();
            g.pose().translate(0, 0, 700);
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
            g.pose().popPose();
        }
        this.workButton.setMessage(workLabel());
        this.followButton.setMessage(followLabel());
        if (this.attackModeButton != null) {
            this.attackModeButton.setMessage(attackLabel());
        }
    }
}
