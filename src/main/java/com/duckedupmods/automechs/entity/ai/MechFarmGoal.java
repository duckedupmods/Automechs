package com.duckedupmods.automechs.entity.ai;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The Farming mech's behavior over a flat field (one block tall): plant seeds on empty farmland, harvest
 * fully-grown crops and replant them, and — only as an accelerator — bone-meal immature crops with the
 * Fertilizer Dispenser module. Field work uses only what the mech carries; when it fills up, runs out of
 * seeds/bone meal, or sits idle with produce, it walks to the linked chest to deposit and restock. If the
 * chest is destroyed the link is cleared. Planting/harvesting/waiting all work without bone meal — bone
 * meal just speeds growth. The field is the box placed with the GUI "Set Area" button, or a small patch
 * around the mech if none is set. Server-only.
 */
public class MechFarmGoal extends Goal {

    private static final double REACH_SQR = 9.0D; // act within 3 blocks
    private static final int BONEMEAL_PER_CROP = 4; // max applications to ripen one crop (stops early if it ripens)
    private static final int DEPOSIT_DELAY_TICKS = 100; // ~5s carrying produce → take it to the chest
    private static final int WORKING_STOCK = 64; // seeds / bone meal kept for self-sufficiency (deposit the rest)

    private static final int ACTION_HARVEST = 0;
    private static final int ACTION_PLANT = 1;

    private final MiningMech mech;
    private final PathNavigation navigation;

    private BlockPos target;
    private int targetAction;
    private int workProgress;
    private int fertilizeProgress;
    private int produceTicks; // how long we've been carrying depositable produce

    // Field scan results, refreshed on a short cadence (not every tick — see SCAN_INTERVAL).
    private static final int SCAN_INTERVAL = 4; // ticks between full field rescans (≈0.2s latency)
    private int scanCooldown;
    private BlockPos scanMature;
    private BlockPos scanEmpty;
    private boolean scanImmature;

    public MechFarmGoal(MiningMech mech) {
        this.mech = mech;
        this.navigation = mech.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** The side length (in blocks) of a farm field at the given selected size tier: 2×2 … 8×8. */
    public static int farmSide(int tier) {
        return 2 + Math.max(0, tier);
    }

    @Override
    public boolean canUse() {
        return Config.ENABLE_AUTOMECHS.get()
                && mech.getRole() == MechRole.FARMING
                && mech.isWorkEnabled();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        this.target = null;
        this.workProgress = 0;
        this.fertilizeProgress = 0;
        this.produceTicks = 0;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (!(mech.level() instanceof ServerLevel level)) {
            return;
        }
        // Drop a stale link if the chest was destroyed (only when the position is loaded).
        validateDeposit(level);
        maybeScanField(level);

        IItemHandler chest = depositChest(level);
        boolean hasProduce = carriesProduce();
        this.produceTicks = hasProduce ? this.produceTicks + 1 : 0;

        // 1. Full up → must offload before we can store any more.
        if (chest != null && isInventoryFull()) {
            serviceChest(level, chest);
            return;
        }
        // 2. Harvest ripe crops (uses no resources).
        if (this.scanMature != null) {
            workToward(level, this.scanMature, ACTION_HARVEST);
            return;
        }
        // 3. Periodically take produce back to the chest — covers "idle for a while" and steady drop-off.
        if (chest != null && hasProduce && this.produceTicks >= DEPOSIT_DELAY_TICKS) {
            serviceChest(level, chest);
            return;
        }
        // 4. Restock from the chest when we lack a resource the field needs and the chest has it.
        if (chest != null && needsRestock(chest)) {
            serviceChest(level, chest);
            return;
        }
        // 5. Plant seeds we carry onto empty farmland.
        if (this.scanEmpty != null && carries(MechFarmGoal::isSeed)) {
            workToward(level, this.scanEmpty, ACTION_PLANT);
            return;
        }
        // 6. Fertilize immature crops with bone meal we carry (on its own cadence).
        if (mech.hasUpgrade(UpgradeType.FERTILIZER) && this.scanImmature && carries(MechFarmGoal::isBoneMeal)) {
            if (++this.fertilizeProgress >= mech.getDigTicks()) {
                this.fertilizeProgress = 0;
                fertilizeArea(level);
            }
            return;
        }
        // 7. Nothing to do: stand by and wait for the crops to grow.
        this.target = null;
        this.navigation.stop();
    }

    /** Walk to {@code pos} and, once in reach and charged, perform the action there. */
    private void workToward(ServerLevel level, BlockPos pos, int action) {
        if (!pos.equals(this.target)) {
            this.target = pos;
            this.targetAction = action;
            this.workProgress = 0;
        }
        Vec3 center = Vec3.atCenterOf(pos);
        mech.getLookControl().setLookAt(center.x, center.y, center.z);
        if (mech.distanceToSqr(center) > REACH_SQR) {
            this.workProgress = 0;
            this.navigation.moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        this.navigation.stop();

        int cost = mech.getMineCost();
        if (mech.getEnergy().getEnergyStored() < cost) {
            return; // out of power
        }
        if (++this.workProgress < mech.getDigTicks()) {
            return;
        }
        this.workProgress = 0;
        if (mech.consumeEnergy(cost)) {
            if (action == ACTION_PLANT) {
                plant(level, pos);
            } else {
                harvest(level, pos);
            }
        }
        this.target = null;
    }

    /** Harvest a mature crop into the mech's inventory and replant it at age 0. */
    private void harvest(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ItemStack tool = new ItemStack(Items.NETHERITE_HOE);
        int fortune = mech.getUpgradeLevel(UpgradeType.FORTUNE);
        if (fortune > 0) {
            tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.FORTUNE), fortune);
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), mech, tool);
        for (ItemStack drop : drops) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(mech.getInventory(), drop, false);
            if (!remainder.isEmpty()) {
                Block.popResource(level, mech.blockPosition(), remainder);
            }
        }
        level.levelEvent(2001, pos, Block.getId(state)); // break particles + sound

        BlockState replant = replantState(state);
        if (replant != null) {
            level.setBlock(pos, replant, Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
    }

    /** Plant a seed the mech carries onto an empty farmland cell. */
    private void plant(ServerLevel level, BlockPos pos) {
        IItemHandler inventory = mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock crop) {
                BlockState planted = crop.defaultBlockState();
                if (planted.canSurvive(level, pos)) {
                    level.setBlock(pos, planted, Block.UPDATE_ALL);
                    inventory.extractItem(slot, 1, false);
                    return;
                }
            }
        }
    }

    /**
     * Bone-meal every immature crop across the field in one pass (Fertilizer Dispenser), using only the
     * bone meal the mech carries. Each crop gets at most {@link #BONEMEAL_PER_CROP} applications but the
     * loop re-checks maturity each time, so a crop that ripens early gets no extra (no waste). The pass is
     * capped at a size-scaled budget; it stops early if the mech runs out of bone meal or power.
     */
    private void fertilizeArea(ServerLevel level) {
        int cost = mech.getMineCost();
        int budget = 8 + mech.getEffectiveRangeTier() * 6;
        BlockPos min = areaMin();
        BlockPos max = areaMax();
        for (int y = max.getY(); y >= min.getY() && budget > 0; y--) {
            for (int x = min.getX(); x <= max.getX() && budget > 0; x++) {
                for (int z = min.getZ(); z <= max.getZ() && budget > 0; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BonemealableBlock bonemealable)) {
                        continue;
                    }
                    int applied = 0;
                    while (applied < BONEMEAL_PER_CROP && budget > 0 && isFertilizable(level, pos, state)) {
                        if (mech.getEnergy().getEnergyStored() < cost || !mech.consumeEnergy(cost)) {
                            return;
                        }
                        if (!extractMatching(mech.getInventory(), MechFarmGoal::isBoneMeal)) {
                            return; // out of carried bone meal
                        }
                        bonemealable.performBonemeal(level, level.getRandom(), pos, state);
                        level.levelEvent(1505, pos, 0); // bonemeal particles
                        state = level.getBlockState(pos);
                        applied++;
                        budget--;
                    }
                }
            }
        }
    }

    // --- Chest trips ---------------------------------------------------------

    /** True when the field needs a resource the mech lacks but the chest can supply. */
    private boolean needsRestock(IItemHandler chest) {
        boolean needSeed = this.scanEmpty != null && !carries(MechFarmGoal::isSeed) && contains(chest, MechFarmGoal::isSeed);
        boolean needBone = mech.hasUpgrade(UpgradeType.FERTILIZER) && this.scanImmature
                && !carries(MechFarmGoal::isBoneMeal) && contains(chest, MechFarmGoal::isBoneMeal);
        return needSeed || needBone;
    }

    /** Walk to the linked chest; on arrival, deposit produce and restock seeds + bone meal. */
    private void serviceChest(ServerLevel level, IItemHandler chest) {
        BlockPos deposit = mech.getDepositPos();
        Vec3 center = Vec3.atCenterOf(deposit);
        mech.getLookControl().setLookAt(center.x, center.y, center.z);
        if (mech.distanceToSqr(center) > REACH_SQR) {
            this.navigation.moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        this.navigation.stop();
        this.produceTicks = 0;

        IItemHandler inventory = mech.getInventory();
        depositSurplus(inventory, chest);

        // Top bone meal up to the working stock; only refill seeds when completely out (harvest normally
        // replenishes them). Carrots/potatoes are both seed and produce — the reserve keeps enough to
        // replant while the surplus is deposited.
        if (mech.hasUpgrade(UpgradeType.FERTILIZER)) {
            pullUpTo(chest, inventory, MechFarmGoal::isBoneMeal, WORKING_STOCK);
        }
        if (!carries(MechFarmGoal::isSeed)) {
            pullFromChest(chest, inventory, MechFarmGoal::isSeed, WORKING_STOCK);
        }
    }

    /** Deposit everything except a {@link #WORKING_STOCK} reserve of each seed/bone-meal type. */
    private void depositSurplus(IItemHandler inventory, IItemHandler chest) {
        Map<Item, Integer> reserve = new HashMap<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int keep = keepFromStack(stack, reserve);
            int depositable = stack.getCount() - keep;
            if (depositable <= 0) {
                continue;
            }
            ItemStack portion = stack.copy();
            portion.setCount(depositable);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(chest, portion, false);
            int moved = depositable - remainder.getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
            }
        }
    }

    /** How many of this stack to keep (reserving up to {@link #WORKING_STOCK} per kept item type). */
    private static int keepFromStack(ItemStack stack, Map<Item, Integer> reserve) {
        if (!isKept(stack)) {
            return 0;
        }
        int left = reserve.getOrDefault(stack.getItem(), WORKING_STOCK);
        int keep = Math.min(left, stack.getCount());
        reserve.put(stack.getItem(), left - keep);
        return keep;
    }

    /** Pull from the chest only enough matching items to bring the mech's holding up to {@code target}. */
    private static void pullUpTo(IItemHandler chest, IItemHandler mech, Predicate<ItemStack> match, int target) {
        int need = target - count(mech, match);
        if (need > 0) {
            pullFromChest(chest, mech, match, need);
        }
    }

    private static int count(IItemHandler handler, Predicate<ItemStack> match) {
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (match.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Move up to {@code maxTotal} items matching {@code match} from the chest into the mech. */
    private static void pullFromChest(IItemHandler chest, IItemHandler mech, Predicate<ItemStack> match, int maxTotal) {
        int pulled = 0;
        for (int slot = 0; slot < chest.getSlots() && pulled < maxTotal; slot++) {
            ItemStack stack = chest.getStackInSlot(slot);
            if (!match.test(stack)) {
                continue;
            }
            ItemStack sample = chest.extractItem(slot, Math.min(stack.getCount(), maxTotal - pulled), true);
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

    /** Clear the deposit link if its block is loaded but no longer a container (chest destroyed). */
    private void validateDeposit(ServerLevel level) {
        BlockPos deposit = mech.getDepositPos();
        if (deposit == null || !level.isLoaded(deposit)) {
            return;
        }
        if (Capabilities.ItemHandler.BLOCK.getCapability(level, deposit, null, null, (Direction) null) == null) {
            mech.setDepositPos(null);
        }
    }

    private IItemHandler depositChest(ServerLevel level) {
        BlockPos deposit = mech.getDepositPos();
        if (deposit == null) {
            return null;
        }
        return Capabilities.ItemHandler.BLOCK.getCapability(level, deposit, null, null, (Direction) null);
    }

    // --- Field scan ----------------------------------------------------------

    /**
     * Rescan on a short cadence rather than every tick (a field can be ≈200 cells; most ticks it just sits
     * growing). Forces an immediate rescan when a cached harvest/plant target was just consumed — an O(1)
     * recheck — so the action loop never paths to a stale target.
     */
    private void maybeScanField(ServerLevel level) {
        boolean staleTarget =
                (this.scanMature != null && !isMature(level, this.scanMature, level.getBlockState(this.scanMature)))
                || (this.scanEmpty != null && !isPlantable(level, this.scanEmpty, level.getBlockState(this.scanEmpty)));
        if (--this.scanCooldown <= 0 || staleTarget) {
            scanField(level);
            this.scanCooldown = SCAN_INTERVAL;
        }
    }

    /** Single pass over the field: record a ripe crop, an empty plantable cell, and any immature crop. */
    private void scanField(ServerLevel level) {
        this.scanMature = null;
        this.scanEmpty = null;
        this.scanImmature = false;

        BlockPos min = areaMin();
        BlockPos max = areaMax();
        for (int y = max.getY(); y >= min.getY(); y--) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (this.scanMature == null && isMature(level, pos, state)) {
                        this.scanMature = pos;
                    } else if (isFertilizable(level, pos, state)) {
                        this.scanImmature = true;
                    } else if (this.scanEmpty == null && isPlantable(level, pos, state)) {
                        this.scanEmpty = pos;
                    }
                }
            }
        }
    }

    /** A crop ready to harvest: a max-age {@link CropBlock}, ripe nether wart, or a tagged crop. */
    private boolean isMature(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (state.is(ModTags.Blocks.MECH_HARVESTABLE) && block instanceof BonemealableBlock bonemealable) {
            return !bonemealable.isValidBonemealTarget(level, pos, state);
        }
        return false;
    }

    /** An immature crop that bone meal would advance. */
    private boolean isFertilizable(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return !crop.isMaxAge(state);
        }
        if (state.is(ModTags.Blocks.MECH_HARVESTABLE) && block instanceof BonemealableBlock bonemealable) {
            return bonemealable.isValidBonemealTarget(level, pos, state);
        }
        return false;
    }

    /** An empty cell with farmland beneath it, ready for a seed. */
    private boolean isPlantable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() && level.getBlockState(pos.below()).getBlock() instanceof FarmBlock;
    }

    /** The age-0 state to replant after harvest, or null to just clear the block. */
    private static BlockState replantState(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        if (block instanceof NetherWartBlock) {
            return state.setValue(NetherWartBlock.AGE, 0);
        }
        return null;
    }

    // --- Field bounds --------------------------------------------------------

    private BlockPos areaMin() {
        if (mech.hasWorkArea()) {
            return mech.getAreaMin();
        }
        int side = farmSide(mech.getEffectiveRangeTier());
        int lo = (side - 1) / 2;
        BlockPos at = mech.blockPosition();
        return new BlockPos(at.getX() - lo, at.getY() - 1, at.getZ() - lo);
    }

    private BlockPos areaMax() {
        if (mech.hasWorkArea()) {
            return mech.getAreaMax();
        }
        int side = farmSide(mech.getEffectiveRangeTier());
        int hi = side / 2;
        BlockPos at = mech.blockPosition();
        return new BlockPos(at.getX() + hi, at.getY() + 1, at.getZ() + hi);
    }

    // --- Item helpers --------------------------------------------------------

    private static boolean isBoneMeal(ItemStack stack) {
        return stack.is(Items.BONE_MEAL);
    }

    private static boolean isSeed(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;
    }

    /** Items the mech keeps (never deposits) so it stays self-sufficient: seeds + bone meal. */
    private static boolean isKept(ItemStack stack) {
        return isBoneMeal(stack) || isSeed(stack);
    }

    private boolean carries(Predicate<ItemStack> match) {
        return contains(mech.getInventory(), match);
    }

    /** Whether the mech holds anything depositable — pure produce, or seeds/produce beyond the reserve. */
    private boolean carriesProduce() {
        IItemHandler inventory = mech.getInventory();
        Map<Item, Integer> reserve = new HashMap<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() - keepFromStack(stack, reserve) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(IItemHandler handler, Predicate<ItemStack> match) {
        if (handler == null) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (match.test(handler.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean extractMatching(IItemHandler handler, Predicate<ItemStack> match) {
        if (handler == null) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (match.test(handler.getStackInSlot(slot)) && !handler.extractItem(slot, 1, false).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isInventoryFull() {
        IItemHandler inventory = mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
