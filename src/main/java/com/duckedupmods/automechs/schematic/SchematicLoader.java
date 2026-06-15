package com.duckedupmods.automechs.schematic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the open schematic file formats into a mod-agnostic {@link Blueprint} — no Litematica (or any
 * other mod) needs to be installed, we just parse the documented NBT layouts ourselves:
 * <ul>
 *   <li><b>{@code .litematic}</b> — Litematica's gzipped NBT: one or more regions, each a block-state
 *       palette plus a bit-packed long array of indices.</li>
 *   <li><b>vanilla {@code .nbt}</b> — structure-block files: a palette plus a list of positioned states.</li>
 * </ul>
 *
 * <p>Unknown blocks (from mods not installed) resolve to air and are skipped, so a schematic still builds
 * its known parts. Block-entity contents and entities are ignored (MVP). Both file types are gzipped NBT,
 * so {@link NbtIo#readCompressed} handles either.</p>
 */
public final class SchematicLoader {

    /** Hard sanity ceiling on a single file's volume so a malformed/hostile file can't OOM us. */
    private static final long MAX_VOLUME = 4_000_000L;
    private static final long NBT_QUOTA = 64L * 1024L * 1024L; // 64 MiB heap budget for the read

    private static final HolderGetter<Block> BLOCKS = BuiltInRegistries.BLOCK.asLookup();

    private SchematicLoader() {
    }

    /** Parse a schematic file on disk (server-side folder, or client picker). */
    public static Blueprint fromFile(Path path) throws IOException {
        CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(NBT_QUOTA));
        return fromRoot(root);
    }

    /** Parse schematic bytes (e.g. a client→server upload payload). */
    public static Blueprint fromBytes(byte[] data) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.create(NBT_QUOTA));
            return fromRoot(root);
        }
    }

    /** Dispatch by detected format. */
    public static Blueprint fromRoot(CompoundTag root) throws IOException {
        if (root.contains("Regions", Tag.TAG_COMPOUND)) {
            return fromLitematic(root);
        }
        if (root.contains("blocks", Tag.TAG_LIST) && (root.contains("palette", Tag.TAG_LIST) || root.contains("palettes", Tag.TAG_LIST))) {
            return fromVanilla(root);
        }
        throw new IOException("Unrecognized schematic format (not a .litematic or vanilla .nbt structure)");
    }

    // ------------------------------------------------------------------ Litematica

    private static Blueprint fromLitematic(CompoundTag root) throws IOException {
        CompoundTag regions = root.getCompound("Regions");
        List<RawBlock> raw = new ArrayList<>();
        long totalVolume = 0L;

        for (String name : regions.getAllKeys()) {
            CompoundTag region = regions.getCompound(name);
            CompoundTag posTag = region.getCompound("Position");
            CompoundTag sizeTag = region.getCompound("Size");
            int px = posTag.getInt("x");
            int py = posTag.getInt("y");
            int pz = posTag.getInt("z");
            int sx = sizeTag.getInt("x");
            int sy = sizeTag.getInt("y");
            int sz = sizeTag.getInt("z");
            int ax = Math.abs(sx);
            int ay = Math.abs(sy);
            int az = Math.abs(sz);
            long volume = (long) ax * ay * az;
            totalVolume += volume;
            if (totalVolume > MAX_VOLUME) {
                throw new IOException("Schematic too large (over " + MAX_VOLUME + " blocks)");
            }
            if (volume == 0) {
                continue;
            }

            BlockState[] palette = readPalette(region.getList("BlockStatePalette", Tag.TAG_COMPOUND));
            long[] longs = region.getLongArray("BlockStates");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.length - 1)));
            long mask = (1L << bits) - 1L;

            // Local coords grow in the direction of Size's sign, anchored at Position.
            int dirX = sx < 0 ? -1 : 1;
            int dirY = sy < 0 ? -1 : 1;
            int dirZ = sz < 0 ? -1 : 1;

            for (int y = 0; y < ay; y++) {
                for (int z = 0; z < az; z++) {
                    for (int x = 0; x < ax; x++) {
                        long index = (long) (y * az + z) * ax + x;
                        int id = longs.length == 0 ? 0 : bitAt(longs, index, bits, mask);
                        if (id < 0 || id >= palette.length) {
                            continue;
                        }
                        BlockState state = palette[id];
                        if (isSkippable(state)) {
                            continue;
                        }
                        raw.add(new RawBlock(px + dirX * x, py + dirY * y, pz + dirZ * z, state));
                    }
                }
            }
        }
        return normalize(raw);
    }

    /** Litematica's bit-packed accessor: entries are {@code bits} wide and may straddle two longs. */
    private static int bitAt(long[] longs, long index, int bits, long mask) {
        long startOffset = index * bits;
        int startArr = (int) (startOffset >>> 6);
        int endArr = (int) (((index + 1L) * bits - 1L) >>> 6);
        int startBit = (int) (startOffset & 0x3F);
        if (startArr == endArr) {
            return (int) ((longs[startArr] >>> startBit) & mask);
        }
        int endBits = 64 - startBit;
        return (int) (((longs[startArr] >>> startBit) | (longs[endArr] << endBits)) & mask);
    }

    // ------------------------------------------------------------------ vanilla .nbt

    private static Blueprint fromVanilla(CompoundTag root) throws IOException {
        ListTag sizeList = root.getList("size", Tag.TAG_INT);
        if (sizeList.size() < 3) {
            throw new IOException("Vanilla structure missing size");
        }
        long volume = (long) sizeList.getInt(0) * sizeList.getInt(1) * sizeList.getInt(2);
        if (volume > MAX_VOLUME) {
            throw new IOException("Schematic too large (over " + MAX_VOLUME + " blocks)");
        }

        ListTag paletteTag = root.contains("palette", Tag.TAG_LIST)
                ? root.getList("palette", Tag.TAG_COMPOUND)
                : root.getList("palettes", Tag.TAG_LIST).getList(0);
        BlockState[] palette = readPalette(paletteTag);

        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        List<RawBlock> raw = new ArrayList<>(blocks.size());
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            int id = b.getInt("state");
            if (id < 0 || id >= palette.length) {
                continue;
            }
            BlockState state = palette[id];
            if (isSkippable(state)) {
                continue;
            }
            ListTag p = b.getList("pos", Tag.TAG_INT);
            if (p.size() < 3) {
                continue;
            }
            raw.add(new RawBlock(p.getInt(0), p.getInt(1), p.getInt(2), state));
        }
        return normalize(raw);
    }

    // ------------------------------------------------------------------ shared

    private static BlockState[] readPalette(ListTag list) {
        BlockState[] palette = new BlockState[list.size()];
        for (int i = 0; i < list.size(); i++) {
            palette[i] = NbtUtils.readBlockState(BLOCKS, list.getCompound(i));
        }
        return palette;
    }

    /** Air-likes and vanilla {@code structure_void} mean "leave the existing block" — never placed. */
    private static boolean isSkippable(BlockState state) {
        return state.isAir() || state.is(Blocks.STRUCTURE_VOID);
    }

    /** Shift every block so the blueprint's minimum corner is (0,0,0), and wrap as a {@link Blueprint}. */
    private static Blueprint normalize(List<RawBlock> raw) {
        if (raw.isEmpty()) {
            return new Blueprint(new Vec3i(0, 0, 0), List.of());
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RawBlock r : raw) {
            minX = Math.min(minX, r.x); minY = Math.min(minY, r.y); minZ = Math.min(minZ, r.z);
            maxX = Math.max(maxX, r.x); maxY = Math.max(maxY, r.y); maxZ = Math.max(maxZ, r.z);
        }
        List<Blueprint.Placement> placements = new ArrayList<>(raw.size());
        for (RawBlock r : raw) {
            placements.add(new Blueprint.Placement(new BlockPos(r.x - minX, r.y - minY, r.z - minZ), r.state));
        }
        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        return new Blueprint(size, placements);
    }

    private record RawBlock(int x, int y, int z, BlockState state) {}
}
