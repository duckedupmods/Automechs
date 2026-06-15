package com.duckedupmods.automechs.entity.ai;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The mech's work behavior: mine the assigned area top-down, store drops internally, and deposit to
 * the linked chest when full (or when the area is cleared). It idles in place when out of power — a
 * nearby {@link com.duckedupmods.automechs.block.ChargingPadBlock Charging Pad} tops it back up and
 * work resumes. Runs only on the server; gated by config and the mech having a work order.
 */
public class MechWorkGoal extends Goal {

    private static final double REACH_SQR = 9.0D; // act within 3 blocks

    private final MiningMech mech;
    private final PathNavigation navigation;
    private BlockPos target;
    private int digProgress;

    // Resume cursor for findTarget: the highest Y that may still hold a block. The quarry digs strictly
    // top-down and cleared layers never refill, so we never re-scan the (growing) cleared region above the
    // dig front. Reset when the assigned area changes or the goal restarts.
    private int scanTopY;
    private BlockPos scanMin;
    private BlockPos scanMax;

    public MechWorkGoal(MiningMech mech) {
        this.mech = mech;
        this.navigation = mech.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return Config.ENABLE_AUTOMECHS.get()
                && mech.getRole() == MechRole.MINING
                && mech.isWorkEnabled()
                && mech.hasWorkArea();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        this.target = null;
        this.digProgress = 0;
        this.scanMin = null; // force the resume cursor to reset (re-scan from the top) on next start
        this.navigation.stop();
    }

    @Override
    public void tick() {
        if (!(mech.level() instanceof ServerLevel level)) {
            return;
        }
        // Deposit when full, or when the area is cleared but we still hold items.
        if (isInventoryFull()) {
            doDeposit(level);
            return;
        }
        doMine(level);
    }

    private void doMine(ServerLevel level) {
        if (this.target == null || !isMineable(level, this.target)) {
            this.target = findTarget(level);
            this.digProgress = 0;
        }
        if (this.target == null) {
            // Area cleared. Offload anything we still carry, otherwise stand by.
            if (!isInventoryEmpty()) {
                doDeposit(level);
            } else {
                this.navigation.stop();
            }
            return;
        }

        Vec3 center = Vec3.atCenterOf(this.target);
        mech.getLookControl().setLookAt(center.x, center.y, center.z);
        if (mech.distanceToSqr(center) > REACH_SQR) {
            this.digProgress = 0;
            this.navigation.moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        this.navigation.stop();

        int cost = mech.getMineCost();
        if (mech.getEnergy().getEnergyStored() < cost) {
            // Out of power: idle until a Charging Pad tops us up.
            return;
        }
        if (++this.digProgress < digDuration(level, this.target)) {
            return;
        }

        // Break the block.
        if (!mech.consumeEnergy(cost)) {
            this.digProgress = 0;
            return;
        }
        BlockState state = level.getBlockState(this.target);
        List<ItemStack> drops = gatherDrops(level, this.target, state);
        for (ItemStack drop : drops) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(mech.getInventory(), drop, false);
            if (!remainder.isEmpty()) {
                Block.popResource(level, mech.blockPosition(), remainder);
            }
        }
        level.levelEvent(2001, this.target, Block.getId(state)); // break particles + sound
        level.removeBlock(this.target, false);
        if (mech.hasUpgrade(UpgradeType.HAZARD_SEAL)) {
            sealLiquids(level, this.target);
        }
        this.target = null;
        this.digProgress = 0;
    }

    /**
     * Collect the block's drops, applying the mech's mining modules: Silk Touch (whole block, overrides
     * Fortune), Fortune (extra ore/gems on {@code c:ores}), then Smelter (smelt ore drops to ingots).
     */
    private List<ItemStack> gatherDrops(ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        boolean ore = state.is(ModTags.Blocks.ORES);
        boolean silk = mech.hasUpgrade(UpgradeType.SILK_TOUCH);
        if (silk) {
            tool.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
        } else if (ore) {
            int fortune = mech.getUpgradeLevel(UpgradeType.FORTUNE);
            if (fortune > 0) {
                tool.enchant(enchants.getOrThrow(Enchantments.FORTUNE), fortune);
            }
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), mech, tool);
        if (ore && !silk && mech.hasUpgrade(UpgradeType.SMELTER)) {
            drops = smelt(level, drops);
        }
        return drops;
    }

    /** Replace any drop that has a furnace recipe with its smelted result (preserving count). */
    private List<ItemStack> smelt(ServerLevel level, List<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            SingleRecipeInput input = new SingleRecipeInput(drop);
            Optional<RecipeHolder<SmeltingRecipe>> recipe =
                    level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, input, level);
            if (recipe.isPresent()) {
                ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
                if (!result.isEmpty()) {
                    ItemStack smelted = result.copy();
                    smelted.setCount(result.getCount() * drop.getCount());
                    out.add(smelted);
                    continue;
                }
            }
            out.add(drop);
        }
        return out;
    }

    /** Hazard Seal: wall off any liquid touching the just-dug cell so a quarry won't flood or burn. */
    private void sealLiquids(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos np = pos.relative(dir);
            if (!level.getBlockState(np).getFluidState().isEmpty()) {
                level.setBlock(np, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private void doDeposit(ServerLevel level) {
        BlockPos deposit = mech.getDepositPos();
        if (deposit == null) {
            // No deposit target: hold items and idle (mining pauses while full).
            this.navigation.stop();
            return;
        }
        Vec3 center = Vec3.atCenterOf(deposit);
        if (mech.distanceToSqr(center) > REACH_SQR) {
            this.navigation.moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        this.navigation.stop();

        IItemHandler chest = Capabilities.ItemHandler.BLOCK.getCapability(level, deposit, null, null, (Direction) null);
        if (chest == null) {
            return;
        }
        IItemHandler inventory = mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(chest, stack.copy(), false);
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
            }
        }
    }

    private BlockPos findTarget(ServerLevel level) {
        BlockPos min = mech.getAreaMin();
        BlockPos max = mech.getAreaMax();
        if (min == null || max == null) {
            return null;
        }
        // Resume cursor: reset to the top whenever the assigned area changes (or after a restart, which
        // nulls scanMin). Otherwise carry on from the dig front so cleared upper layers aren't re-scanned.
        if (!min.equals(this.scanMin) || !max.equals(this.scanMax)) {
            this.scanMin = min;
            this.scanMax = max;
            this.scanTopY = max.getY();
        }
        // Top-down so it digs like a quarry, starting from the cursor (no layer above it still has blocks).
        for (int y = Math.min(this.scanTopY, max.getY()); y >= min.getY(); y--) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isMineable(level, pos)) {
                        this.scanTopY = y; // resume here next time; everything above is cleared
                        return pos;
                    }
                }
            }
        }
        this.scanTopY = min.getY() - 1; // whole area cleared
        return null;
    }

    private boolean isMineable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false; // unbreakable (bedrock, etc.)
        }
        if (level.getBlockEntity(pos) != null) {
            return false; // never break machines / chests
        }
        return !state.is(ModTags.Blocks.MECH_UNMINEABLE);
    }

    private int digDuration(ServerLevel level, BlockPos pos) {
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        int base = mech.getDigTicks();
        return Math.max(base, (int) (base * Math.max(1.0F, hardness)));
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

    private boolean isInventoryEmpty() {
        IItemHandler inventory = mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
