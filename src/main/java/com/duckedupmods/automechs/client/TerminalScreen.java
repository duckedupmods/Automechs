package com.duckedupmods.automechs.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.duckedupmods.automechs.menu.TerminalMenu;
import com.duckedupmods.automechs.network.NetworkItem;
import com.duckedupmods.automechs.network.StorageRequestPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Storage Terminal screen — an AE2-style window onto the whole Data Rack network. A searchable,
 * scrollable grid shows every item aggregated across the connected racks (not the fragmented per-sector
 * mess); left-click withdraws a stack, right-click a single item, and shift-clicking from the player
 * inventory inserts into the network. When the Main Drive has no power the grid greys out and shows an
 * OFFLINE banner — the stored items are safe, just not reachable until power returns.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 18;
    private static final int GRID_X = 7;
    private static final int GRID_Y = 32;

    private EditBox search;
    private int scrollRow;
    private List<NetworkItem> filtered = List.of();

    // AE2-style left toolbar state.
    private int sortMode = 0;               // 0 = amount, 1 = name (A-Z), 2 = mod/group
    private boolean sortDescending = true;  // high→low / Z→A  vs  low→high / A→Z
    private static final int BTN = 18;      // toolbar button size
    private static final String[] SORT_GLYPH = { "#", "Az", "M" };
    private static final String[] SORT_NAME = { "amount", "name", "mod" };

    public TerminalScreen(TerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 184;
        this.imageHeight = 228;
        this.inventoryLabelY = TerminalMenu.PLAYER_Y - 11;
    }

    // Clean AE2-style palette.
    private static final int BORDER   = 0xFF05080B;
    private static final int PANEL    = 0xFF1A222B;
    private static final int PANEL_HI = 0xFF2A343F;
    private static final int PANEL_LO = 0xFF0E141A;
    private static final int WELL     = 0xFF0A0F13;
    private static final int SLOT_BG  = 0xFF11181E;
    private static final int SLOT_HI  = 0xFF323D48;
    private static final int SLOT_LO  = 0xFF05080B;

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 7;
        this.titleLabelY = 6;
        // Search box on the header's second row, right of the status text (no overlap).
        this.search = new EditBox(this.font, this.leftPos + 92, this.topPos + 16, this.imageWidth - 99, 12,
                Component.translatable("gui.automechs.terminal.search"));
        this.search.setHint(Component.translatable("gui.automechs.terminal.search"));
        this.search.setMaxLength(48);
        this.search.setBordered(false);
        this.search.setTextColor(GuiHelper.TEXT_LIGHT);
        addRenderableWidget(this.search);
    }

    // ---- data ----------------------------------------------------------------

    private void recomputeFiltered() {
        String q = this.search != null ? this.search.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<NetworkItem> out = new ArrayList<>();
        for (NetworkItem ni : this.menu.getClientItems()) {
            if (q.isEmpty() || ni.icon().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(ni);
            }
        }
        // AE2-style sort: amount / name / mod-group, ascending or descending (left toolbar buttons).
        java.util.Comparator<NetworkItem> cmp = switch (this.sortMode) {
            case 1 -> java.util.Comparator.comparing(
                    ni -> ni.icon().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
            case 2 -> java.util.Comparator
                    .comparing((NetworkItem ni) -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(ni.icon().getItem()).getNamespace())
                    .thenComparing(ni -> ni.icon().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER);
            default -> java.util.Comparator.comparingInt(NetworkItem::count);
        };
        if (this.sortDescending) {
            cmp = cmp.reversed();
        }
        out.sort(cmp);
        this.filtered = out;

        int maxRow = Math.max(0, (this.filtered.size() + COLS - 1) / COLS - ROWS);
        this.scrollRow = Math.max(0, Math.min(this.scrollRow, maxRow));
    }

    // ---- rendering -----------------------------------------------------------

    /** A clean recessed slot well (AE2-style): dark interior, soft top/left shadow, subtle bottom/right edge. */
    private void slotWell(GuiGraphics g, int sx, int sy) {
        g.fill(sx, sy, sx + 18, sy + 18, SLOT_BG);
        g.fill(sx, sy, sx + 18, sy + 1, SLOT_LO);
        g.fill(sx, sy, sx + 1, sy + 18, SLOT_LO);
        g.fill(sx, sy + 17, sx + 18, sy + 18, SLOT_HI);
        g.fill(sx + 17, sy, sx + 18, sy + 18, SLOT_HI);
    }

    /** A raised beveled frame around (x,y,w,h): bright top/left, dark bottom/right — a window chrome look. */
    private void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 2, PANEL_HI);
        g.fill(x, y, x + 2, y + h, PANEL_HI);
        g.fill(x + w - 2, y, x + w, y + h, PANEL_LO);
        g.fill(x, y + h - 2, x + w, y + h, PANEL_LO);
    }

    /** A recessed box (dark interior + sunken edges) to hold a slot section. */
    private void recess(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL);
        g.fill(x, y, x + w, y + 1, SLOT_LO);
        g.fill(x, y, x + 1, y + h, SLOT_LO);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_HI);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_HI);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        recomputeFiltered();
        int x = this.leftPos;
        int y = this.topPos;
        int w = this.imageWidth;
        int h = this.imageHeight;
        boolean online = this.menu.isClientOnline();

        // --- window: dark outer border, slate body, beveled chrome, cyan title rule ---
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, BORDER);
        g.fill(x, y, x + w, y + h, PANEL);
        frame(g, x, y, w, h);
        g.fill(x + 4, y + 14, x + w - 4, y + 15, GuiHelper.ACCENT); // accent rule below the title

        // --- header row 2: status (left) + recessed search field (right) ---
        String status = (online ? "ONLINE  " : "OFFLINE  ") + this.menu.getClientItemCount();
        g.drawString(this.font, status, x + 8, y + 18, online ? 0xFF4BE3A8 : 0xFFE0603A, false);
        recess(g, x + 90, y + 14, w - 96, 15); // search well (the EditBox renders inside)

        // --- network grid section (framed recess holding the slots + scrollbar) ---
        int gx = x + GRID_X;
        int gy = y + GRID_Y;
        int gw = COLS * CELL;
        int gh = ROWS * CELL;
        recess(g, gx - 3, gy - 3, gw + 12, gh + 6); // includes the scrollbar gutter on the right

        int start = this.scrollRow * COLS;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int sx = gx + col * CELL;
                int sy = gy + row * CELL;
                slotWell(g, sx, sy);
                int idx = start + row * COLS + col;
                if (idx < this.filtered.size()) {
                    NetworkItem ni = this.filtered.get(idx);
                    g.renderItem(ni.icon(), sx + 1, sy + 1);
                    drawCount(g, sx + 1, sy + 1, ni.count());
                    if (!online) {
                        g.fill(sx, sy, sx + CELL, sy + CELL, 0x99151B20);
                    }
                }
            }
        }
        if (online && this.filtered.isEmpty()) {
            // Word-wrap inside the grid so the message never spills over the frame.
            int cx = gx + gw / 2;
            var lines = this.font.split(Component.translatable("gui.automechs.terminal.empty"), gw - 12);
            int ty = gy + gh / 2 - (lines.size() * this.font.lineHeight) / 2;
            for (var line : lines) {
                g.drawString(this.font, line, cx - this.font.width(line) / 2, ty, GuiHelper.TEXT_DIM, false);
                ty += this.font.lineHeight;
            }
        }
        drawScrollbar(g, gx + gw + 3, gy);

        // --- player inventory section (its own framed recess, below the Inventory label) ---
        int px = x + TerminalMenu.PLAYER_X;
        int py = y + TerminalMenu.PLAYER_Y;
        recess(g, px - 3, py - 3, 9 * 18 + 6, 3 * 18 + 6);
        recess(g, px - 3, py + 58 - 3, 9 * 18 + 6, 18 + 6);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotWell(g, px + col * 18 - 1, py + row * 18 - 1);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotWell(g, px + col * 18 - 1, py + 58 - 1);
        }
    }

    private void drawScrollbar(GuiGraphics g, int sx, int sy) {
        int h = ROWS * CELL;
        g.fill(sx, sy, sx + 4, sy + h, WELL);
        int totalRows = (this.filtered.size() + COLS - 1) / COLS;
        if (totalRows <= ROWS) {
            return;
        }
        int maxRow = totalRows - ROWS;
        int thumbH = Math.max(14, h * ROWS / totalRows);
        int track = h - thumbH;
        int ty = sy + (maxRow == 0 ? 0 : track * this.scrollRow / maxRow);
        g.fill(sx, ty, sx + 4, ty + thumbH, GuiHelper.ACCENT);
    }

    /** Draws a compact item count (e.g. 1.2k) bottom-right of a cell, slightly shrunk so big numbers fit. */
    private void drawCount(GuiGraphics g, int sx, int sy, int count) {
        if (count <= 1) {
            return;
        }
        String label = formatCount(count);
        float scale = 0.75F;
        g.pose().pushPose();
        g.pose().translate(sx + 17, sy + 17, 200);
        g.pose().scale(scale, scale, 1.0F);
        int w = this.font.width(label);
        g.drawString(this.font, label, -w, -this.font.lineHeight + 2, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    private static String formatCount(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        if (count < 100_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        }
        if (count < 1_000_000) {
            return (count / 1000) + "k";
        }
        return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawToolbar(g, mouseX, mouseY);
        // Tooltip for the hovered network cell (real per-item count in the tooltip line).
        NetworkItem hovered = cellAt(mouseX, mouseY);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(hovered.icon()));
            lines.add(Component.translatable("gui.automechs.terminal.count", hovered.count())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        } else if (toolbarTooltip(mouseX, mouseY) != null) {
            g.renderTooltip(this.font, toolbarTooltip(mouseX, mouseY), mouseX, mouseY);
        } else {
            this.renderTooltip(g, mouseX, mouseY);
        }
    }

    // ---- left toolbar (AE2-style sort buttons) -------------------------------

    private int btnX() { return this.leftPos - BTN - 3; }
    private int btnY(int i) { return this.topPos + 18 + i * (BTN + 3); }

    private boolean overBtn(double mx, double my, int i) {
        int bx = btnX(), by = btnY(i);
        return mx >= bx && mx < bx + BTN && my >= by && my < by + BTN;
    }

    private Component toolbarTooltip(int mx, int my) {
        if (overBtn(mx, my, 0)) {
            return Component.literal("Sort: " + SORT_NAME[this.sortMode]);
        }
        if (overBtn(mx, my, 1)) {
            return Component.literal(this.sortDescending ? "Order: descending" : "Order: ascending");
        }
        return null;
    }

    private void drawToolbar(GuiGraphics g, int mouseX, int mouseY) {
        // button 0: sort key (amount '#' / name 'Az')
        drawButton(g, 0, mouseX, mouseY);
        int b0x = btnX(), b0y = btnY(0);
        String key = SORT_GLYPH[this.sortMode];
        g.drawString(this.font, key, b0x + (BTN - this.font.width(key)) / 2 + 1, b0y + 5, GuiHelper.ACCENT, false);
        // button 1: sort direction (triangle)
        drawButton(g, 1, mouseX, mouseY);
        int cx = btnX() + BTN / 2, cy = btnY(1) + BTN / 2;
        if (this.sortDescending) {
            g.fill(cx - 3, cy - 2, cx + 3, cy - 1, GuiHelper.ACCENT);
            g.fill(cx - 2, cy - 1, cx + 2, cy, GuiHelper.ACCENT);
            g.fill(cx - 1, cy, cx + 1, cy + 1, GuiHelper.ACCENT);
        } else {
            g.fill(cx - 1, cy - 2, cx + 1, cy - 1, GuiHelper.ACCENT);
            g.fill(cx - 2, cy - 1, cx + 2, cy, GuiHelper.ACCENT);
            g.fill(cx - 3, cy, cx + 3, cy + 1, GuiHelper.ACCENT);
        }
    }

    private void drawButton(GuiGraphics g, int i, int mouseX, int mouseY) {
        int bx = btnX(), by = btnY(i);
        boolean hov = overBtn(mouseX, mouseY, i);
        g.fill(bx - 1, by - 1, bx + BTN + 1, by + BTN + 1, BORDER);
        g.fill(bx, by, bx + BTN, by + BTN, hov ? PANEL_HI : PANEL);
        g.fill(bx, by, bx + BTN, by + 1, SLOT_HI);
        g.fill(bx, by, bx + 1, by + BTN, SLOT_HI);
        g.fill(bx + BTN - 1, by, bx + BTN, by + BTN, PANEL_LO);
        g.fill(bx, by + BTN - 1, bx + BTN, by + BTN, PANEL_LO);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, GuiHelper.ACCENT, false);
        g.drawString(this.font, this.playerInventoryTitle, TerminalMenu.PLAYER_X, this.inventoryLabelY,
                GuiHelper.TEXT_DIM, false);
    }

    // ---- input ---------------------------------------------------------------

    /** The network item under the cursor, or null. */
    private NetworkItem cellAt(double mouseX, double mouseY) {
        int gx = this.leftPos + GRID_X;
        int gy = this.topPos + GRID_Y;
        if (mouseX < gx || mouseX >= gx + COLS * CELL || mouseY < gy || mouseY >= gy + ROWS * CELL) {
            return null;
        }
        int col = (int) ((mouseX - gx) / CELL);
        int row = (int) ((mouseY - gy) / CELL);
        int idx = (this.scrollRow + row) * COLS + col;
        return idx >= 0 && idx < this.filtered.size() ? this.filtered.get(idx) : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Left toolbar sort buttons (AE2-style).
        if (overBtn(mouseX, mouseY, 0)) {
            this.sortMode = (this.sortMode + 1) % 3;
            this.scrollRow = 0;
            playClick();
            return true;
        }
        if (overBtn(mouseX, mouseY, 1)) {
            this.sortDescending = !this.sortDescending;
            this.scrollRow = 0;
            playClick();
            return true;
        }
        NetworkItem hit = cellAt(mouseX, mouseY);
        if (hit != null) {
            if (!this.menu.isClientOnline()) {
                return true;
            }
            int amount = button == 1 ? 1 : hit.icon().getMaxStackSize();
            PacketDistributor.sendToServer(new StorageRequestPayload(hit.icon().copyWithCount(1), amount));
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int gx = this.leftPos + GRID_X;
        int gy = this.topPos + GRID_Y;
        boolean overGrid = mouseX >= gx && mouseX < gx + COLS * CELL + 6 && mouseY >= gy && mouseY < gy + ROWS * CELL;
        if (overGrid && scrollY != 0) {
            int totalRows = (this.filtered.size() + COLS - 1) / COLS;
            int maxRow = Math.max(0, totalRows - ROWS);
            this.scrollRow = Math.max(0, Math.min(maxRow, this.scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.search.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                this.search.setFocused(false);
                return true;
            }
            this.scrollRow = 0;
            return this.search.keyPressed(keyCode, scanCode, modifiers) || this.search.canConsumeInput()
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }
}
