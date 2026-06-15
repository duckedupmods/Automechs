package com.duckedupmods.automechs.schematic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A parsed, mod-agnostic structure the Builder mech can construct: a bounding size plus an ordered list
 * of block placements (relative to a (0,0,0) origin, air already stripped) and a derived material list.
 *
 * <p>This is produced by {@link SchematicLoader} from a {@code .litematic} or vanilla {@code .nbt} file —
 * we read the open file formats ourselves, so neither Litematica nor any other mod needs to be installed.
 * Placements are sorted bottom-up (ascending Y) so the mech can stand on its own work as it climbs.</p>
 */
public final class Blueprint {

    /** One block to place: position relative to the blueprint origin, and the exact state to set. */
    public record Placement(BlockPos pos, BlockState state) {}

    private final Vec3i size;
    private final List<Placement> placements;

    public Blueprint(Vec3i size, List<Placement> placements) {
        this.size = size;
        // Bottom-up so the builder never places a floating block it then can't reach.
        List<Placement> sorted = new ArrayList<>(placements);
        sorted.sort((a, b) -> {
            int dy = Integer.compare(a.pos().getY(), b.pos().getY());
            if (dy != 0) {
                return dy;
            }
            int dx = Integer.compare(a.pos().getX(), b.pos().getX());
            return dx != 0 ? dx : Integer.compare(a.pos().getZ(), b.pos().getZ());
        });
        this.placements = List.copyOf(sorted);
    }

    public Vec3i size() {
        return this.size;
    }

    public List<Placement> placements() {
        return this.placements;
    }

    /** Total non-air blocks to place. */
    public int blockCount() {
        return this.placements.size();
    }

    /**
     * The shopping list: how many of each item the build consumes, keyed by the item the block drops/places
     * from. Block-entity contents are ignored (MVP). Used for the GUI checklist and chest sourcing.
     */
    public Map<Item, Integer> materials() {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Placement placement : this.placements) {
            Item item = placement.state().getBlock().asItem();
            if (item == null || item == ItemStack.EMPTY.getItem()) {
                continue;
            }
            counts.merge(item, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * A copy of this blueprint rotated about the origin (0,0,0). Lets the player spin the ghost preview
     * before committing. Re-sorting (bottom-up) is preserved by the constructor.
     */
    public Blueprint rotated(Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return this;
        }
        Vec3i rotatedSize = switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
            default -> this.size;
        };
        int maxX = this.size.getX() - 1;
        int maxZ = this.size.getZ() - 1;
        List<Placement> out = new ArrayList<>(this.placements.size());
        for (Placement placement : this.placements) {
            BlockPos p = placement.pos();
            BlockPos rp = switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(maxZ - p.getZ(), p.getY(), p.getX());
                case CLOCKWISE_180 -> new BlockPos(maxX - p.getX(), p.getY(), maxZ - p.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(p.getZ(), p.getY(), maxX - p.getX());
                default -> p;
            };
            out.add(new Placement(rp, placement.state().rotate(rotation)));
        }
        return new Blueprint(rotatedSize, out);
    }
}
