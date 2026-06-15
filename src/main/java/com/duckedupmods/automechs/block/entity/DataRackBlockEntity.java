package com.duckedupmods.automechs.block.entity;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Backs the Data Rack: a passive item store organised into fixed "data sectors". The rack has no GUI of
 * its own — items flow in and out through the network Terminal (and hoppers/pipes via the item-handler
 * capability), landing in whatever sectors are free, so the same item can scatter across several
 * partly-filled sectors (the rack fragments like a disk). A {@link #defrag()} pass — run by a Cache
 * Crawler bot that physically visits the rack — merges same-item fragments into full stacks and compacts
 * them to the front, so a defragged rack uses one sector per stack. The block face shows live
 * {@link #fragmentationPercent() Defrag%} and item-count stats, drawn by {@code DataRackRenderer}.
 */
public class DataRackBlockEntity extends BlockEntity implements GeoBlockEntity {

    /** Sector count for the Basic rack. (Tiers raise this later.) */
    public static final int SECTORS = 54;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** The data sectors. The Terminal and hoppers/pipes insert via the item-handler capability (first-fit). */
    private final ItemStackHandler sectors = new ItemStackHandler(SECTORS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // Client-side mirror of the display figures (synced via update packets — sector contents are not).
    private int clientFrag;
    private int clientItems;
    private int clientUsed;
    // Last values pushed to clients, so the ticker only re-syncs on a real change.
    private int syncedFrag = -1;
    private int syncedItems = -1;
    private int syncedUsed = -1;
    private int syncCooldown;

    public DataRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DATA_RACK.get(), pos, state);
    }

    public IItemHandler getSectors() {
        return this.sectors;
    }

    /** Frag% to show on the block face — the synced figure on the client, computed live on the server. */
    public int displayFrag() {
        return this.level != null && this.level.isClientSide ? this.clientFrag : fragmentationPercent();
    }

    /** Item count to show on the block face — the synced figure on the client, computed live on the server. */
    public int displayItems() {
        return this.level != null && this.level.isClientSide ? this.clientItems : itemCount();
    }

    /** Used-sector count for the LED fill bar — synced on the client, computed live on the server. */
    public int displayUsed() {
        return this.level != null && this.level.isClientSide ? this.clientUsed : usedSectors();
    }

    /** Total sectors, for the LED fill bar's full-scale reference. */
    public int totalSectors() {
        return SECTORS;
    }

    /** Server ticker: re-sync the two display figures to nearby clients when they change. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, DataRackBlockEntity be) {
        if (be.syncCooldown-- > 0) {
            return;
        }
        be.syncCooldown = 10;
        int frag = be.fragmentationPercent();
        int items = be.itemCount();
        int used = be.usedSectors();
        if (frag != be.syncedFrag || items != be.syncedItems || used != be.syncedUsed) {
            be.syncedFrag = frag;
            be.syncedItems = items;
            be.syncedUsed = used;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ---- sector math ---------------------------------------------------------

    /** How many sectors hold any data. */
    public int usedSectors() {
        int used = 0;
        for (int i = 0; i < this.sectors.getSlots(); i++) {
            if (!this.sectors.getStackInSlot(i).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Total number of items (counting stack sizes) stored across every sector. */
    public int itemCount() {
        int total = 0;
        for (int i = 0; i < this.sectors.getSlots(); i++) {
            total += this.sectors.getStackInSlot(i).getCount();
        }
        return total;
    }

    /** The fewest sectors this data COULD occupy if fully compacted (one stack per sector). */
    public int optimalSectors() {
        List<ItemStack> merged = new ArrayList<>();
        for (int i = 0; i < this.sectors.getSlots(); i++) {
            ItemStack s = this.sectors.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack acc : merged) {
                if (ItemStack.isSameItemSameComponents(acc, s)) {
                    acc.grow(s.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(s.copy());
            }
        }
        int optimal = 0;
        for (ItemStack acc : merged) {
            int max = Math.max(1, acc.getMaxStackSize());
            optimal += (acc.getCount() + max - 1) / max;
        }
        return optimal;
    }

    /** Wasted-space metric: how much of the used space is fragmentation, 0–100. */
    public int fragmentationPercent() {
        int used = usedSectors();
        if (used <= 0) {
            return 0;
        }
        int optimal = optimalSectors();
        return Math.max(0, Math.round(100.0F * (used - optimal) / used));
    }

    /** True if defragging would actually free sectors (used by the Cache Crawler to pick a target). */
    public boolean needsDefrag() {
        return usedSectors() > optimalSectors();
    }

    /**
     * Compact the rack: merge every same-item fragment into full stacks and pack them contiguously from
     * the first sector. Returns the number of sectors freed (for feedback / bot pacing).
     */
    public int defrag() {
        int before = usedSectors();

        // 1) sum every item type currently stored.
        List<ItemStack> merged = new ArrayList<>();
        for (int i = 0; i < this.sectors.getSlots(); i++) {
            ItemStack s = this.sectors.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack acc : merged) {
                if (ItemStack.isSameItemSameComponents(acc, s)) {
                    acc.grow(s.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(s.copy());
            }
        }

        // 2) rewrite the sectors front-to-back as full stacks, grouped by item.
        for (int i = 0; i < this.sectors.getSlots(); i++) {
            this.sectors.setStackInSlot(i, ItemStack.EMPTY);
        }
        int slot = 0;
        for (ItemStack acc : merged) {
            int max = Math.max(1, acc.getMaxStackSize());
            int remaining = acc.getCount();
            while (remaining > 0 && slot < this.sectors.getSlots()) {
                int take = Math.min(remaining, max);
                ItemStack stack = acc.copy();
                stack.setCount(take);
                this.sectors.setStackInSlot(slot++, stack);
                remaining -= take;
            }
        }
        setChanged();
        return Math.max(0, before - usedSectors());
    }

    /** Sectors to scatter when the block is broken. */
    public List<ItemStack> getDropContents() {
        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < this.sectors.getSlots(); i++) {
            ItemStack stack = this.sectors.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }

    // ---- save / load ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Sectors", this.sectors.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Sectors")) {
            this.sectors.deserializeNBT(registries, tag.getCompound("Sectors"));
        }
    }

    // ---- client sync (display figures only, not the sector contents) ---------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Frag", fragmentationPercent());
        tag.putInt("Items", itemCount());
        tag.putInt("Used", usedSectors());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.clientFrag = tag.getInt("Frag");
        this.clientItems = tag.getInt("Items");
        this.clientUsed = tag.getInt("Used");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Apply live updates. NeoForge's default {@code onDataPacket} routes to {@code loadWithComponents}
     * (i.e. {@code loadAdditional}), which ignores our display-only "Frag"/"Items"/"Used" keys — so without
     * this the face stats freeze at their chunk-load value. Route the packet to {@link #handleUpdateTag}.
     */
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        if (pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag(), registries);
        }
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
