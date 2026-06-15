package com.duckedupmods.automechs.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.registry.ModEntities;
import com.duckedupmods.automechs.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The Automechs Holo-Guide: a client-only illustrated, paginated field manual. It is meant to read like a
 * proper mod guidebook (Patchouli/Modonomicon style): every region is framed — a header (icon + title), a
 * text panel, and an illustration panel that shows either a live rotating render of the mech the page
 * describes, a crafting grid, or a catalog of items with hover tooltips. A clickable Contents page indexes
 * every entry so players can jump straight to any machine, role, tool or upgrade. Drawn holographic
 * cyan-on-dark. Arrows / scroll to page; click a Contents row to jump; Esc closes.
 */
public class GuideScreen extends Screen {

    private static final int W = 300;
    private static final int H = 216;
    private static final int PAD = 8;

    private static final int HOLO_BG = 0xFF0A1119;    // panel body
    private static final int BOX_BG = 0xFF0E1A24;     // framed sub-panel interior
    private static final int BOX_BORDER = 0xFF2C5E68; // dim-cyan frame
    private static final int TEXT = 0xFFE4F2F8;       // body text
    private static final int ACCENT_RGB = 0x3CD2E8;   // GuiHelper.ACCENT without alpha (for text styles)

    /** A region of a page (header / text / art): draws into the given inner rectangle. */
    @FunctionalInterface
    private interface Panel {
        void draw(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY);
    }

    private record Page(String key, ItemStack icon, Panel text, Panel art) {}

    /** A clickable Contents row: screen-space bounds + the page it jumps to. */
    private record Link(int x1, int y1, int x2, int y2, int target) {}

    /** Which vanilla item is the catalyst at the centre of each upgrade module's recipe. */
    private static final Map<UpgradeType, ItemStack> CATALYST = new EnumMap<>(UpgradeType.class);
    static {
        CATALYST.put(UpgradeType.SPEED, Items.SUGAR.getDefaultInstance());
        CATALYST.put(UpgradeType.CAPACITY, Items.REDSTONE_BLOCK.getDefaultInstance());
        CATALYST.put(UpgradeType.RANGE, Items.ENDER_PEARL.getDefaultInstance());
        CATALYST.put(UpgradeType.EFFICIENCY, Items.GOLD_BLOCK.getDefaultInstance());
        CATALYST.put(UpgradeType.FORTUNE, Items.DIAMOND.getDefaultInstance());
        CATALYST.put(UpgradeType.SMELTER, Items.FURNACE.getDefaultInstance());
        CATALYST.put(UpgradeType.SILK_TOUCH, Items.STRING.getDefaultInstance());
        CATALYST.put(UpgradeType.HAZARD_SEAL, Items.BUCKET.getDefaultInstance());
        CATALYST.put(UpgradeType.FERTILIZER, Items.BONE_BLOCK.getDefaultInstance());
        CATALYST.put(UpgradeType.SHARPNESS, Items.IRON_SWORD.getDefaultInstance());
        CATALYST.put(UpgradeType.LOOTING, Items.EMERALD.getDefaultInstance());
        CATALYST.put(UpgradeType.FIRE_ASPECT, Items.BLAZE_POWDER.getDefaultInstance());
        CATALYST.put(UpgradeType.KNOCKBACK, Items.PISTON.getDefaultInstance());
        CATALYST.put(UpgradeType.ARMOR, Items.IRON_BLOCK.getDefaultInstance());
        CATALYST.put(UpgradeType.SOUL_TANK, Items.EXPERIENCE_BOTTLE.getDefaultInstance());
        CATALYST.put(UpgradeType.MAGNET, Items.HOPPER.getDefaultInstance());
        CATALYST.put(UpgradeType.SOLAR, Items.DAYLIGHT_DETECTOR.getDefaultInstance());
        CATALYST.put(UpgradeType.ENDER_LINK, Items.ENDER_CHEST.getDefaultInstance());
    }

    private final Map<MechRole, MiningMech> previews = new EnumMap<>(MechRole.class);
    private final List<Page> pages = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();

    private int leftPos;
    private int topPos;
    private int page;
    private Button prev;
    private Button next;

    // On an upgrade-catalog page: which module the recipe template currently illustrates (hovered, else first).
    private UpgradeType focused;
    // On the circuits page: which circuit the recipe panel illustrates (hovered, else first).
    private MechRole focusedCircuit;

    // A tooltip queued by a panel this frame, drawn on top after everything else.
    private List<Component> tooltip;
    private int tipX;
    private int tipY;

    public GuideScreen() {
        super(Component.translatable("item.automechs.holo_guide"));
        buildPages();
    }

    private void buildPages() {
        ItemStack chip = ModItems.AI_CHIP.get().getDefaultInstance();

        // 0 — Contents (clickable index)
        pages.add(new Page("contents", ModItems.HOLO_GUIDE.get().getDefaultInstance(), this::drawContents, null));

        // Basics
        pages.add(new Page("overview", ModItems.MECH_CHASSIS.get().getDefaultInstance(),
                body("overview"), entity(MechRole.MINING, "Worker Mech")));

        // Power
        pages.add(new Page("power", ModItems.COMBUSTION_DYNAMO.get().getDefaultInstance(), body("power"),
                recipe(ModItems.COMBUSTION_DYNAMO.get().getDefaultInstance(), 1, "ICI", "IFI", "IRI",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'C', Items.COPPER_INGOT.getDefaultInstance(),
                                'F', Blocks.FURNACE.asItem().getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance()))));
        pages.add(new Page("conduit", ModItems.POWER_CONDUIT.get().getDefaultInstance(), body("conduit"),
                recipe(ModItems.POWER_CONDUIT.get().getDefaultInstance(), 6, "NNN", "RRR", "NNN",
                        Map.of('N', Items.IRON_NUGGET.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance()))));
        pages.add(new Page("charging_pad", ModItems.CHARGING_PAD.get().getDefaultInstance(), body("charging_pad"),
                recipe(ModItems.CHARGING_PAD.get().getDefaultInstance(), 1, "CRC", "RIR", "III",
                        Map.of('C', Items.COPPER_INGOT.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'I', Items.IRON_INGOT.getDefaultInstance()))));

        // Building mechs
        pages.add(new Page("fabricator", ModItems.ASSEMBLY_WORKSHOP.get().getDefaultInstance(), body("fabricator"),
                recipe(ModItems.ASSEMBLY_WORKSHOP.get().getDefaultInstance(), 1, "III", "ICI", "III",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'C', Blocks.CRAFTING_TABLE.asItem().getDefaultInstance()))));
        pages.add(new Page("part_plates", ModItems.MECH_PLATES.get().getDefaultInstance(), body("part_plates"),
                fabRecipe(ModItems.MECH_PLATES.get().getDefaultInstance(),
                        stack(Items.IRON_INGOT, 3), stack(Items.COPPER_INGOT, 2))));
        pages.add(new Page("part_core", ModItems.MECH_CORE.get().getDefaultInstance(), body("part_core"),
                fabRecipe(ModItems.MECH_CORE.get().getDefaultInstance(),
                        stack(Items.REDSTONE, 3), stack(Items.COPPER_INGOT, 1), stack(Items.DIAMOND, 1))));
        pages.add(new Page("part_chip", chip, body("part_chip"),
                fabRecipe(ModItems.AI_CHIP.get().getDefaultInstance(),
                        stack(Items.GOLD_INGOT, 2), stack(Items.LAPIS_LAZULI, 2), stack(Items.REDSTONE, 2))));
        pages.add(new Page("builder_bench", ModItems.MECH_ASSEMBLY_BENCH.get().getDefaultInstance(), body("builder_bench"),
                recipe(ModItems.MECH_ASSEMBLY_BENCH.get().getDefaultInstance(), 1, "ICI", "RBR", "III",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'C', Items.COPPER_INGOT.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'B', ModItems.ASSEMBLY_WORKSHOP.get().getDefaultInstance()))));
        pages.add(new Page("circuits", ModItems.circuit(MechRole.MINING).get().getDefaultInstance(),
                circuitList(), circuitRecipe()));

        // Roles (live mech renders)
        pages.add(new Page("mining", ModItems.circuit(MechRole.MINING).get().getDefaultInstance(),
                body("mining"), entity(MechRole.MINING, "Mining Mech")));
        pages.add(new Page("farming", ModItems.circuit(MechRole.FARMING).get().getDefaultInstance(),
                body("farming"), entity(MechRole.FARMING, "Farming Mech")));
        pages.add(new Page("builder", ModItems.circuit(MechRole.BUILDING).get().getDefaultInstance(),
                body("builder"), entity(MechRole.BUILDING, "Builder Mech")));
        pages.add(new Page("combat", ModItems.circuit(MechRole.COMBAT).get().getDefaultInstance(),
                body("combat"), entity(MechRole.COMBAT, "Combat Mech")));

        // Tools
        pages.add(new Page("linker", ModItems.MECH_LINKER.get().getDefaultInstance(), body("linker"),
                recipe(ModItems.MECH_LINKER.get().getDefaultInstance(), 1, " R ", " I ", " I ",
                        Map.of('R', Items.REDSTONE.getDefaultInstance(),
                                'I', Items.IRON_INGOT.getDefaultInstance()))));
        pages.add(new Page("tablet", ModItems.MECH_TABLET.get().getDefaultInstance(), body("tablet"),
                recipe(ModItems.MECH_TABLET.get().getDefaultInstance(), 1, "IGI", "ICI", "IRI",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'G', Items.GLASS_PANE.getDefaultInstance(),
                                'C', chip,
                                'R', Items.REDSTONE.getDefaultInstance()))));
        pages.add(new Page("holo", ModItems.HOLO_GUIDE.get().getDefaultInstance(), body("holo"),
                recipeRow("Recipe", ModItems.HOLO_GUIDE.get().getDefaultInstance(),
                        Items.BOOK.getDefaultInstance(),
                        Items.REDSTONE.getDefaultInstance(),
                        Items.GLOWSTONE_DUST.getDefaultInstance())));

        // Data storage network (v0.2): an FE-powered, AE2-style storage system + defrag bots.
        pages.add(new Page("storage", ModItems.STORAGE_TERMINAL.get().getDefaultInstance(), body("storage"),
                itemCard(ModItems.MAIN_DRIVE.get().getDefaultInstance(), "Powered Network")));
        pages.add(new Page("main_drive", ModItems.MAIN_DRIVE.get().getDefaultInstance(), body("main_drive"),
                recipe(ModItems.MAIN_DRIVE.get().getDefaultInstance(), 1, "IGI", "RDR", "ICI",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'G', Items.GLASS_PANE.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'D', Items.DIAMOND.getDefaultInstance(),
                                'C', chip))));
        pages.add(new Page("data_cable", ModItems.DATA_CABLE.get().getDefaultInstance(), body("data_cable"),
                recipe(ModItems.DATA_CABLE.get().getDefaultInstance(), 8, "NNN", "RGR", "NNN",
                        Map.of('N', Items.IRON_NUGGET.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'G', Items.GLASS_PANE.getDefaultInstance()))));
        pages.add(new Page("data_rack", ModItems.DATA_RACK.get().getDefaultInstance(), body("data_rack"),
                recipe(ModItems.DATA_RACK.get().getDefaultInstance(), 1, "IGI", "RCR", "IGI",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'G', Items.GLASS_PANE.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'C', Blocks.CHEST.asItem().getDefaultInstance()))));
        pages.add(new Page("storage_terminal", ModItems.STORAGE_TERMINAL.get().getDefaultInstance(), body("storage_terminal"),
                recipe(ModItems.STORAGE_TERMINAL.get().getDefaultInstance(), 1, "IGI", "GCG", "IRI",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'G', Items.GLASS_PANE.getDefaultInstance(),
                                'C', chip,
                                'R', Items.REDSTONE.getDefaultInstance()))));
        pages.add(new Page("cache_crawler", ModItems.CACHE_CRAWLER_SPAWN_EGG.get().getDefaultInstance(), body("cache_crawler"),
                recipe(ModItems.CACHE_CRAWLER_SPAWN_EGG.get().getDefaultInstance(), 1, "N N", "RCR", "N N",
                        Map.of('N', Items.IRON_NUGGET.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'C', chip))));

        // Upgrades
        pages.add(new Page("upgrade_station", ModItems.UPGRADE_STATION.get().getDefaultInstance(), body("upgrade_station"),
                recipe(ModItems.UPGRADE_STATION.get().getDefaultInstance(), 1, "ICI", "RAR", "III",
                        Map.of('I', Items.IRON_INGOT.getDefaultInstance(),
                                'C', Items.COPPER_INGOT.getDefaultInstance(),
                                'R', Items.REDSTONE.getDefaultInstance(),
                                'A', Blocks.ANVIL.asItem().getDefaultInstance()))));
        pages.add(new Page("upgrades_core", ModItems.upgrade(UpgradeType.SPEED).get().getDefaultInstance(),
                moduleList(UpgradeType.SPEED, UpgradeType.CAPACITY, UpgradeType.RANGE, UpgradeType.EFFICIENCY),
                recipeTemplate()));
        pages.add(new Page("upgrades_mining", ModItems.upgrade(UpgradeType.FORTUNE).get().getDefaultInstance(),
                moduleList(UpgradeType.FORTUNE, UpgradeType.SMELTER, UpgradeType.SILK_TOUCH, UpgradeType.HAZARD_SEAL),
                recipeTemplate()));
        pages.add(new Page("upgrades_combat", ModItems.upgrade(UpgradeType.SHARPNESS).get().getDefaultInstance(),
                moduleList(UpgradeType.SHARPNESS, UpgradeType.LOOTING, UpgradeType.FIRE_ASPECT,
                        UpgradeType.KNOCKBACK, UpgradeType.ARMOR, UpgradeType.SOUL_TANK),
                recipeTemplate()));
        pages.add(new Page("upgrades_field", ModItems.upgrade(UpgradeType.MAGNET).get().getDefaultInstance(),
                moduleList(UpgradeType.FERTILIZER, UpgradeType.MAGNET, UpgradeType.SOLAR, UpgradeType.ENDER_LINK),
                recipeTemplate()));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - W) / 2;
        this.topPos = (this.height - H) / 2;

        this.prev = Button.builder(Component.literal("◀"), b -> turn(-1))
                .bounds(this.leftPos + PAD, this.topPos + H - 24, 22, 16).build();
        this.next = Button.builder(Component.literal("▶"), b -> turn(1))
                .bounds(this.leftPos + W - PAD - 22, this.topPos + H - 24, 22, 16).build();
        addRenderableWidget(this.prev);
        addRenderableWidget(this.next);
        addRenderableWidget(Button.builder(Component.translatable("gui.automechs.guide.close"), b -> onClose())
                .bounds(this.leftPos + W / 2 - 40, this.topPos + H - 24, 80, 16).build());
        refreshButtons();
    }

    private void turn(int delta) {
        this.page = Math.max(0, Math.min(this.pages.size() - 1, this.page + delta));
        refreshButtons();
    }

    private void refreshButtons() {
        this.prev.active = this.page > 0;
        this.next.active = this.page < this.pages.size() - 1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick); // blur the world ONCE (see note at the widget loop)
        this.tooltip = null;
        this.links.clear();
        int x = this.leftPos;
        int y = this.topPos;

        // Outer holographic shell.
        g.fill(x, y, x + W, y + H, HOLO_BG);
        holoFrame(g, x, y, W, H);

        Page p = this.pages.get(this.page);
        int innerW = W - PAD * 2;

        // Header box: title icon + title text + chapter marker.
        framedBox(g, x + PAD, y + PAD, innerW, 22);
        g.renderItem(p.icon(), x + PAD + 4, y + PAD + 3);
        g.drawString(this.font, Component.translatable("guide.automechs." + p.key() + ".title"),
                x + PAD + 26, y + PAD + 7, GuiHelper.ACCENT, true);

        int contentTop = y + PAD + 26;
        int contentH = 140;

        if ("contents".equals(p.key())) {
            // Contents uses the full content width for a two-column clickable index.
            framedBox(g, x + PAD, contentTop, innerW, contentH);
            clipped(g, x + PAD, contentTop, innerW, contentH, () ->
                    p.text().draw(g, x + PAD + 6, contentTop + 5, innerW - 12, contentH - 10, mouseX, mouseY));
        } else {
            int textW = 176;
            int artW = innerW - textW - 6;

            framedBox(g, x + PAD, contentTop, textW, contentH);
            if (p.text() != null) {
                clipped(g, x + PAD, contentTop, textW, contentH, () ->
                        p.text().draw(g, x + PAD + 6, contentTop + 5, textW - 12, contentH - 10, mouseX, mouseY));
            }

            int artX = x + PAD + textW + 6;
            framedBox(g, artX, contentTop, artW, contentH);
            if (p.art() != null) {
                clipped(g, artX, contentTop, artW, contentH, () ->
                        p.art().draw(g, artX + 4, contentTop + 4, artW - 8, contentH - 8, mouseX, mouseY));
            }
        }

        // Framed footer holding the page indicator + nav buttons.
        int footTop = contentTop + contentH + 2;
        framedBox(g, x + PAD, footTop, innerW, H - (footTop - y) - PAD);
        String pos = (this.page + 1) + " / " + this.pages.size();
        g.drawString(this.font, pos, x + W / 2 - this.font.width(pos) / 2, footTop + 4, GuiHelper.TEXT_DIM, false);

        // Render the widgets (buttons) directly — NOT via super.render(), which calls renderBackground() a
        // second time and would run the blur shader over the panel + text we just drew.
        for (Renderable widget : this.renderables) {
            widget.render(g, mouseX, mouseY, partialTick);
        }

        // Any tooltip a panel queued this frame goes on top of everything.
        if (this.tooltip != null) {
            g.renderComponentTooltip(this.font, this.tooltip, this.tipX, this.tipY);
        }
    }

    // --- Text panels ---------------------------------------------------------

    /** The default left panel: the page's wrapped body text. */
    private Panel body(String key) {
        return (g, x, y, w, h, mx, my) -> {
            List<FormattedCharSequence> lines = this.font.split(
                    Component.translatable("guide.automechs." + key + ".body"), w);
            int ty = y;
            for (FormattedCharSequence line : lines) {
                g.drawString(this.font, line, x, ty, TEXT, true);
                ty += this.font.lineHeight + 2;
            }
        };
    }

    /**
     * A vertical catalog of upgrade modules: each row shows the module icon + name + max level, and on the
     * right the catalyst its recipe needs (a "+ item" hint). Hovering a row shows the full description and
     * sets it as the module the recipe panel illustrates.
     */
    private Panel moduleList(UpgradeType... types) {
        return (g, x, y, w, h, mx, my) -> {
            this.focused = types.length > 0 ? types[0] : null;
            int rowH = Math.min(28, (h) / Math.max(1, types.length));
            int yy = y;
            for (UpgradeType t : types) {
                ItemStack mod = ModItems.upgrade(t).get().getDefaultInstance();
                ItemStack cat = CATALYST.get(t);
                boolean hover = mx >= x && mx < x + w && my >= yy && my < yy + rowH;
                if (hover) {
                    g.fill(x, yy, x + w, yy + rowH, 0x3033E0F0);
                    this.focused = t;
                }
                int iconY = yy + (rowH - 18) / 2;
                GuiHelper.darkSlot(g, x + 1, iconY);
                g.renderItem(mod, x + 1, iconY);
                g.drawString(this.font, mod.getHoverName(), x + 23, yy + 4, TEXT, false);
                g.drawString(this.font, "Max ×" + t.maxLevel(), x + 23, yy + 4 + this.font.lineHeight,
                        GuiHelper.TEXT_DIM, false);
                // The catalyst it needs, shown at the right edge as "+ <item>".
                int catX = x + w - 19;
                g.drawString(this.font, "+", catX - 7, iconY + 5, GuiHelper.TEXT_DIM, false);
                GuiHelper.darkSlot(g, catX, iconY);
                g.renderItem(cat, catX, iconY);
                if (hover) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(mod.getHoverName().copy().withStyle(accent()));
                    tip.add(Component.literal("Max level " + t.maxLevel()).withStyle(ChatFormatting.GRAY));
                    tip.add(Component.literal("Recipe: Copper + Redstone frame + " + cat.getHoverName().getString())
                            .withStyle(ChatFormatting.DARK_GRAY));
                    tip.add(Component.empty());
                    for (Component line : wrap(Component.translatable(t.descriptionKey()).getString(), 170)) {
                        tip.add(line.copy().withStyle(ChatFormatting.GRAY));
                    }
                    this.tooltip = tip;
                    this.tipX = mx;
                    this.tipY = my;
                }
                yy += rowH;
            }
        };
    }

    /**
     * A list of the role circuits on the wide (text) side: icon + name + a one-line role summary. Hovering a
     * row sets it as the circuit the recipe panel illustrates.
     */
    private Panel circuitList() {
        MechRole[] roles = {MechRole.MINING, MechRole.FARMING, MechRole.BUILDING, MechRole.COMBAT};
        String[] desc = {"Digs quarry boxes", "Tends crop fields", "Builds schematics", "Guards and fights"};
        return (g, x, y, w, h, mx, my) -> {
            this.focusedCircuit = roles[0];
            int rowH = Math.min(32, h / roles.length);
            int yy = y;
            for (int i = 0; i < roles.length; i++) {
                boolean hover = mx >= x && mx < x + w && my >= yy && my < yy + rowH;
                if (hover) {
                    g.fill(x, yy, x + w, yy + rowH, 0x3033E0F0);
                    this.focusedCircuit = roles[i];
                }
                ItemStack c = ModItems.circuit(roles[i]).get().getDefaultInstance();
                int iconY = yy + (rowH - 18) / 2;
                GuiHelper.darkSlot(g, x + 1, iconY);
                g.renderItem(c, x + 1, iconY);
                g.drawString(this.font, c.getHoverName(), x + 23, yy + rowH / 2 - this.font.lineHeight - 1,
                        TEXT, false);
                g.drawString(this.font, desc[i], x + 23, yy + rowH / 2 + 1, GuiHelper.TEXT_DIM, false);
                yy += rowH;
            }
        };
    }

    /** The role-circuit recipe for the focused circuit: gold + redstone + that role's defining tool. */
    private Panel circuitRecipe() {
        return (g, ax, ay, aw, ah, mouseX, mouseY) -> {
            MechRole role = this.focusedCircuit != null ? this.focusedCircuit : MechRole.MINING;
            ItemStack result = ModItems.circuit(role).get().getDefaultInstance();
            recipeRow("Fabricator", result,
                    stack(Items.GOLD_INGOT, 2), stack(Items.REDSTONE, 2), circuitTool(role))
                    .draw(g, ax, ay, aw, ah, mouseX, mouseY);
        };
    }

    private static ItemStack circuitTool(MechRole role) {
        return switch (role) {
            case MINING -> stack(Items.IRON_PICKAXE, 1);
            case FARMING -> stack(Items.IRON_HOE, 1);
            case BUILDING -> stack(Blocks.PISTON, 1);
            case COMBAT -> stack(Items.IRON_SWORD, 1);
        };
    }

    /** The clickable Contents index: every page laid out in two columns. */
    private void drawContents(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int rows = (this.pages.size() - 1 + 1) / 2; // pages excluding contents itself, two columns
        int colW = w / 2;
        int rowH = Math.max(this.font.lineHeight + 3, (h) / Math.max(1, rows));
        for (int i = 1; i < this.pages.size(); i++) {
            int idx = i - 1;
            int col = idx / rows;
            int row = idx % rows;
            int cx = x + col * colW;
            int cy = y + row * rowH;
            Page pg = this.pages.get(i);
            boolean hover = mx >= cx && mx < cx + colW - 4 && my >= cy && my < cy + rowH;
            String num = (i + 1) + ".";
            Component title = Component.translatable("guide.automechs." + pg.key() + ".title");
            g.drawString(this.font, num, cx, cy + 2, GuiHelper.TEXT_DIM, false);
            g.drawString(this.font, title, cx + 18, cy + 2, hover ? GuiHelper.ACCENT : TEXT, false);
            this.links.add(new Link(cx, cy, cx + colW - 4, cy + rowH, i));
        }
    }

    // --- Art panels ----------------------------------------------------------

    /** A live, mouse-following render of the mech for the given role, with a caption. */
    private Panel entity(MechRole role, String caption) {
        return (g, ax, ay, aw, ah, mouseX, mouseY) -> {
            int artH = ah - 12; // leave room for the caption
            MiningMech mech = previews.computeIfAbsent(role, this::makePreview);
            if (mech != null) {
                int scale = (int) (artH * 0.42F);
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, ax, ay, ax + aw, ay + artH, scale, 0.0F, mouseX, mouseY, mech);
            }
            caption(g, caption, ax, ay, aw, ah);
        };
    }

    private MiningMech makePreview(MechRole role) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        MiningMech mech = ModEntities.MINING_MECH.get().create(Minecraft.getInstance().level);
        if (mech != null) {
            mech.setRole(role);
        }
        return mech;
    }

    /** A crafting-grid render: a 3×3 of slots, an arrow, the result (with count), and a "Recipe" caption. */
    private Panel recipe(ItemStack result, int count, String r0, String r1, String r2, Map<Character, ItemStack> key) {
        ItemStack[] grid = new ItemStack[9];
        String[] rows = {r0, r1, r2};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                char c = rows[row].charAt(col);
                grid[row * 3 + col] = c == ' ' ? ItemStack.EMPTY : key.getOrDefault(c, ItemStack.EMPTY);
            }
        }
        ItemStack out = result.copy();
        out.setCount(count);
        return (g, ax, ay, aw, ah, mouseX, mouseY) -> {
            int gridW = 54;
            int sx = ax + Math.max(0, (aw - gridW) / 2);
            int sy = ay + 4;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int cx = sx + col * 18;
                    int cy = sy + row * 18;
                    GuiHelper.darkSlot(g, cx + 1, cy + 1);
                    ItemStack s = grid[row * 3 + col];
                    if (!s.isEmpty()) {
                        g.renderItem(s, cx + 1, cy + 1);
                        if (mouseX >= cx + 1 && mouseX < cx + 17 && mouseY >= cy + 1 && mouseY < cy + 17) {
                            queueStackTooltip(s, mouseX, mouseY);
                        }
                    }
                }
            }
            int arrowY = sy + 54 + 1;
            g.drawString(this.font, "▼", ax + aw / 2 - 3, arrowY, GuiHelper.ACCENT, false);
            int rx = ax + aw / 2 - 9;
            int ry = arrowY + 11;
            GuiHelper.darkSlot(g, rx + 1, ry + 1);
            g.renderItem(out, rx + 1, ry + 1);
            g.renderItemDecorations(this.font, out, rx + 1, ry + 1);
            if (mouseX >= rx + 1 && mouseX < rx + 17 && mouseY >= ry + 1 && mouseY < ry + 17) {
                queueStackTooltip(out, mouseX, mouseY);
            }
            caption(g, "Recipe", ax, ay, aw, ah);
        };
    }

    /** A Fabricator recipe: the consumed ingredients (with counts) in a row, an arrow, then the result. */
    private Panel fabRecipe(ItemStack result, ItemStack... inputs) {
        return recipeRow("Fabricator", result, inputs);
    }

    /** A row of input stacks (with count badges) → arrow → result, under the given caption. */
    private Panel recipeRow(String caption, ItemStack result, ItemStack... inputs) {
        return (g, ax, ay, aw, ah, mouseX, mouseY) -> {
            int rowW = inputs.length * 18;
            int sx = ax + Math.max(0, (aw - rowW) / 2);
            int sy = ay + 14;
            for (int i = 0; i < inputs.length; i++) {
                int cx = sx + i * 18;
                GuiHelper.darkSlot(g, cx + 1, sy + 1);
                g.renderItem(inputs[i], cx + 1, sy + 1);
                g.renderItemDecorations(this.font, inputs[i], cx + 1, sy + 1);
                if (mouseX >= cx + 1 && mouseX < cx + 17 && mouseY >= sy + 1 && mouseY < sy + 17) {
                    queueStackTooltip(inputs[i], mouseX, mouseY);
                }
            }
            int arrowY = sy + 22;
            g.drawString(this.font, "▼", ax + aw / 2 - 3, arrowY, GuiHelper.ACCENT, false);
            int rx = ax + aw / 2 - 9;
            int ry = arrowY + 11;
            GuiHelper.darkSlot(g, rx + 1, ry + 1);
            g.renderItem(result, rx + 1, ry + 1);
            g.renderItemDecorations(this.font, result, rx + 1, ry + 1);
            if (mouseX >= rx + 1 && mouseX < rx + 17 && mouseY >= ry + 1 && mouseY < ry + 17) {
                queueStackTooltip(result, mouseX, mouseY);
            }
            caption(g, caption, ax, ay, aw, ah);
        };
    }

    /**
     * The upgrade-module recipe: a Copper + Redstone frame around a catalyst. The centre shows the catalyst
     * of the module currently focused in the list (hovered, else the first), so players see exactly what each
     * module needs — never a placeholder.
     */
    private Panel recipeTemplate() {
        ItemStack r = Items.REDSTONE.getDefaultInstance();
        ItemStack c = Items.COPPER_INGOT.getDefaultInstance();
        return (g, ax, ay, aw, ah, mouseX, mouseY) -> {
            ItemStack cat = this.focused != null ? CATALYST.get(this.focused) : ItemStack.EMPTY;
            ItemStack[] grid = {r, c, r, c, cat, c, r, c, r};
            int gridW = 54;
            int sx = ax + Math.max(0, (aw - gridW) / 2);
            int sy = ay + 12;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int cx = sx + col * 18;
                    int cy = sy + row * 18;
                    GuiHelper.darkSlot(g, cx + 1, cy + 1);
                    ItemStack s = grid[row * 3 + col];
                    if (!s.isEmpty()) {
                        g.renderItem(s, cx + 1, cy + 1);
                        if (mouseX >= cx + 1 && mouseX < cx + 17 && mouseY >= cy + 1 && mouseY < cy + 17) {
                            queueStackTooltip(s, mouseX, mouseY);
                        }
                    }
                }
            }
            String catName = this.focused != null ? CATALYST.get(this.focused).getHoverName().getString() : "Catalyst";
            caption(g, "Frame + " + catName, ax, ay, aw, ah);
        };
    }

    /** A single large item centred in the art box with a caption — for entries without a crafting recipe. */
    private Panel itemCard(ItemStack showcase, String caption) {
        return (g, ax, ay, aw, ah, mouseX, mouseY) -> {
            int cx = ax + aw / 2 - 8;
            int cy = ay + (ah - 12) / 2 - 8;
            GuiHelper.darkSlot(g, cx, cy);
            g.renderItem(showcase, cx, cy);
            if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
                queueStackTooltip(showcase, mouseX, mouseY);
            }
            caption(g, caption, ax, ay, aw, ah);
        };
    }

    private static ItemStack stack(net.minecraft.world.level.ItemLike item, int count) {
        ItemStack s = new ItemStack(item);
        s.setCount(count);
        return s;
    }

    /** A small centred caption at the bottom of an art box. */
    private void caption(GuiGraphics g, String text, int ax, int ay, int aw, int ah) {
        g.drawString(this.font, text, ax + aw / 2 - this.font.width(text) / 2, ay + ah - 9,
                GuiHelper.ACCENT, false);
    }

    private void queueStackTooltip(ItemStack stack, int mx, int my) {
        this.tooltip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        this.tipX = mx;
        this.tipY = my;
    }

    private static Style accent() {
        return Style.EMPTY.withColor(TextColor.fromRgb(ACCENT_RGB)).withBold(true);
    }

    /** Greedy word-wrap of a plain string into literal components at the given pixel width. */
    private List<Component> wrap(String s, int maxW) {
        List<Component> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String trial = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(trial) > maxW && line.length() > 0) {
                out.add(Component.literal(line.toString()));
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(trial);
            }
        }
        if (line.length() > 0) {
            out.add(Component.literal(line.toString()));
        }
        return out;
    }

    // --- Framing -------------------------------------------------------------

    /** Runs {@code draw} with rendering scissor-clipped to the interior of a frame, so nothing overflows it. */
    private static void clipped(GuiGraphics g, int x, int y, int w, int h, Runnable draw) {
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        draw.run();
        g.disableScissor();
    }

    /** A recessed, cyan-bordered sub-panel — the frame used for the header, text and art regions. */
    private static void framedBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BOX_BG);
        g.fill(x, y, x + w, y + 1, BOX_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, BOX_BORDER);
        g.fill(x, y, x + 1, y + h, BOX_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, BOX_BORDER);
    }

    /** A bright cyan outer frame with brighter corner ticks — the holo-projector edge. */
    private static void holoFrame(GuiGraphics g, int x, int y, int w, int h) {
        int c = GuiHelper.ACCENT;
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
        int t = 6;
        int cc = 0xFFCFFFFF;
        g.fill(x, y, x + t, y + 2, cc);
        g.fill(x, y, x + 2, y + t, cc);
        g.fill(x + w - t, y, x + w, y + 2, cc);
        g.fill(x + w - 2, y, x + w, y + t, cc);
        g.fill(x, y + h - 2, x + t, y + h, cc);
        g.fill(x, y + h - t, x + 2, y + h, cc);
        g.fill(x + w - t, y + h - 2, x + w, y + h, cc);
        g.fill(x + w - 2, y + h - t, x + w, y + h, cc);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.page == 0 && button == 0) {
            for (Link link : this.links) {
                if (mx >= link.x1() && mx < link.x2() && my >= link.y1() && my < link.y2()) {
                    this.page = link.target();
                    refreshButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (dy != 0) {
            turn(dy > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
