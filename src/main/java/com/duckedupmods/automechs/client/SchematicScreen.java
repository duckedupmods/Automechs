package com.duckedupmods.automechs.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.network.UploadSchematicPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-only schematic picker for a Builder mech. Lists the {@code .litematic} / {@code .nbt} files in the
 * game's {@code schematics/} folder, with an "Open Folder" button so the player can drop files in. Picking
 * one reads its bytes and uploads them to the server (which re-parses and stamps the blueprint on the mech).
 */
public class SchematicScreen extends Screen {

    private static final int ROW_H = 14;
    private static final int LIST_TOP = 38;

    private final UUID mechId;
    private final List<Path> files = new ArrayList<>();
    private Path folder;
    private int scroll;
    private int listBottom;

    public SchematicScreen(UUID mechId) {
        super(Component.translatable("gui.automechs.schematic.title"));
        this.mechId = mechId;
    }

    /** The game-directory {@code schematics/} folder (created on first use). */
    public static Path schematicsFolder() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
    }

    @Override
    protected void init() {
        this.folder = schematicsFolder();
        refresh();

        this.listBottom = this.height - 40;

        addRenderableWidget(Button.builder(Component.translatable("gui.automechs.schematic.open_folder"), b -> openFolder())
                .bounds(this.width / 2 - 158, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.automechs.schematic.refresh"), b -> refresh())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents_GUI_CANCEL(), b -> onClose())
                .bounds(this.width / 2 + 58, this.height - 28, 100, 20).build());
    }

    private static Component CommonComponents_GUI_CANCEL() {
        return Component.translatable("gui.cancel");
    }

    private void refresh() {
        this.files.clear();
        this.scroll = 0;
        try {
            if (!Files.isDirectory(this.folder)) {
                Files.createDirectories(this.folder);
            }
            try (Stream<Path> stream = Files.list(this.folder)) {
                stream.filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.endsWith(".litematic") || n.endsWith(".nbt") || n.endsWith(".schem");
                        })
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEach(this.files::add);
            }
        } catch (IOException ignored) {
            // Folder unavailable — leave the list empty.
        }
    }

    private void openFolder() {
        try {
            if (!Files.isDirectory(this.folder)) {
                Files.createDirectories(this.folder);
            }
        } catch (IOException ignored) {
        }
        Util.getPlatform().openUri(this.folder.toUri());
    }

    private int visibleRows() {
        return Math.max(0, (this.listBottom - LIST_TOP) / ROW_H);
    }

    private void pick(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            if (data.length == 0 || data.length > UploadSchematicPayload.MAX_BYTES) {
                message("gui.automechs.schematic.too_big");
                return;
            }
            // Parse client-side too so the placement ghost can show the real blocks (server re-parses for auth).
            try {
                SchematicGhost.set(this.mechId, com.duckedupmods.automechs.schematic.SchematicLoader.fromBytes(data));
            } catch (Exception ignored) {
                // Preview unavailable — the wireframe box still works; the server validates the upload.
            }
            PacketDistributor.sendToServer(new UploadSchematicPayload(this.mechId, file.getFileName().toString(), data));
            onClose();
        } catch (IOException e) {
            message("gui.automechs.schematic.read_error");
        }
    }

    private void message(String key) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && my >= LIST_TOP && my < this.listBottom) {
            int row = (int) ((my - LIST_TOP) / ROW_H) + this.scroll;
            if (row >= 0 && row < this.files.size() && mx >= this.width / 2 - 158 && mx <= this.width / 2 + 158) {
                pick(this.files.get(row));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, this.files.size() - visibleRows());
        this.scroll = Mth.clamp(this.scroll - (int) Math.signum(dy), 0, max);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        int x0 = this.width / 2 - 158;
        int x1 = this.width / 2 + 158;
        g.fill(x0 - 2, LIST_TOP - 2, x1 + 2, this.listBottom + 2, 0x66000000);

        if (this.files.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.automechs.schematic.empty"),
                    this.width / 2, LIST_TOP + 8, 0xFF9AA7B4);
            return;
        }

        int rows = visibleRows();
        for (int i = 0; i < rows && (i + this.scroll) < this.files.size(); i++) {
            int idx = i + this.scroll;
            int y = LIST_TOP + i * ROW_H;
            boolean hover = mouseX >= x0 && mouseX <= x1 && mouseY >= y && mouseY < y + ROW_H;
            if (hover) {
                g.fill(x0, y, x1, y + ROW_H, 0x55308AE0);
            }
            String name = this.files.get(idx).getFileName().toString();
            g.drawString(this.font, name, x0 + 4, y + 3, hover ? 0xFFFFFFFF : 0xFFC4D2DE, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Tiny hook so callers don't need to import this screen's package. */
    public static void open(UUID mechId) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new SchematicScreen(mechId));
        Automechs.LOGGER.debug("Opened schematic picker");
    }
}
