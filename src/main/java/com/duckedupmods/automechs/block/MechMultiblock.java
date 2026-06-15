package com.duckedupmods.automechs.block;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.block.entity.MechAssemblyStructureBlockEntity;
import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared geometry + form/dismantle logic for the Robot Builder's multiblock — a 3-wide × 2-deep × 2-tall
 * printing capsule. The controller block (the Mech Assembly Bench) sits at the front-centre-bottom; the
 * other 11 cells are invisible {@link MechAssemblyStructureBlock filler} blocks pointing back at it.
 *
 * <p>The footprint is not square, so cell offsets are defined in the controller's LOCAL frame (X = right,
 * Z = backward/into the machine, Y = up) and rotated into world space by the controller's facing. All
 * mutations are server-side; a {@link #DISMANTLING} re-entrancy guard stops the cascading break from
 * recursing.</p>
 */
public final class MechMultiblock {

    /** How far in front of the controller a finished mech emerges (always just outside the front face). */
    public static final int SPAWN_DISTANCE = 1;
    /** Render/cull padding in blocks around the controller that covers the whole capsule. */
    public static final int RENDER_PAD = 2;

    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);

    private MechMultiblock() {}

    public static boolean isDismantling() {
        return DISMANTLING.get();
    }

    /** Local cells of the capsule: X∈[-1,1] (3 wide), Z∈[0,1] (2 deep, backward), Y∈[0,1] (2 tall). */
    private static List<BlockPos> localCells() {
        List<BlockPos> out = new ArrayList<>(12);
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    out.add(new BlockPos(x, y, z));
                }
            }
        }
        return out;
    }

    /** Filler offsets (every cell except the controller's own 0,0,0) rotated into world space by facing. */
    public static List<BlockPos> fillerOffsets(Direction facing) {
        Vec3i right = facing.getClockWise().getNormal();
        Vec3i back = facing.getOpposite().getNormal();
        List<BlockPos> out = new ArrayList<>(11);
        for (BlockPos local : localCells()) {
            if (local.getX() == 0 && local.getY() == 0 && local.getZ() == 0) {
                continue;
            }
            int wx = right.getX() * local.getX() + back.getX() * local.getZ();
            int wz = right.getZ() * local.getX() + back.getZ() * local.getZ();
            out.add(new BlockPos(wx, local.getY(), wz));
        }
        return out;
    }

    /** True if every filler cell around {@code controller} is free so the capsule can form. */
    public static boolean canForm(Level level, BlockPos controller, Direction facing) {
        for (BlockPos off : fillerOffsets(facing)) {
            if (!level.getBlockState(controller.offset(off)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Place filler blocks into whatever cells are free (skipping occupied ones) and point each at the
     * controller. Forgiving: the builder always places and claims as much of its footprint as it can,
     * rather than refusing when a single cell is blocked.
     */
    public static void form(Level level, BlockPos controller, Direction facing) {
        BlockState filler = ModBlocks.MECH_ASSEMBLY_STRUCTURE.get().defaultBlockState();
        List<BlockPos> placed = new ArrayList<>(11);
        for (BlockPos off : fillerOffsets(facing)) {
            BlockPos p = controller.offset(off);
            if (!level.getBlockState(p).canBeReplaced()) {
                continue;
            }
            level.setBlock(p, filler, Block.UPDATE_ALL);
            if (level.getBlockEntity(p) instanceof MechAssemblyStructureBlockEntity be) {
                be.setController(controller);
            }
            placed.add(p);
        }
        // Each filler cell exposes the controller's energy capability — but only AFTER setController ran
        // above. The setBlock() that placed it already fired the adjacent conduits' updateShape, which
        // queried the (not-yet-linked) capability and saw null, so they drew themselves disconnected.
        // Now that every controller link exists, refresh the cells so neighbouring conduits re-evaluate
        // and reconnect. (Also covers re-placing a builder where a conduit was already touching its edge.)
        for (BlockPos p : placed) {
            refreshNeighborConnections(level, p);
        }
    }

    /**
     * Re-run the shape update for every block touching {@code pos} so capability-aware neighbours (power
     * conduits) recompute their connection now that this cell's energy capability is live. Mirrors what
     * the engine does on a block change, but invoked a tick-fraction later than the initial placement.
     */
    private static void refreshNeighborConnections(Level level, BlockPos pos) {
        level.invalidateCapabilities(pos);
        BlockState self = level.getBlockState(pos);
        for (Direction dir : Direction.values()) {
            BlockPos np = pos.relative(dir);
            BlockState neighbor = level.getBlockState(np);
            BlockState updated = neighbor.updateShape(dir.getOpposite(), self, level, np, pos);
            if (updated != neighbor) {
                level.setBlock(np, updated, Block.UPDATE_ALL);
            }
        }
    }

    /** Remove the filler cells of the structure owned by {@code controller}. Drops nothing; never recurses. */
    public static void removeStructure(Level level, BlockPos controller, Direction facing) {
        if (DISMANTLING.get()) {
            return;
        }
        DISMANTLING.set(true);
        try {
            for (BlockPos off : fillerOffsets(facing)) {
                BlockPos p = controller.offset(off);
                if (level.getBlockState(p).is(ModBlocks.MECH_ASSEMBLY_STRUCTURE.get())) {
                    level.removeBlock(p, false);
                }
            }
        } finally {
            DISMANTLING.set(false);
        }
    }
}
