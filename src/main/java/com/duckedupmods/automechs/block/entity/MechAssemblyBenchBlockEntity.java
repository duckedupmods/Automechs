package com.duckedupmods.automechs.block.entity;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.block.MechAssemblyBenchBlock;
import com.duckedupmods.automechs.block.MechMultiblock;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.MechCircuitItem;
import com.duckedupmods.automechs.menu.MechAssemblyBenchMenu;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModEntities;
import com.duckedupmods.automechs.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Block entity for the animated Mech Assembly Bench (the Robot Builder). It has three typed sockets —
 * Head, Chest, and a role Circuit — plus a Forge-Energy buffer fed by conduits. When all three parts
 * are present and the bench has power, the welding arms run (the {@link MechAssemblyBenchBlock#WORKING}
 * blockstate) and, after {@link #BUILD_TICKS} powered ticks, the three parts are consumed and a mech of
 * the circuit's {@link MechRole} <em>spawns</em> in front of the bench.
 */
public class MechAssemblyBenchBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {

    public static final int SLOT_PLATES = 0;
    public static final int SLOT_CORE = 1;
    public static final int SLOT_AI = 2;
    public static final int SLOT_CIRCUIT = 3;
    public static final int INPUT_SLOTS = 4;
    public static final int BUILD_TICKS = 200;
    public static final int ENERGY_CAPACITY = 50_000;
    public static final int ENERGY_MAX_RECEIVE = 5_000;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("work");

    private final ItemStackHandler inputItems = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_PLATES -> stack.is(ModItems.MECH_PLATES.get());
                case SLOT_CORE -> stack.is(ModItems.MECH_CORE.get());
                case SLOT_AI -> stack.is(ModItems.AI_CHIP.get());
                case SLOT_CIRCUIT -> stack.getItem() instanceof MechCircuitItem;
                default -> false;
            };
        }
    };

    /** Receive-only buffer fed by conduits. {@link #consume} pulls energy for the build. */
    private static final class BenchEnergy extends EnergyStorage {
        private BenchEnergy() {
            super(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
        }

        void setStored(int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }

        boolean consume(int amount) {
            if (amount <= 0) {
                return true;
            }
            if (this.energy < amount) {
                return false;
            }
            this.energy -= amount;
            return true;
        }
    }

    private final BenchEnergy energy = new BenchEnergy();
    private int progress;
    /** Client-only: synced "parts are loaded" flag that drives whether the preview mech renders. */
    private boolean clientHasParts;
    /** Server-only: last-synced parts state, so we only push a block update when it actually changes. */
    private boolean lastHasParts;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            // index 2/3 carry the FE buffer as two shorts (ContainerData syncs shorts; see menu).
            return switch (index) {
                case 0 -> progress;
                case 1 -> BUILD_TICKS;
                case 2 -> energy.getEnergyStored() & 0xFFFF;
                default -> (energy.getEnergyStored() >>> 16) & 0xFFFF;
            };
        }

        @Override
        public void set(int index, int value) {
            // Client-side sync: energy arrives as two shorts (low/high 16 bits); store it so get() reflects it.
            switch (index) {
                case 0 -> progress = value;
                case 2 -> energy.setStored((energy.getEnergyStored() & ~0xFFFF) | (value & 0xFFFF));
                case 3 -> energy.setStored(((value & 0xFFFF) << 16) | (energy.getEnergyStored() & 0xFFFF));
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public MechAssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECH_ASSEMBLY_BENCH.get(), pos, state);
    }

    public IItemHandler getInputItems() {
        return this.inputItems;
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    public ContainerData getData() {
        return this.data;
    }

    private boolean hasAllParts() {
        return this.inputItems.getStackInSlot(SLOT_PLATES).is(ModItems.MECH_PLATES.get())
                && this.inputItems.getStackInSlot(SLOT_CORE).is(ModItems.MECH_CORE.get())
                && this.inputItems.getStackInSlot(SLOT_AI).is(ModItems.AI_CHIP.get())
                && this.inputItems.getStackInSlot(SLOT_CIRCUIT).getItem() instanceof MechCircuitItem;
    }

    /** Whether the preview mech should render: server reads live parts, client reads the synced flag. */
    public boolean isPreviewActive() {
        return this.level != null && this.level.isClientSide ? this.clientHasParts : hasAllParts();
    }

    /** Build progress 0..1 (the renderer grows/raises the preview mech with this). */
    public float getBuildFraction() {
        return BUILD_TICKS <= 0 ? 0F : Math.min(1F, this.progress / (float) BUILD_TICKS);
    }

    /** Role of the mech currently slotted to build (drives which preview model/texture shows). */
    public MechRole getPreviewRole() {
        ItemStack circuit = this.inputItems.getStackInSlot(SLOT_CIRCUIT);
        return circuit.getItem() instanceof MechCircuitItem chip ? chip.role() : MechRole.MINING;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MechAssemblyBenchBlockEntity be) {
        boolean building = false;
        if (be.hasAllParts()) {
            // Only advance while powered; otherwise the build stalls (arms idle) until energy returns.
            if (be.energy.consume(Config.ASSEMBLY_FE_PER_TICK.get())) {
                be.progress++;
                building = true;
                if (be.progress >= BUILD_TICKS) {
                    be.buildMech(level, pos, state);
                    be.progress = 0;
                }
                be.setChanged();
            }
        } else if (be.progress != 0) {
            be.progress = 0;
            be.setChanged();
        }

        if (state.getValue(MechAssemblyBenchBlock.WORKING) != building) {
            level.setBlock(pos, state.setValue(MechAssemblyBenchBlock.WORKING, building), Block.UPDATE_CLIENTS);
        }

        // Sync progress + parts state to the client so the preview mech reveals correctly and stops the
        // instant the build finishes. Push when parts change, or every few ticks while building.
        boolean parts = be.hasAllParts();
        if (parts != be.lastHasParts) {
            be.lastHasParts = parts;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        } else if (building && be.progress % 4 == 0) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Consume one of each part and spawn the finished mech (role from the circuit) in front of the bench. */
    private void buildMech(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        ItemStack circuit = this.inputItems.getStackInSlot(SLOT_CIRCUIT);
        MechRole role = circuit.getItem() instanceof MechCircuitItem chip ? chip.role() : MechRole.MINING;

        this.inputItems.extractItem(SLOT_PLATES, 1, false);
        this.inputItems.extractItem(SLOT_CORE, 1, false);
        this.inputItems.extractItem(SLOT_AI, 1, false);
        this.inputItems.extractItem(SLOT_CIRCUIT, 1, false);

        MiningMech mech = ModEntities.MINING_MECH.get().create(server);
        if (mech == null) {
            return;
        }
        Direction face = state.getValue(MechAssemblyBenchBlock.FACING);
        // Emerge just outside the capsule's front face (the structure extends backward from the controller).
        BlockPos spawn = pos.relative(face, MechMultiblock.SPAWN_DISTANCE);
        mech.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, face.toYRot(), 0.0F);
        mech.setRole(role);
        Player owner = server.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 16.0D, false);
        if (owner != null) {
            mech.setOwner(owner);
        }
        server.addFreshEntity(mech);
        server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
    }

    /** Items to scatter when the block is broken. */
    public List<ItemStack> getDropContents() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            // Extract so items leave the handler — dropped to the world exactly once (called on break).
            ItemStack stack = this.inputItems.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automechs.mech_assembly_bench");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MechAssemblyBenchMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", this.inputItems.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
        tag.putInt("Energy", this.energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Input")) {
            this.inputItems.deserializeNBT(registries, tag.getCompound("Input"));
        }
        this.progress = tag.getInt("Progress");
        this.energy.setStored(tag.getInt("Energy"));
        if (tag.contains("HasParts")) {
            this.clientHasParts = tag.getBoolean("HasParts");
        }
    }

    // ---- client sync for the assembling-mech preview ------------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Progress", this.progress);
        tag.putBoolean("HasParts", hasAllParts());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.progress = tag.getInt("Progress");
            this.clientHasParts = tag.getBoolean("HasParts");
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state ->
                state.setAndContinue(this.getBlockState().getValue(MechAssemblyBenchBlock.WORKING) ? WORK : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
