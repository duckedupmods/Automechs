package com.duckedupmods.automechs.entity.ai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.schematic.Blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The Builder mech's behavior: construct a loaded schematic ({@link Blueprint}) in the world. The structure
 * is pinned to a player-placed anchor + rotation; the mech places blocks bottom-up, standing on solid ground
 * or the part it has already built and reaching over to each cell, drawing materials from any chest within
 * range (it walks over, restocks, and carries on). Each block costs Forge Energy. With block-breaking enabled
 * it clears whatever obstructs the schematic's own footprint (dropping it); cells it can't reach yet are
 * deferred and retried after their neighbours give it footing. It pauses when out of materials or power.
 * Server-only.
 */
public class MechBuildGoal extends Goal {

    private static final double BUILD_REACH_SQR = 25.0D;  // place within ~5 blocks (a builder "projects" blocks)
    private static final double STAND_REACH_SQR = 12.25D; // pick footing within ~3.5 blocks so it's safely in reach
    private static final double CHEST_REACH_SQR = 16.0D;  // restock within ~4 blocks of a chest
    private static final int STUCK_LIMIT = 50;            // ticks of no progress before a recovery hop to the stand spot
    private static final int MAX_PASSES = 6;              // retry deferred (then-unreachable) cells this many times
    private static final int STOCK = 64;                  // per-material amount kept on hand
    private static final int SCAN_WINDOW = 4096;          // max placements examined per tick
    private static final long SOURCE_REFRESH = 40L;       // ticks between chest re-scans
    private static final int STAND_SEARCH = 4;            // horizontal radius searched for a footing spot near a cell
    private static final long MAX_SOURCE_SCAN = 250_000L; // cap on positions scanned for chests around a build

    private static final int ACT_PLACE = 0;
    private static final int ACT_FETCH = 1;
    private static final int ACT_DONE = 2;

    private static final int W_MOVING = 0;  // still travelling to / charging at the cell
    private static final int W_ACTED = 1;   // performed the action this tick
    private static final int W_BLOCKED = 2; // no footing to reach the cell

    private static final long MAX_EXCAVATE = 200_000L; // cap on interior cells listed for excavation

    private final MiningMech mech;
    private final PathNavigation navigation;

    // Rotated, bottom-up placement plan, cached per (blueprint, anchor, rotation).
    private List<Blueprint.Placement> plan;
    private Set<Item> materialItems;
    private Blueprint planSource;
    private BlockPos planAnchor;
    private Vec3i planSize;
    private Rotation planRot;
    private final Set<Integer> skipped = new HashSet<>();   // permanently un-buildable (unbreakable / walled in)
    private final Set<Integer> deferred = new HashSet<>();  // can't reach yet — retried next pass
    private int cursor;
    private int passCount;
    private boolean announced;

    // Excavation: interior/empty cells (relative positions) that should be cleared of terrain, top-down.
    private List<BlockPos> excavateList;
    private int excavateCursor;

    // Current placement target (index into plan), the spot to stand at to reach it, and the fetch goal.
    private int targetIndex = -1;
    private BlockPos targetStand;
    private int workProgress;
    private Item fetchItem;

    // Navigation smoothing + stuck recovery: don't re-issue a path every tick, and hop free if blocked.
    private BlockPos navTarget;
    private int stuckTicks;
    private double lastDistSqr = Double.MAX_VALUE;

    // Cached in-range container positions.
    private List<BlockPos> sources;
    private long sourcesAge = Long.MIN_VALUE;

    public MechBuildGoal(MiningMech mech) {
        this.mech = mech;
        this.navigation = mech.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return Config.ENABLE_AUTOMECHS.get()
                && mech.getRole() == MechRole.BUILDING
                && mech.isWorkEnabled()
                && mech.hasSchematic()
                && mech.getBuildAnchor() != null
                // No power → don't even start (and stop mid-build): otherwise the bot just runs around
                // trying to place blocks it can never afford. Needs enough FE for at least one block.
                && mech.getEnergy().getEnergyStored() >= mech.getMineCost();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.targetIndex = -1;
        this.targetStand = null;
        this.fetchItem = null;
        this.workProgress = 0;
        this.navTarget = null;
        this.stuckTicks = 0;
        this.lastDistSqr = Double.MAX_VALUE;
        this.navigation.stop();
    }

    /** Ticks to place one block: a snappy base, shortened by each Speed module (minimum 1). */
    private int buildTicks() {
        return Math.max(1, Config.BUILD_TICKS.get() - mech.getUpgradeLevel(UpgradeType.SPEED));
    }

    @Override
    public void tick() {
        if (!(mech.level() instanceof ServerLevel level)) {
            return;
        }
        ensurePlan();
        if (this.plan == null || this.planAnchor == null) {
            this.navigation.stop();
            return;
        }

        // Phase 1: dig out the interior terrain so the structure isn't buried in the ground.
        if (Config.BUILD_EXCAVATE.get() && this.targetIndex < 0 && doExcavate(level)) {
            return;
        }

        // Phase 2: continue an in-progress placement.
        if (this.targetIndex >= 0) {
            doPlace(level);
            return;
        }

        switch (planNextAction(level)) {
            case ACT_PLACE -> doPlace(level); // targetIndex now set
            case ACT_FETCH -> fetchMaterial(level);
            default -> {
                // Nothing left from the cursor. If cells were deferred (couldn't be reached on this pass),
                // their neighbours may have given them footing now — clear and sweep again, up to a cap.
                if (!this.deferred.isEmpty() && this.passCount < MAX_PASSES) {
                    this.deferred.clear();
                    this.cursor = 0;
                    this.passCount++;
                } else {
                    this.navigation.stop();
                    announceComplete();
                }
            }
        }
    }

    /** (Re)build the rotated placement plan when the schematic, anchor, or rotation changes. */
    private void ensurePlan() {
        Blueprint base = mech.getBlueprint();
        if (base == null) {
            this.plan = null;
            return;
        }
        Rotation rot = mech.getBuildRotation();
        BlockPos anchor = mech.getBuildAnchor();
        boolean changed = this.plan == null || base != this.planSource || rot != this.planRot
                || anchor == null || !anchor.equals(this.planAnchor);
        if (changed) {
            this.planSource = base;
            this.planRot = rot;
            this.planAnchor = anchor;
            Blueprint rotated = base.rotated(rot);
            this.planSize = rotated.size();
            this.plan = rotated.placements();
            this.materialItems = new HashSet<>(rotated.materials().keySet());
            this.skipped.clear();
            this.deferred.clear();
            this.cursor = 0;
            this.passCount = 0;
            this.targetIndex = -1;
            this.targetStand = null;
            this.fetchItem = null;
            this.announced = false;
            buildExcavationList();
            this.excavateCursor = 0;
        }
    }

    /** List the interior/empty cells (every box cell with no schematic block), top-down, for excavation. */
    private void buildExcavationList() {
        this.excavateList = new ArrayList<>();
        int sx = this.planSize.getX();
        int sy = this.planSize.getY();
        int sz = this.planSize.getZ();
        if ((long) sx * sy * sz > MAX_EXCAVATE) {
            return; // too large — skip excavation (placement still breaks obstructions where blocks go)
        }
        Set<Long> filled = new HashSet<>();
        for (Blueprint.Placement p : this.plan) {
            filled.add(BlockPos.asLong(p.pos().getX(), p.pos().getY(), p.pos().getZ()));
        }
        for (int y = sy - 1; y >= 0; y--) {       // top-down
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    if (!filled.contains(BlockPos.asLong(x, y, z))) {
                        this.excavateList.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    /**
     * Scan from the cursor for the next thing to do: a cell we can place now ({@link #ACT_PLACE}, target set),
     * a material we must fetch ({@link #ACT_FETCH}, fetchItem set), or nothing left ({@link #ACT_DONE}).
     */
    private int planNextAction(ServerLevel level) {
        boolean canBreak = Config.BUILD_BREAK_BLOCKS.get();
        Item needed = null;
        int n = this.plan.size();
        int scanned = 0;
        for (int i = this.cursor; i < n; i++) {
            if (this.skipped.contains(i) || this.deferred.contains(i)) {
                if (i == this.cursor) {
                    this.cursor++;
                }
                continue;
            }
            Blueprint.Placement p = this.plan.get(i);
            BlockPos wp = this.planAnchor.offset(p.pos());
            BlockState cur = level.getBlockState(wp);
            if (cur == p.state()) { // already built
                if (i == this.cursor) {
                    this.cursor++;
                }
                continue;
            }
            Item item = p.state().getBlock().asItem();
            if (item == null || item == ItemStack.EMPTY.getItem()) { // no placeable item — skip
                if (i == this.cursor) {
                    this.cursor++;
                }
                continue;
            }
            boolean occupied = !isReplaceable(cur);
            if (occupied && !canClear(level, wp, cur, canBreak)) {
                // Obstructed and we can't (or won't) clear it — leave it, advance past.
                if (i == this.cursor) {
                    this.cursor++;
                }
                continue;
            }
            // Actionable: place it (and break first if occupied). Need the material to do so.
            if (carriesItemFor(p.state())) {
                this.targetIndex = i;
                this.targetStand = standSpotNear(level, wp);
                this.workProgress = 0;
                this.stuckTicks = 0;
                this.lastDistSqr = Double.MAX_VALUE;
                return ACT_PLACE;
            }
            if (needed == null) {
                needed = item;
            }
            if (++scanned > SCAN_WINDOW) {
                break;
            }
        }
        if (needed != null) {
            this.fetchItem = needed;
            return ACT_FETCH;
        }
        return ACT_DONE;
    }

    /** Get in reach of the target cell, then clear any obstruction and place the block (charged + cadence-gated). */
    private void doPlace(ServerLevel level) {
        if (this.targetIndex < 0 || this.targetIndex >= this.plan.size()) {
            this.targetIndex = -1;
            return;
        }
        Blueprint.Placement p = this.plan.get(this.targetIndex);
        BlockPos wp = this.planAnchor.offset(p.pos());
        BlockState state = p.state();
        BlockState cur = level.getBlockState(wp);
        if (cur == state) { // already done (by us or otherwise)
            advanceCursorPast(this.targetIndex);
            clearTarget();
            return;
        }
        if (!carriesItemFor(state)) {
            clearTarget(); // lost the material — re-plan (will fetch)
            return;
        }
        if (!isReplaceable(cur) && !canClear(level, wp, cur, Config.BUILD_BREAK_BLOCKS.get())) {
            this.skipped.add(this.targetIndex); // can't (or shouldn't) clear it — leave it
            clearTarget();
            return;
        }
        int result = serviceCell(level, wp, () -> {
            if (!isReplaceable(level.getBlockState(wp))) {
                level.destroyBlock(wp, true, mech); // clear the obstruction first; place on a later tick
            } else if (consumeItemFor(state)) {
                level.setBlock(wp, state, Block.UPDATE_ALL);
                SoundType sound = state.getSoundType();
                level.playSound(null, wp, sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                advanceCursorPast(this.targetIndex);
                clearTarget();
            }
        });
        if (result == W_BLOCKED) {
            this.deferred.add(this.targetIndex); // no footing yet — retried after neighbours give it footing
            clearTarget();
        }
    }

    /** Phase 1: clear interior terrain from the structure's empty cells (top-down). Returns true while busy. */
    private boolean doExcavate(ServerLevel level) {
        if (this.excavateList == null) {
            return false;
        }
        while (this.excavateCursor < this.excavateList.size()) {
            BlockPos wp = this.planAnchor.offset(this.excavateList.get(this.excavateCursor));
            BlockState cur = level.getBlockState(wp);
            if (isReplaceable(cur) || !canClear(level, wp, cur, true)) {
                nextExcavationCell(); // already clear, or a protected block entity / unbreakable
                continue;
            }
            int result = serviceCell(level, wp, () -> level.destroyBlock(wp, true, mech));
            if (result != W_MOVING) {
                nextExcavationCell(); // cleared it, or no footing to reach it — move on (best effort)
            }
            return true;
        }
        return false;
    }

    private void nextExcavationCell() {
        this.excavateCursor++;
        this.targetStand = null;
        this.workProgress = 0;
        this.navTarget = null;
        this.stuckTicks = 0;
        this.lastDistSqr = Double.MAX_VALUE;
    }

    /**
     * Move to a footing spot beside {@code wp} and, once in reach + charged + cadence elapsed, run {@code act}.
     * Footing is a solid stand spot near the cell (reach over gaps from the edge / from what's already built);
     * if there's none, returns {@link #W_BLOCKED}. If pathing stalls, it hops onto the footing spot.
     */
    private int serviceCell(ServerLevel level, BlockPos wp, Runnable act) {
        Vec3 c = Vec3.atCenterOf(wp);
        mech.getLookControl().setLookAt(c.x, c.y, c.z);
        if (mech.distanceToSqr(c) > BUILD_REACH_SQR) {
            if (this.targetStand == null) {
                this.targetStand = standSpotNear(level, wp);
            }
            if (this.targetStand == null) {
                return W_BLOCKED;
            }
            Vec3 g = Vec3.atCenterOf(this.targetStand);
            if (!this.targetStand.equals(this.navTarget) || this.navigation.isDone()) {
                this.navigation.moveTo(g.x, g.y, g.z, 1.1D);
                this.navTarget = this.targetStand;
            }
            double distSqr = mech.distanceToSqr(g);
            if (distSqr < this.lastDistSqr - 0.02D) {
                this.lastDistSqr = distSqr;
                this.stuckTicks = 0;
            } else if (++this.stuckTicks >= STUCK_LIMIT) {
                this.navigation.stop();
                mech.teleportTo(this.targetStand.getX() + 0.5D, this.targetStand.getY(), this.targetStand.getZ() + 0.5D);
                this.stuckTicks = 0;
                this.lastDistSqr = Double.MAX_VALUE;
                this.navTarget = null;
            }
            return W_MOVING;
        }
        // In reach — plant and work through everything reachable from here.
        this.navigation.stop();
        this.navTarget = null;
        this.stuckTicks = 0;
        this.lastDistSqr = Double.MAX_VALUE;

        int cost = mech.getMineCost();
        if (mech.getEnergy().getEnergyStored() < cost) {
            return W_MOVING; // out of power → pause
        }
        if (++this.workProgress < buildTicks()) {
            return W_MOVING;
        }
        this.workProgress = 0;
        if (!mech.consumeEnergy(cost)) {
            return W_MOVING;
        }
        act.run();
        return W_ACTED;
    }

    private void clearTarget() {
        this.targetIndex = -1;
        this.targetStand = null;
        this.navTarget = null;
        this.stuckTicks = 0;
        this.lastDistSqr = Double.MAX_VALUE;
    }

    private void advanceCursorPast(int index) {
        if (index == this.cursor) {
            this.cursor++;
        }
    }

    /**
     * The nearest position the mech can stand on (air feet+head, solid below) from which {@code cell} is within
     * reach — searched over a small box so it can stand on the part it has already built and reach across a gap.
     */
    private BlockPos standSpotNear(ServerLevel level, BlockPos cell) {
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;
        for (int dy = 2; dy >= -2; dy--) {
            for (int dx = -STAND_SEARCH; dx <= STAND_SEARCH; dx++) {
                for (int dz = -STAND_SEARCH; dz <= STAND_SEARCH; dz++) {
                    BlockPos feet = cell.offset(dx, dy, dz);
                    if (feet.distSqr(cell) > STAND_REACH_SQR || !isStandable(level, feet)) {
                        continue;
                    }
                    double d = mech.distanceToSqr(Vec3.atCenterOf(feet));
                    if (d < bestSqr) {
                        bestSqr = d;
                        best = feet;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !below.isAir() && !below.canBeReplaced();
    }

    /**
     * Whether an obstructing block may be cleared to make way for the schematic: breaking must be enabled, the
     * block must be breakable (not bedrock), and it must NOT be a block entity — so the mech never eats the
     * player's material chests, the charging pad, or any machine sitting in the footprint.
     */
    private boolean canClear(ServerLevel level, BlockPos pos, BlockState state, boolean canBreak) {
        return canBreak
                && state.getDestroySpeed(level, pos) >= 0.0F
                && level.getBlockEntity(pos) == null;
    }

    // --- Material sourcing ---------------------------------------------------

    /**
     * Walk to the nearest chest that holds <em>any</em> material the build still needs and restock there. We
     * never fixate on one specific block type — if a single material is missing from the chests, the mech
     * still gathers everything else and builds what it can, instead of stalling and picking up nothing.
     */
    private void fetchMaterial(ServerLevel level) {
        this.fetchItem = null;
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;
        for (BlockPos pos : sources(level)) {
            IItemHandler handler = handlerAt(level, pos);
            if (handler != null && hasNeededMaterial(handler)) {
                double d = mech.distanceToSqr(Vec3.atCenterOf(pos));
                if (d < bestSqr) {
                    bestSqr = d;
                    best = pos;
                }
            }
        }
        if (best == null) {
            // No chest in the footprint has anything we still need — go to the build site and wait there
            // (the player may add materials), rather than sit wherever we are.
            Vec3 site = Vec3.atCenterOf(this.planAnchor);
            if (mech.distanceToSqr(site) > 36.0D) {
                this.navigation.moveTo(site.x, site.y, site.z, 1.0D);
            } else {
                this.navigation.stop();
            }
            return;
        }
        Vec3 c = Vec3.atCenterOf(best);
        mech.getLookControl().setLookAt(c.x, c.y, c.z);
        if (mech.distanceToSqr(c) > CHEST_REACH_SQR) {
            this.navigation.moveTo(c.x, c.y, c.z, 1.0D);
            return;
        }
        this.navigation.stop();
        restock(level, handlerAt(level, best));
        this.fetchItem = null;
    }

    /** Whether this chest holds any build material the mech is short on (so it's worth visiting). */
    private boolean hasNeededMaterial(IItemHandler chest) {
        if (this.materialItems == null) {
            return false;
        }
        IItemHandler inventory = mech.getInventory();
        for (Item item : this.materialItems) {
            if (countItem(inventory, item) < STOCK && containsItem(chest, item)) {
                return true;
            }
        }
        return false;
    }

    /** Top every still-needed material up to {@link #STOCK} from this chest (as space allows). */
    private void restock(ServerLevel level, IItemHandler chest) {
        if (chest == null || this.materialItems == null) {
            return;
        }
        IItemHandler inventory = mech.getInventory();
        for (Item item : this.materialItems) {
            int have = countItem(inventory, item);
            if (have >= STOCK) {
                continue;
            }
            pull(chest, inventory, item, STOCK - have);
        }
    }

    /**
     * Container positions to pull materials from, cached briefly. Scans the <em>whole build footprint</em>
     * (plus a margin) — not just around the mech — so the player can drop a chest anywhere in/next to the
     * structure and the mech will find and walk to it. Falls back to a box around the mech for builds too big
     * to scan whole.
     */
    private List<BlockPos> sources(ServerLevel level) {
        long now = level.getGameTime();
        if (this.sources != null && now - this.sourcesAge < SOURCE_REFRESH) {
            return this.sources;
        }
        this.sourcesAge = now;
        int margin = Config.BUILD_MATERIAL_RANGE.get();

        BlockPos lo;
        BlockPos hi;
        long volume = this.planSize == null ? Long.MAX_VALUE
                : (long) (this.planSize.getX() + 2L * margin)
                        * (this.planSize.getY() + 2L * margin)
                        * (this.planSize.getZ() + 2L * margin);
        if (this.planAnchor != null && this.planSize != null && volume <= MAX_SOURCE_SCAN) {
            lo = this.planAnchor.offset(-margin, -margin, -margin);
            hi = this.planAnchor.offset(this.planSize.getX() + margin, this.planSize.getY() + margin,
                    this.planSize.getZ() + margin);
        } else {
            BlockPos at = mech.blockPosition();
            lo = at.offset(-margin, -margin, -margin);
            hi = at.offset(margin, margin, margin);
        }

        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = lo.getX(); x <= hi.getX(); x++) {
            for (int y = lo.getY(); y <= hi.getY(); y++) {
                for (int z = lo.getZ(); z <= hi.getZ(); z++) {
                    m.set(x, y, z);
                    if (level.getBlockEntity(m) != null && handlerAt(level, m) != null) {
                        found.add(m.immutable());
                    }
                }
            }
        }
        this.sources = found;
        return found;
    }

    private void announceComplete() {
        if (this.announced || this.cursor < this.plan.size()) {
            return;
        }
        this.announced = true;
        LivingEntity owner = mech.getOwner();
        if (owner instanceof net.minecraft.world.entity.player.Player player) {
            player.displayClientMessage(Component.translatable(
                    "message.automechs.build_done", mech.getSchematicName()), true);
        }
    }

    // --- Item helpers --------------------------------------------------------

    private boolean carriesItemFor(BlockState state) {
        Block block = state.getBlock();
        IItemHandler inventory = mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeItemFor(BlockState state) {
        Block block = state.getBlock();
        IItemHandler inventory = mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                return !inventory.extractItem(slot, 1, false).isEmpty();
            }
        }
        return false;
    }

    private static boolean containsItem(IItemHandler handler, Item item) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private static int countItem(IItemHandler handler, Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Move up to {@code max} of {@code item} from chest into the mech's inventory. */
    private static void pull(IItemHandler chest, IItemHandler mech, Item item, int max) {
        int pulled = 0;
        for (int slot = 0; slot < chest.getSlots() && pulled < max; slot++) {
            ItemStack stack = chest.getStackInSlot(slot);
            if (stack.getItem() != item) {
                continue;
            }
            ItemStack sample = chest.extractItem(slot, Math.min(stack.getCount(), max - pulled), true);
            if (sample.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(mech, sample.copy(), false);
            int moved = sample.getCount() - remainder.getCount();
            if (moved > 0) {
                chest.extractItem(slot, moved, false);
                pulled += moved;
            }
        }
    }

    private static IItemHandler handlerAt(ServerLevel level, BlockPos pos) {
        return Capabilities.ItemHandler.BLOCK.getCapability(level, pos, null, null, (Direction) null);
    }

    /** Air or vanilla-replaceable (tall grass, water, snow layer…) — never overwrite a solid block. */
    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
