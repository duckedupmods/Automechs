package com.duckedupmods.automechs.block.entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Main Drive — the powered controller of a Data Rack network. It receives Forge Energy (from any
 * cable/generator), floods its {@link com.duckedupmods.automechs.block.DataCableBlock Data Cables} to
 * discover the connected Data Racks (and the Terminal), and each tick draws FE scaled by how many racks
 * it powers and how much data they hold. If its buffer can't cover the draw the network goes
 * {@code offline} (terminal unusable, bots idle) — stored items are always safe.
 */
public class MainDriveBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final int CAPACITY = 200_000;
    public static final int MAX_RECEIVE = 5_000;

    // Power model: base + per-rack + per-stored-stack, all FE/tick. (Config-tunable later.)
    public static final int FE_BASE = 20;
    public static final int FE_PER_RACK = 5;
    public static final int FE_PER_STACK = 1;

    private static final int RESCAN_TICKS = 20;
    private static final int MAX_NODES = 1024; // flood-fill safety cap

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Receive-only FE buffer with internal drain (the drive consumes it to power the network). */
    private static final class DriveEnergy extends EnergyStorage {
        private DriveEnergy() {
            super(CAPACITY, MAX_RECEIVE, 0);
        }

        void setStored(int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }

        void drain(int amount) {
            this.energy = Math.max(0, this.energy - Math.max(0, amount));
        }
    }

    private final DriveEnergy energy = new DriveEnergy();

    private final List<BlockPos> racks = new ArrayList<>();
    private BlockPos terminal;
    private boolean online;
    private int feDraw;
    private int rescanCooldown;
    // Network tallies, refreshed on the rescan cadence (not every tick) — feeds the FE draw + face readout.
    private int cachedStacks;
    private int cachedItems;

    // Client-side mirror of the status figures shown on the drive's face (synced via update packets).
    private boolean clientOnline;
    private int clientItems;
    private int clientRacks;
    private int clientFe;
    // Last-synced values, so we only push a block update when the readout actually changes.
    private boolean syncedOnline;
    private int syncedItems = -1;
    private int syncedRacks = -1;
    private int syncedFeBucket = -1;

    public MainDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAIN_DRIVE.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    public List<BlockPos> getRacks() {
        return this.racks;
    }

    public BlockPos getTerminal() {
        return this.terminal;
    }

    public boolean isOnline() {
        return this.online;
    }

    public int getFeDraw() {
        return this.feDraw;
    }

    public int getStoredEnergy() {
        return this.energy.getEnergyStored();
    }

    // ---- display figures (synced on the client, live on the server) ----------

    public boolean displayOnline() {
        return this.level != null && this.level.isClientSide ? this.clientOnline : this.online;
    }

    public int displayItems() {
        return this.level != null && this.level.isClientSide ? this.clientItems : itemCount();
    }

    public int displayRacks() {
        return this.level != null && this.level.isClientSide ? this.clientRacks : this.racks.size();
    }

    public int displayFe() {
        return this.level != null && this.level.isClientSide ? this.clientFe : this.energy.getEnergyStored();
    }

    // ---- network status (used by status report, terminal, bots) --------------

    public int rackCount() {
        return this.racks.size();
    }

    /** Total stored items across every rack in the network. */
    public int itemCount() {
        int total = 0;
        for (DataRackBlockEntity rack : liveRacks()) {
            for (int i = 0; i < rack.getSectors().getSlots(); i++) {
                total += rack.getSectors().getStackInSlot(i).getCount();
            }
        }
        return total;
    }

    /** Total used sectors across the network (≈ stacks stored — the power-scaling figure). */
    public int usedSectorCount() {
        int total = 0;
        for (DataRackBlockEntity rack : liveRacks()) {
            total += rack.usedSectors();
        }
        return total;
    }

    /** Resolve the discovered rack positions to live block entities (skipping any that vanished). */
    public List<DataRackBlockEntity> liveRacks() {
        List<DataRackBlockEntity> out = new ArrayList<>();
        if (this.level == null) {
            return out;
        }
        for (BlockPos pos : this.racks) {
            if (this.level.getBlockEntity(pos) instanceof DataRackBlockEntity rack) {
                out.add(rack);
            }
        }
        return out;
    }

    // ---- tick ----------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, MainDriveBlockEntity be) {
        if (be.rescanCooldown-- <= 0) {
            be.rescan(level, pos); // also refreshes cachedStacks / cachedItems
            be.rescanCooldown = RESCAN_TICKS;
        }
        be.feDraw = FE_BASE + FE_PER_RACK * be.racks.size() + FE_PER_STACK * be.cachedStacks;
        if (be.energy.getEnergyStored() >= be.feDraw) {
            be.energy.drain(be.feDraw);
            be.online = true;
        } else {
            be.online = false;
        }
        // Re-sync the face readout to nearby clients whenever a shown figure changes (FE bucketed to 5%).
        int rackCount = be.racks.size();
        int feBucket = be.energy.getEnergyStored() * 20 / CAPACITY;
        if (be.online != be.syncedOnline || be.cachedItems != be.syncedItems
                || rackCount != be.syncedRacks || feBucket != be.syncedFeBucket) {
            be.syncedOnline = be.online;
            be.syncedItems = be.cachedItems;
            be.syncedRacks = rackCount;
            be.syncedFeBucket = feBucket;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** Flood-fill through Data Cables from the drive to find member racks + the terminal. */
    private void rescan(Level level, BlockPos origin) {
        this.racks.clear();
        this.terminal = null;

        Set<BlockPos> visitedCables = new HashSet<>();
        Set<BlockPos> seenMembers = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        // seed from the drive's own neighbours.
        for (Direction dir : Direction.values()) {
            BlockPos np = origin.relative(dir);
            if (isCable(level, np)) {
                queue.add(np);
            } else {
                checkMember(level, np, seenMembers);
            }
        }

        int budget = MAX_NODES;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos cur = queue.poll();
            if (!visitedCables.add(cur)) {
                continue;
            }
            for (Direction dir : Direction.values()) {
                BlockPos np = cur.relative(dir);
                if (isCable(level, np)) {
                    if (!visitedCables.contains(np)) {
                        queue.add(np);
                    }
                } else {
                    checkMember(level, np, seenMembers);
                }
            }
        }

        // Refresh the network tallies once here, rather than re-scanning every rack every tick.
        int stacks = 0;
        int items = 0;
        for (DataRackBlockEntity rack : liveRacks()) {
            stacks += rack.usedSectors();
            items += rack.itemCount();
        }
        this.cachedStacks = stacks;
        this.cachedItems = items;
    }

    private boolean isCable(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.DATA_CABLE.get());
    }

    private void checkMember(Level level, BlockPos pos, Set<BlockPos> seen) {
        if (!seen.add(pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DataRackBlockEntity) {
            this.racks.add(pos.immutable());
        }
        // Terminal block entity is hooked up when the Terminal is added.
    }

    // ---- save / load ---------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", this.energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy.setStored(tag.getInt("Energy"));
    }

    // ---- client sync (status figures for the face readout) -------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("On", this.online);
        tag.putInt("Items", itemCount());
        tag.putInt("Racks", this.racks.size());
        tag.putInt("Fe", this.energy.getEnergyStored());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.clientOnline = tag.getBoolean("On");
        this.clientItems = tag.getInt("Items");
        this.clientRacks = tag.getInt("Racks");
        this.clientFe = tag.getInt("Fe");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Route live updates to {@link #handleUpdateTag} (NeoForge's default would drop our display-only keys). */
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
