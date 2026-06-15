package com.duckedupmods.automechs.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.duckedupmods.automechs.network.NetworkItem;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Storage Terminal — the AE2-style access point onto a Data Rack network. It floods the Data Cables to
 * find the powered {@link MainDriveBlockEntity Main Drive}, then reads/writes the racks the drive manages:
 * the GUI shows every item aggregated across the network, withdraws pull items out, and inserts scatter
 * incoming items into free sectors (deliberately fragmented, so the Cache Crawler bots have defrag work to
 * do). Everything is gated on the drive being {@link MainDriveBlockEntity#isOnline() online} — no power,
 * no access; the stored items stay safe regardless.
 */
public class TerminalBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {

    private static final int MAX_NODES = 1024; // flood-fill safety cap
    private static final int DRIVE_CACHE_TTL = 20; // ticks to trust the cached drive before re-flooding
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Cached drive resolution — a single menu operation calls findDrive() many times (isOnline + racks +
    // snapshot + count); flooding the whole cable graph each time was the network's hottest path.
    private MainDriveBlockEntity cachedDrive;
    private long driveCacheTick = Long.MIN_VALUE;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_TERMINAL.get(), pos, state);
    }

    // ---- network discovery ---------------------------------------------------

    /** Resolve the network's Main Drive, re-flooding the cable graph at most once per {@value #DRIVE_CACHE_TTL} ticks. */
    public MainDriveBlockEntity findDrive() {
        if (this.level == null) {
            return null;
        }
        long now = this.level.getGameTime();
        if (now - this.driveCacheTick < DRIVE_CACHE_TTL
                && (this.cachedDrive == null || !this.cachedDrive.isRemoved())) {
            return this.cachedDrive;
        }
        this.cachedDrive = floodForDrive();
        this.driveCacheTick = now;
        return this.cachedDrive;
    }

    /** Flood the cable graph from this terminal and return the first Main Drive reached, or null. */
    private MainDriveBlockEntity floodForDrive() {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        // Seed from the terminal's own neighbours (it sits on the cable, not inside it).
        for (Direction dir : Direction.values()) {
            BlockPos np = this.worldPosition.relative(dir);
            BlockState s = this.level.getBlockState(np);
            if (s.is(ModBlocks.MAIN_DRIVE.get()) && this.level.getBlockEntity(np) instanceof MainDriveBlockEntity drive) {
                return drive;
            }
            if (s.is(ModBlocks.DATA_CABLE.get())) {
                queue.add(np);
            }
        }
        int budget = MAX_NODES;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos cur = queue.poll();
            if (!visited.add(cur)) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos np = cur.relative(dir);
                BlockState s = this.level.getBlockState(np);
                if (s.is(ModBlocks.MAIN_DRIVE.get()) && this.level.getBlockEntity(np) instanceof MainDriveBlockEntity drive) {
                    return drive;
                }
                if (s.is(ModBlocks.DATA_CABLE.get()) && !visited.contains(np)) {
                    queue.add(np);
                }
            }
        }
        return null;
    }

    public boolean isOnline() {
        MainDriveBlockEntity drive = findDrive();
        return drive != null && drive.isOnline();
    }

    public List<DataRackBlockEntity> racks() {
        MainDriveBlockEntity drive = findDrive();
        return drive != null ? drive.liveRacks() : List.of();
    }

    // ---- aggregate view ------------------------------------------------------

    /** Aggregate every item across the network into one entry per type, sorted by total count descending. */
    public List<NetworkItem> snapshot() {
        List<NetworkItem> out = new ArrayList<>();
        for (DataRackBlockEntity rack : racks()) {
            IItemHandler sectors = rack.getSectors();
            for (int i = 0; i < sectors.getSlots(); i++) {
                ItemStack s = sectors.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                boolean merged = false;
                for (int j = 0; j < out.size(); j++) {
                    if (ItemStack.isSameItemSameComponents(out.get(j).icon(), s)) {
                        out.set(j, new NetworkItem(out.get(j).icon(), out.get(j).count() + s.getCount()));
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    out.add(new NetworkItem(s.copyWithCount(1), s.getCount()));
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        // The snapshot codec is capped (MAX_ITEMS); keep the largest entries so encoding never throws and
        // freezes the grid on a huge store. (AE2 paginates; we show the top types.)
        if (out.size() > com.duckedupmods.automechs.network.StorageSnapshotPayload.MAX_ITEMS) {
            return new ArrayList<>(out.subList(0, com.duckedupmods.automechs.network.StorageSnapshotPayload.MAX_ITEMS));
        }
        return out;
    }

    public int networkItemCount() {
        int total = 0;
        for (DataRackBlockEntity rack : racks()) {
            total += rack.itemCount();
        }
        return total;
    }

    // ---- insert (fragmenting) ------------------------------------------------

    /**
     * Scatter a stack into the network's free sectors. Incoming items are split into random chunks across
     * empty sectors rather than merged into existing ones — that's what fragments the rack, giving the
     * Cache Crawlers something to defrag. Returns the remainder that didn't fit (network full / offline).
     */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty() || !isOnline() || this.level == null) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        int max = Math.max(1, remaining.getMaxStackSize());
        for (DataRackBlockEntity rack : racks()) {
            IItemHandler sectors = rack.getSectors();
            for (int i = 0; i < sectors.getSlots() && !remaining.isEmpty(); i++) {
                if (!sectors.getStackInSlot(i).isEmpty()) {
                    continue;
                }
                int chunk = Math.min(remaining.getCount(), 1 + this.level.getRandom().nextInt(max));
                ItemStack put = remaining.copyWithCount(chunk);
                ItemStack leftover = sectors.insertItem(i, put, false);
                int placed = chunk - leftover.getCount();
                remaining.shrink(placed);
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    // ---- withdraw ------------------------------------------------------------

    /**
     * Pull up to {@code amount} of the item matching {@code proto} out of the network. Walks every rack's
     * sectors, extracting matching fragments until the request is met. Returns the assembled stack.
     */
    public ItemStack withdraw(ItemStack proto, int amount) {
        if (proto.isEmpty() || amount <= 0 || !isOnline()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ItemStack.EMPTY;
        int needed = amount;
        for (DataRackBlockEntity rack : racks()) {
            IItemHandler sectors = rack.getSectors();
            for (int i = 0; i < sectors.getSlots() && needed > 0; i++) {
                ItemStack s = sectors.getStackInSlot(i);
                if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, proto)) {
                    continue;
                }
                ItemStack got = sectors.extractItem(i, needed, false);
                if (got.isEmpty()) {
                    continue;
                }
                if (out.isEmpty()) {
                    out = got;
                } else {
                    out.grow(got.getCount());
                }
                needed -= got.getCount();
            }
            if (needed <= 0) {
                break;
            }
        }
        return out;
    }

    // ---- menu ----------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.automechs.terminal.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.duckedupmods.automechs.menu.TerminalMenu(containerId, playerInventory, this);
    }

    // ---- GeckoLib ------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
