package com.duckedupmods.automechs.client;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.MechFolder;
import com.duckedupmods.automechs.menu.MechDatabaseMenu;
import com.duckedupmods.automechs.network.MechCommandPayload;
import com.duckedupmods.automechs.network.MechFolderPayload;
import com.duckedupmods.automechs.network.MechMovePayload;
import com.duckedupmods.automechs.network.MechSummary;
import com.duckedupmods.automechs.network.MechTextPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Node-canvas dashboard for the Mech Tablet. Registered robots are draggable node boxes on a pannable,
 * zoomable canvas; folders are resizable labelled regions you drag robots into. Left-drag empty space to
 * rubber-band select many robots at once (move/group them together); right- or middle-drag to pan; scroll
 * to zoom. The view (zoom/pan) persists between openings.
 */
public class MechDatabaseScreen extends AbstractContainerScreen<MechDatabaseMenu> {

    private static final int PANEL = 0xFF12151B;
    private static final int PANEL_LIGHT = 0xFF2E3340;
    private static final int PANEL_DARK = 0xFF06070A;
    private static final int CANVAS_BG = 0xFF0C0E13;
    private static final int GRID = 0x14FFFFFF;
    private static final int NODE_BG = 0xFF20252E;
    private static final int NODE_BG_OFF = 0xFF181B21;
    private static final int NODE_BORDER = 0xFF3A4250;
    private static final int NODE_SEL = 0xFF50D4E0;
    private static final int NODE_DETACH = 0xFFE07A3A;
    private static final int BAR_FILL = 0xFF50D4E0;
    private static final int BTN = 0xFF3A6B73;
    private static final int BTN_HOVER = 0xFF50D4E0;
    private static final int TITLE_TEXT = 0xFF50D4E0;
    private static final int SUBTEXT = 0xFF9098A0;

    private static final int NODE_W = 100;
    private static final int NODE_H = 54;
    private static final int ICON = 32;
    private static final int BTN_H = 16;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 3.0F;
    private static final int FOLDER_HANDLE = 8;

    private static float savedZoom = 1.0F;
    private static double savedPanX, savedPanY;
    private static boolean viewLoaded;

    private int canvasX0, canvasY0, canvasX1, canvasY1;
    private int barY;
    private int newGroupBtnX, newGroupBtnY;
    private static final int NEW_GROUP_W = 78;

    private double panX, panY;
    private float zoom = 1.0F;

    private final Set<UUID> selectedIds = new LinkedHashSet<>();
    private String selectedFolder;
    private String appliedSelection;

    private UUID draggingId;
    private boolean dragMoved;
    private boolean detaching;
    private boolean clickWasInSelection;
    private String draggingGroup;
    private boolean groupMoved;
    private String resizingFolder;
    private boolean panning;
    private boolean marquee;
    private double marqStartX, marqStartY, marqCurX, marqCurY;
    private double lastMouseX, lastMouseY;
    private double lastWorldX, lastWorldY;

    private final Map<UUID, int[]> localPos = new HashMap<>();
    private final Map<UUID, String> localGroup = new HashMap<>();
    private final Map<String, int[]> localFolderPos = new HashMap<>();
    private final Map<String, int[]> localFolderSize = new HashMap<>();
    private final Map<String, int[]> folderBounds = new HashMap<>();

    private EditBox nameBox;

    public MechDatabaseScreen(MechDatabaseMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 300;
        this.imageHeight = 204;
    }

    @Override
    protected void init() {
        super.init();
        this.canvasX0 = this.leftPos + 6;
        this.canvasY0 = this.topPos + 18;
        this.canvasX1 = this.leftPos + this.imageWidth - 6;
        this.canvasY1 = this.topPos + this.imageHeight - 28;
        this.barY = this.canvasY1 + 2;
        this.newGroupBtnX = this.leftPos + this.imageWidth - NEW_GROUP_W - 6;
        this.newGroupBtnY = this.topPos + 5;

        loadView();
        this.zoom = savedZoom;
        this.panX = savedPanX;
        this.panY = savedPanY;

        this.nameBox = new EditBox(this.font, this.leftPos + 44, this.barY + 4, 130, 14,
                Component.translatable("gui.automechs.mech_database.name"));
        this.nameBox.setMaxLength(48);
        addRenderableWidget(this.nameBox);
        this.appliedSelection = "<init>";
    }

    private List<MechSummary> mechs() {
        return this.menu.getMechs();
    }

    private List<MechFolder> folders() {
        return this.menu.getFolders();
    }

    private MechSummary singleNode() {
        if (this.selectedIds.size() != 1) {
            return null;
        }
        UUID id = this.selectedIds.iterator().next();
        return byId(id);
    }

    private String selectionKey() {
        if (this.selectedFolder != null) {
            return "f:" + this.selectedFolder;
        }
        if (this.selectedIds.size() == 1) {
            return "n:" + this.selectedIds.iterator().next();
        }
        if (this.selectedIds.size() > 1) {
            return "m:" + this.selectedIds.size();
        }
        return null;
    }

    private int worldX(MechSummary m) {
        int[] p = this.localPos.get(m.id());
        return p != null ? p[0] : m.canvasX();
    }

    private int worldY(MechSummary m) {
        int[] p = this.localPos.get(m.id());
        return p != null ? p[1] : m.canvasY();
    }

    private int folderAnchorX(MechFolder f) {
        int[] p = this.localFolderPos.get(f.name());
        return p != null ? p[0] : f.x();
    }

    private int folderAnchorY(MechFolder f) {
        int[] p = this.localFolderPos.get(f.name());
        return p != null ? p[1] : f.y();
    }

    private int folderW(MechFolder f) {
        int[] s = this.localFolderSize.get(f.name());
        return s != null ? s[0] : f.w();
    }

    private int folderH(MechFolder f) {
        int[] s = this.localFolderSize.get(f.name());
        return s != null ? s[1] : f.h();
    }

    /**
     * A node's group as the client should treat it RIGHT NOW. After a regroup/detach we stamp a local
     * override so the canvas reacts instantly, instead of flickering for the ~10 ticks until the server's
     * summary echoes the change back. {@link #reconcileGroups()} drops each override once the server agrees.
     */
    private String groupOf(MechSummary m) {
        String g = this.localGroup.get(m.id());
        return g != null ? g : m.group();
    }

    private void reconcileGroups() {
        if (this.localGroup.isEmpty()) {
            return;
        }
        for (MechSummary m : mechs()) {
            String g = this.localGroup.get(m.id());
            if (g != null && g.equals(m.group())) {
                this.localGroup.remove(m.id());
            }
        }
    }

    /**
     * A folder's drawn/hit bounds: the union of its manual rectangle (the player-set base size) and the
     * bounding box of every member node. The manual size is the floor, but the box always grows to contain
     * its robots — so moving a node live-resizes the group, while corner-dragging still sets the base size.
     */
    private int[] effectiveFolderBounds(MechFolder f) {
        int x0 = folderAnchorX(f);
        int y0 = folderAnchorY(f);
        int x1 = x0 + folderW(f);
        int y1 = y0 + folderH(f);
        final int pad = 6;
        final int header = 13;
        for (MechSummary m : mechs()) {
            if (groupOf(m).equals(f.name())) {
                // A robot being shift-dragged out is leaving — don't let the box stretch to follow it.
                if (this.detaching && m.id().equals(this.draggingId)) {
                    continue;
                }
                int nx = worldX(m);
                int ny = worldY(m);
                x0 = Math.min(x0, nx - pad);
                y0 = Math.min(y0, ny - pad - header);
                x1 = Math.max(x1, nx + NODE_W + pad);
                y1 = Math.max(y1, ny + NODE_H + pad);
            }
        }
        return new int[]{x0, y0, x1, y1};
    }

    private double screenToWorldX(double sx) {
        return (sx - this.canvasX0) / this.zoom + this.panX;
    }

    private double screenToWorldY(double sy) {
        return (sy - this.canvasY0) / this.zoom + this.panY;
    }

    // ---- rendering ----------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        g.fill(x, y, x + this.imageWidth, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 1, y + this.imageHeight, PANEL_LIGHT);
        g.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, PANEL_DARK);
        g.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, PANEL_DARK);

        g.fill(this.canvasX0, this.canvasY0, this.canvasX1, this.canvasY1, CANVAS_BG);

        syncDetailWidgets();
        reconcileGroups();

        List<MechSummary> list = mechs();

        g.enableScissor(this.canvasX0, this.canvasY0, this.canvasX1, this.canvasY1);
        g.pose().pushPose();
        g.pose().translate(this.canvasX0, this.canvasY0, 0);
        g.pose().scale(this.zoom, this.zoom, 1.0F);
        g.pose().translate(-this.panX, -this.panY, 0);

        drawGrid(g);
        drawFolderRegions(g);
        for (MechSummary m : list) {
            drawNode(g, m);
        }

        g.pose().popPose();
        g.disableScissor();

        if (list.isEmpty() && folders().isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.automechs.mech_database.empty"),
                    (this.canvasX0 + this.canvasX1) / 2, (this.canvasY0 + this.canvasY1) / 2 - 4, SUBTEXT);
        }

        String z = Math.round(this.zoom * 100) + "%";
        g.drawString(this.font, z, this.canvasX1 - this.font.width(z) - 3, this.canvasY0 + 2, SUBTEXT, false);

        drawButton(g, this.newGroupBtnX, this.newGroupBtnY, NEW_GROUP_W, 12,
                Component.translatable("gui.automechs.mech_database.new_group").getString(), mouseX, mouseY);

        drawEditorBar(g, mouseX, mouseY);
    }

    private void drawGrid(GuiGraphics g) {
        int step = 16;
        double wx0 = this.panX;
        double wy0 = this.panY;
        double wx1 = this.panX + (this.canvasX1 - this.canvasX0) / this.zoom;
        double wy1 = this.panY + (this.canvasY1 - this.canvasY0) / this.zoom;
        int startX = (int) (Math.floor(wx0 / step) * step);
        int startY = (int) (Math.floor(wy0 / step) * step);
        for (int gx = startX; gx <= wx1; gx += step) {
            g.fill(gx, (int) wy0, gx + 1, (int) Math.ceil(wy1), GRID);
        }
        for (int gy = startY; gy <= wy1; gy += step) {
            g.fill((int) wx0, gy, (int) Math.ceil(wx1), gy + 1, GRID);
        }
    }

    private void drawFolderRegions(GuiGraphics g) {
        this.folderBounds.clear();
        for (MechFolder f : folders()) {
            int[] b = effectiveFolderBounds(f);
            int x0 = b[0];
            int y0 = b[1];
            int x1 = b[2];
            int y1 = b[3];
            this.folderBounds.put(f.name(), new int[]{x0, y0, x1, y1});

            boolean active = f.name().equals(this.draggingGroup) || f.name().equals(this.selectedFolder)
                    || f.name().equals(this.resizingFolder);
            g.fill(x0, y0, x1, y1, active ? 0x3350D4E0 : 0x2250D4E0);
            outline(g, x0, y0, x1, y1, active ? 0xFF50D4E0 : 0x6650D4E0);
            g.fill(x0, y0, x1, y0 + 13, 0x4450D4E0);
            g.drawString(this.font, this.font.plainSubstrByWidth("= " + f.name(), x1 - x0 - 16), x0 + 4, y0 + 3, 0xFFB8F0F6, false);
            int[] del = deleteRect(this.folderBounds.get(f.name()));
            g.fill(del[0], del[1], del[2], del[3], 0x66E05050);
            g.drawString(this.font, "x", del[0] + 3, del[1] + 2, 0xFFFFDDDD, false);
            int[] hnd = resizeRect(this.folderBounds.get(f.name()));
            g.fill(hnd[0], hnd[1], hnd[2], hnd[3], 0x9950D4E0);
        }
    }

    private static int[] headerRect(int[] region) {
        return new int[]{region[0], region[1], region[2] - 14, region[1] + 13};
    }

    private static int[] deleteRect(int[] region) {
        return new int[]{region[2] - 13, region[1] + 1, region[2] - 1, region[1] + 12};
    }

    private static int[] resizeRect(int[] region) {
        return new int[]{region[2] - FOLDER_HANDLE, region[3] - FOLDER_HANDLE, region[2], region[3]};
    }

    private static net.minecraft.resources.ResourceLocation roleIcon(MechRole role) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.duckedupmods.automechs.Automechs.MODID, "textures/gui/role/" + role.id() + ".png");
    }

    private void drawNode(GuiGraphics g, MechSummary m) {
        int nx = worldX(m);
        int ny = worldY(m);
        boolean sel = this.selectedIds.contains(m.id());

        boolean leaving = this.detaching && m.id().equals(this.draggingId);
        g.fill(nx, ny, nx + NODE_W, ny + NODE_H, m.online() ? NODE_BG : NODE_BG_OFF);
        outline(g, nx, ny, nx + NODE_W, ny + NODE_H, leaving ? NODE_DETACH : (sel ? NODE_SEL : NODE_BORDER));

        g.fill(nx, ny, nx + NODE_W, ny + 12, 0xFF161A20);
        g.fill(nx + 3, ny + 4, nx + 7, ny + 8, statusColor(m));
        g.drawString(this.font, this.font.plainSubstrByWidth(displayName(m), NODE_W - 14), nx + 11, ny + 2,
                sel ? 0xFFFFFFFF : 0xFFE8ECF2, false);

        // Role badge art (PixelLab robot heads). Dimmed when the mech is offline.
        MechRole role = MechRole.byOrdinal(m.roleOrdinal());
        int iconX = nx + 4;
        int iconY = ny + 14;
        g.fill(iconX - 1, iconY - 1, iconX + ICON + 1, iconY + ICON + 1, 0xFF0C0E12);
        if (!m.online()) {
            g.setColor(0.42F, 0.42F, 0.45F, 1.0F);
        }
        g.blit(roleIcon(role), iconX, iconY, 0.0F, 0.0F, ICON, ICON, ICON, ICON);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int textX = nx + ICON + 10;
        g.drawString(this.font, Component.translatable(role.translationKey()), textX, ny + 17, 0xFFE8ECF2, false);

        if (!m.online()) {
            g.drawString(this.font, "offline", textX, ny + 31, 0xFF707782, false);
            return;
        }

        g.drawString(this.font, statusText(m), textX, ny + 31, statusColor(m), false);

        int barX = nx + 6;
        int bY = ny + NODE_H - 8;
        int barW = NODE_W - 12;
        g.fill(barX, bY, barX + barW, bY + 5, 0xFF0C0E12);
        int max = Math.max(1, m.maxEnergy());
        int filled = (int) (barW * Math.min(1.0F, m.energy() / (float) max));
        if (filled > 0) {
            g.fill(barX, bY, barX + filled, bY + 5, BAR_FILL);
        }
    }

    private void drawEditorBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(this.leftPos + 4, this.barY, this.leftPos + this.imageWidth - 4, this.topPos + this.imageHeight - 4, 0xFF161A20);
        MechSummary node = singleNode();
        boolean folderSel = this.selectedFolder != null;
        int count = this.selectedIds.size();

        if (!folderSel && count == 0) {
            g.drawString(this.font, Component.translatable("gui.automechs.mech_database.select_hint"),
                    this.leftPos + 8, this.barY + 8, SUBTEXT, false);
            return;
        }

        if (folderSel) {
            g.drawString(this.font, Component.translatable("gui.automechs.mech_database.folder"), this.leftPos + 8, this.barY + 8, SUBTEXT, false);
            drawButton(g, this.leftPos + 262, this.barY + 3, 28, BTN_H, "X", mouseX, mouseY);
            return;
        }

        if (count == 1 && node != null) {
            g.drawString(this.font, Component.translatable("gui.automechs.mech_database.name"), this.leftPos + 8, this.barY + 8, SUBTEXT, false);
            drawButton(g, this.leftPos + 178, this.barY + 3, 40, BTN_H,
                    node.enabled() ? Component.translatable("gui.automechs.mech_database.pause").getString()
                            : Component.translatable("gui.automechs.mech_database.resume").getString(), mouseX, mouseY);
        } else {
            g.drawString(this.font, Component.translatable("gui.automechs.mech_database.selected", count),
                    this.leftPos + 8, this.barY + 8, 0xFFB8C0C8, false);
            drawButton(g, this.leftPos + 178, this.barY + 3, 40, BTN_H,
                    Component.translatable("gui.automechs.mech_database.pause").getString(), mouseX, mouseY);
        }
        drawButton(g, this.leftPos + 220, this.barY + 3, 40, BTN_H,
                Component.translatable("gui.automechs.mech_database.recall").getString(), mouseX, mouseY);
        drawButton(g, this.leftPos + 262, this.barY + 3, 28, BTN_H, "X", mouseX, mouseY);
    }

    private void drawButton(GuiGraphics g, int bx, int by, int w, int h, String label, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + h;
        g.fill(bx, by, bx + w, by + h, hover ? BTN_HOVER : BTN);
        g.drawString(this.font, label, bx + (w - this.font.width(label)) / 2, by + (h - 8) / 2, hover ? 0xFF0C0E12 : 0xFFFFFFFF, false);
    }

    private void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    private void syncDetailWidgets() {
        MechSummary node = singleNode();
        boolean showName = node != null || this.selectedFolder != null;
        this.nameBox.visible = showName;
        this.nameBox.active = showName;
        String key = selectionKey();
        boolean changed = this.appliedSelection == null ? key != null : !this.appliedSelection.equals(key);
        if (changed) {
            this.appliedSelection = key;
            if (node != null) {
                this.nameBox.setValue(node.name());
            } else if (this.selectedFolder != null) {
                this.nameBox.setValue(this.selectedFolder);
            } else {
                this.nameBox.setValue("");
            }
        }
    }

    private String displayName(MechSummary m) {
        if (!m.name().isEmpty()) {
            return m.name();
        }
        return MechRole.byOrdinal(m.roleOrdinal()).id() + " #" + m.id().toString().substring(0, 4);
    }

    private int statusColor(MechSummary m) {
        if (!m.online()) {
            return 0xFF707782;
        }
        if (m.energy() <= 0) {
            return 0xFFE05050;
        }
        if (!m.enabled()) {
            return 0xFFE0C050;
        }
        return m.working() ? 0xFF60D080 : SUBTEXT;
    }

    private Component statusText(MechSummary m) {
        String key;
        if (m.energy() <= 0) {
            key = "status.automechs.no_power";
        } else if (!m.enabled()) {
            key = "status.automechs.paused";
        } else {
            key = m.working() ? "status.automechs.working" : "status.automechs.idle";
        }
        return Component.translatable(key);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.automechs.mech_database.title"), 8, 6, TITLE_TEXT, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (this.marquee) {
            int x0 = (int) Math.max(this.canvasX0, Math.min(this.marqStartX, this.marqCurX));
            int y0 = (int) Math.max(this.canvasY0, Math.min(this.marqStartY, this.marqCurY));
            int x1 = (int) Math.min(this.canvasX1, Math.max(this.marqStartX, this.marqCurX));
            int y1 = (int) Math.min(this.canvasY1, Math.max(this.marqStartY, this.marqCurY));
            g.fill(x0, y0, x1, y1, 0x2250D4E0);
            outline(g, x0, y0, x1, y1, 0xCC50D4E0);
        }
        this.renderTooltip(g, mouseX, mouseY);
    }

    // ---- input --------------------------------------------------------------

    private boolean inCanvas(double mx, double my) {
        return mx >= this.canvasX0 && mx < this.canvasX1 && my >= this.canvasY0 && my < this.canvasY1;
    }

    private MechSummary nodeAt(double mx, double my) {
        double wx = screenToWorldX(mx);
        double wy = screenToWorldY(my);
        List<MechSummary> list = mechs();
        for (int i = list.size() - 1; i >= 0; i--) {
            MechSummary m = list.get(i);
            int nx = worldX(m);
            int ny = worldY(m);
            if (wx >= nx && wx < nx + NODE_W && wy >= ny && wy < ny + NODE_H) {
                return m;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Right / middle button anywhere in the canvas pans.
        if ((button == 1 || button == 2) && inCanvas(mouseX, mouseY)) {
            this.panning = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }
        if (button == 0) {
            if (inRect(mouseX, mouseY, this.newGroupBtnX, this.newGroupBtnY, NEW_GROUP_W, 12)) {
                createGroup();
                return true;
            }
            // Editor-bar buttons.
            if (mouseY >= this.barY) {
                if (this.selectedFolder != null) {
                    if (inRect(mouseX, mouseY, this.leftPos + 262, this.barY + 3, 28, BTN_H)) {
                        send(new MechFolderPayload(MechFolderPayload.OP_DELETE, this.selectedFolder, "", 0, 0));
                        this.selectedFolder = null;
                        return true;
                    }
                } else if (!this.selectedIds.isEmpty()) {
                    if (inRect(mouseX, mouseY, this.leftPos + 178, this.barY + 3, 40, BTN_H)) {
                        for (UUID id : this.selectedIds) {
                            send(new MechCommandPayload(id, MechCommandPayload.ACTION_TOGGLE));
                        }
                        return true;
                    }
                    if (inRect(mouseX, mouseY, this.leftPos + 220, this.barY + 3, 40, BTN_H)) {
                        for (UUID id : this.selectedIds) {
                            send(new MechCommandPayload(id, MechCommandPayload.ACTION_RECALL));
                        }
                        return true;
                    }
                    if (inRect(mouseX, mouseY, this.leftPos + 262, this.barY + 3, 28, BTN_H)) {
                        for (UUID id : this.selectedIds) {
                            send(new MechCommandPayload(id, MechCommandPayload.ACTION_UNREGISTER));
                        }
                        this.selectedIds.clear();
                        return true;
                    }
                }
            }
            // Canvas.
            if (inCanvas(mouseX, mouseY)) {
                setFocused(null);
                MechSummary hit = nodeAt(mouseX, mouseY);
                if (hit != null) {
                    this.selectedFolder = null;
                    boolean shift = Screen.hasShiftDown();
                    // Shift-drag a grouped robot DETACHES it: drag it out like normal, but it ignores
                    // folders on release and its border turns orange so you know it's leaving the group.
                    this.detaching = shift && !hit.group().isEmpty();
                    if (this.detaching) {
                        this.selectedIds.clear();
                        this.selectedIds.add(hit.id());
                    } else if (shift) {
                        if (!this.selectedIds.remove(hit.id())) {
                            this.selectedIds.add(hit.id());
                        }
                    } else if (!this.selectedIds.contains(hit.id())) {
                        this.selectedIds.clear();
                        this.selectedIds.add(hit.id());
                    }
                    this.clickWasInSelection = this.selectedIds.contains(hit.id());
                    this.draggingId = (this.detaching || this.clickWasInSelection) ? hit.id() : null;
                    this.dragMoved = false;
                    this.lastWorldX = screenToWorldX(mouseX);
                    this.lastWorldY = screenToWorldY(mouseY);
                    playClick();
                    return true;
                }
                String del = folderRectAt(mouseX, mouseY, MechDatabaseScreen::deleteRect);
                if (del != null) {
                    send(new MechFolderPayload(MechFolderPayload.OP_DELETE, del, "", 0, 0));
                    if (del.equals(this.selectedFolder)) {
                        this.selectedFolder = null;
                    }
                    return true;
                }
                String rsz = folderRectAt(mouseX, mouseY, MechDatabaseScreen::resizeRect);
                if (rsz != null) {
                    this.resizingFolder = rsz;
                    this.selectedFolder = rsz;
                    this.selectedIds.clear();
                    this.lastWorldX = screenToWorldX(mouseX);
                    this.lastWorldY = screenToWorldY(mouseY);
                    playClick();
                    return true;
                }
                String folder = folderHeaderAt(mouseX, mouseY);
                if (folder != null) {
                    this.selectedFolder = folder;
                    this.selectedIds.clear();
                    this.draggingGroup = folder;
                    this.groupMoved = false;
                    this.lastWorldX = screenToWorldX(mouseX);
                    this.lastWorldY = screenToWorldY(mouseY);
                    playClick();
                    return true;
                }
                // Empty space → start a rubber-band selection.
                this.marquee = true;
                this.marqStartX = this.marqCurX = mouseX;
                this.marqStartY = this.marqCurY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (this.draggingId != null) {
            double wx = screenToWorldX(mouseX);
            double wy = screenToWorldY(mouseY);
            int ddx = (int) Math.round(wx - this.lastWorldX);
            int ddy = (int) Math.round(wy - this.lastWorldY);
            if (ddx != 0 || ddy != 0) {
                for (UUID id : this.selectedIds) {
                    MechSummary m = byId(id);
                    if (m != null) {
                        this.localPos.put(id, new int[]{worldX(m) + ddx, worldY(m) + ddy});
                    }
                }
                this.lastWorldX = wx;
                this.lastWorldY = wy;
                this.dragMoved = true;
            }
            return true;
        }
        if (this.resizingFolder != null) {
            for (MechFolder f : folders()) {
                if (f.name().equals(this.resizingFolder)) {
                    int nw = Math.max(70, (int) Math.round(screenToWorldX(mouseX)) - folderAnchorX(f));
                    int nh = Math.max(46, (int) Math.round(screenToWorldY(mouseY)) - folderAnchorY(f));
                    this.localFolderSize.put(this.resizingFolder, new int[]{nw, nh});
                    break;
                }
            }
            return true;
        }
        if (this.draggingGroup != null) {
            double wx = screenToWorldX(mouseX);
            double wy = screenToWorldY(mouseY);
            int ddx = (int) Math.round(wx - this.lastWorldX);
            int ddy = (int) Math.round(wy - this.lastWorldY);
            if (ddx != 0 || ddy != 0) {
                for (MechFolder f : folders()) {
                    if (f.name().equals(this.draggingGroup)) {
                        this.localFolderPos.put(f.name(), new int[]{folderAnchorX(f) + ddx, folderAnchorY(f) + ddy});
                    }
                }
                for (MechSummary m : mechs()) {
                    if (groupOf(m).equals(this.draggingGroup)) {
                        this.localPos.put(m.id(), new int[]{worldX(m) + ddx, worldY(m) + ddy});
                    }
                }
                this.lastWorldX = wx;
                this.lastWorldY = wy;
                this.groupMoved = true;
            }
            return true;
        }
        if (this.marquee) {
            this.marqCurX = mouseX;
            this.marqCurY = mouseY;
            return true;
        }
        if (this.panning) {
            this.panX -= (mouseX - this.lastMouseX) / this.zoom;
            this.panY -= (mouseY - this.lastMouseY) / this.zoom;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            persist();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingId != null) {
            UUID grabbed = this.draggingId;
            boolean wasDetaching = this.detaching;
            this.draggingId = null;
            this.detaching = false;
            if (wasDetaching) {
                // Detach drag: drop it wherever it landed and pull it out of its group, regardless of
                // any folder it's hovering over.
                int[] p = this.localPos.get(grabbed);
                if (p != null) {
                    send(new MechMovePayload(grabbed, p[0], p[1]));
                }
                MechSummary node = byId(grabbed);
                if (node != null && !groupOf(node).isEmpty()) {
                    this.localGroup.put(grabbed, "");
                    send(new MechTextPayload(grabbed, MechTextPayload.FIELD_GROUP, ""));
                }
                return true;
            }
            if (this.dragMoved) {
                // Each node's folder membership is decided by where IT actually landed — not by the
                // grabbed node. Dragging one node into a folder must not drag the rest of the selection
                // in with it; only nodes whose own center lands inside a folder region join that folder.
                for (UUID id : this.selectedIds) {
                    int[] p = this.localPos.get(id);
                    if (p != null) {
                        send(new MechMovePayload(id, p[0], p[1]));
                    }
                    MechSummary node = byId(id);
                    if (node != null) {
                        String target = folderRegionAt(worldX(node) + NODE_W / 2.0, worldY(node) + NODE_H / 2.0);
                        if (target != null && !target.equals(groupOf(node))) {
                            this.localGroup.put(id, target);
                            send(new MechTextPayload(id, MechTextPayload.FIELD_GROUP, target));
                        } else if (target == null && !groupOf(node).isEmpty()) {
                            this.localGroup.put(id, "");
                            send(new MechTextPayload(id, MechTextPayload.FIELD_GROUP, ""));
                        }
                    }
                }
            } else if (this.clickWasInSelection && this.selectedIds.size() > 1 && !Screen.hasShiftDown()) {
                // A plain click (no drag) on an already-selected node collapses to just that node.
                this.selectedIds.clear();
                this.selectedIds.add(grabbed);
            }
            return true;
        }
        if (this.resizingFolder != null) {
            String group = this.resizingFolder;
            this.resizingFolder = null;
            int[] s = this.localFolderSize.get(group);
            if (s != null) {
                send(new MechFolderPayload(MechFolderPayload.OP_RESIZE, group, "", s[0], s[1]));
            }
            return true;
        }
        if (this.draggingGroup != null) {
            String group = this.draggingGroup;
            this.draggingGroup = null;
            if (this.groupMoved) {
                int[] fp = this.localFolderPos.get(group);
                if (fp != null) {
                    send(new MechFolderPayload(MechFolderPayload.OP_MOVE, group, "", fp[0], fp[1]));
                }
                for (MechSummary m : mechs()) {
                    if (groupOf(m).equals(group)) {
                        int[] p = this.localPos.get(m.id());
                        if (p != null) {
                            send(new MechMovePayload(m.id(), p[0], p[1]));
                        }
                    }
                }
            }
            return true;
        }
        if (this.marquee) {
            this.marquee = false;
            double w = Math.abs(this.marqCurX - this.marqStartX);
            double h = Math.abs(this.marqCurY - this.marqStartY);
            if (w < 3 && h < 3) {
                // A click on empty space clears the selection.
                this.selectedIds.clear();
                this.selectedFolder = null;
            } else {
                if (!Screen.hasShiftDown()) {
                    this.selectedIds.clear();
                }
                this.selectedFolder = null;
                double wx0 = screenToWorldX(Math.min(this.marqStartX, this.marqCurX));
                double wy0 = screenToWorldY(Math.min(this.marqStartY, this.marqCurY));
                double wx1 = screenToWorldX(Math.max(this.marqStartX, this.marqCurX));
                double wy1 = screenToWorldY(Math.max(this.marqStartY, this.marqCurY));
                for (MechSummary m : mechs()) {
                    int nx = worldX(m);
                    int ny = worldY(m);
                    if (nx + NODE_W >= wx0 && nx <= wx1 && ny + NODE_H >= wy0 && ny <= wy1) {
                        this.selectedIds.add(m.id());
                    }
                }
            }
            return true;
        }
        if (this.panning) {
            this.panning = false;
            persist();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && inCanvas(mouseX, mouseY)) {
            double wx = screenToWorldX(mouseX);
            double wy = screenToWorldY(mouseY);
            float factor = scrollY > 0 ? 1.1F : 1.0F / 1.1F;
            this.zoom = Mth.clamp(this.zoom * factor, MIN_ZOOM, MAX_ZOOM);
            this.panX = wx - (mouseX - this.canvasX0) / this.zoom;
            this.panY = wy - (mouseY - this.canvasY0) / this.zoom;
            persist();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void createGroup() {
        String name = nextGroupName();
        int wx = (int) (this.panX + (this.canvasX1 - this.canvasX0) / 2.0 / this.zoom) - MechFolder.DEFAULT_W / 2;
        int wy = (int) (this.panY + (this.canvasY1 - this.canvasY0) / 2.0 / this.zoom) - MechFolder.DEFAULT_H / 2;
        send(new MechFolderPayload(MechFolderPayload.OP_CREATE, name, "", wx, wy));
        this.selectedFolder = name;
        this.selectedIds.clear();
    }

    private String nextGroupName() {
        Set<String> names = new java.util.HashSet<>();
        for (MechFolder f : folders()) {
            names.add(f.name());
        }
        int n = 1;
        while (names.contains("Group " + n)) {
            n++;
        }
        return "Group " + n;
    }

    private MechSummary byId(UUID id) {
        for (MechSummary m : mechs()) {
            if (m.id().equals(id)) {
                return m;
            }
        }
        return null;
    }

    private String folderHeaderAt(double mx, double my) {
        return folderRectAt(mx, my, MechDatabaseScreen::headerRect);
    }

    private String folderRectAt(double mx, double my, java.util.function.Function<int[], int[]> rectOf) {
        double wx = screenToWorldX(mx);
        double wy = screenToWorldY(my);
        for (Map.Entry<String, int[]> e : this.folderBounds.entrySet()) {
            int[] r = rectOf.apply(e.getValue());
            if (wx >= r[0] && wx < r[2] && wy >= r[1] && wy < r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    private String folderRegionAt(double worldCx, double worldCy) {
        for (Map.Entry<String, int[]> e : this.folderBounds.entrySet()) {
            int[] r = e.getValue();
            if (worldCx >= r[0] && worldCx < r[2] && worldCy >= r[1] && worldCy < r[3]) {
                return e.getKey();
            }
        }
        return null;
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = this.nameBox.isFocused();
        if (typing && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            commitName();
            setFocused(null);
            return true;
        }
        if (typing) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            if (getFocused() != null) {
                getFocused().keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void commitName() {
        String value = this.nameBox.getValue();
        MechSummary node = singleNode();
        if (node != null) {
            send(new MechTextPayload(node.id(), MechTextPayload.FIELD_NAME, value));
        } else if (this.selectedFolder != null && !value.trim().isEmpty() && !value.equals(this.selectedFolder)) {
            send(new MechFolderPayload(MechFolderPayload.OP_RENAME, this.selectedFolder, value.trim(), 0, 0));
            this.selectedFolder = value.trim();
            this.appliedSelection = "f:" + this.selectedFolder;
        }
    }

    @Override
    public void removed() {
        commitName();
        persist();
        saveView();
        super.removed();
    }

    private void persist() {
        savedZoom = this.zoom;
        savedPanX = this.panX;
        savedPanY = this.panY;
    }

    private static java.nio.file.Path viewFile() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("automechs-dashboard-view.properties");
    }

    /** Load the saved zoom/pan from disk once per game launch (statics only survive while the JVM lives). */
    private static void loadView() {
        if (viewLoaded) {
            return;
        }
        viewLoaded = true;
        try {
            java.nio.file.Path file = viewFile();
            if (java.nio.file.Files.exists(file)) {
                java.util.Properties p = new java.util.Properties();
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(file)) {
                    p.load(in);
                }
                savedZoom = Mth.clamp(Float.parseFloat(p.getProperty("zoom", "1.0")), MIN_ZOOM, MAX_ZOOM);
                savedPanX = Double.parseDouble(p.getProperty("panX", "0"));
                savedPanY = Double.parseDouble(p.getProperty("panY", "0"));
            }
        } catch (Exception ignored) {
            // Corrupt/missing file → keep defaults.
        }
    }

    /** Flush zoom/pan to disk so the view survives a game restart. Called on close, not every frame. */
    private static void saveView() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("zoom", Float.toString(savedZoom));
            p.setProperty("panX", Double.toString(savedPanX));
            p.setProperty("panY", Double.toString(savedPanY));
            java.nio.file.Path file = viewFile();
            java.nio.file.Files.createDirectories(file.getParent());
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(file)) {
                p.store(out, "Automechs dashboard view (zoom/pan)");
            }
        } catch (Exception ignored) {
            // Best-effort persistence; a write failure shouldn't break the UI.
        }
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
        playClick();
    }

    private void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }
}
