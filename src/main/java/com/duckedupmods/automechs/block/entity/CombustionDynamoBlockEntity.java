package com.duckedupmods.automechs.block.entity;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.block.CombustionDynamoBlock;
import com.duckedupmods.automechs.menu.CombustionDynamoMenu;
import com.duckedupmods.automechs.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Backs the Combustion Dynamo. Holds a fuel slot (drop a whole stack of coal/charcoal/etc. in via its
 * menu); each tick it burns its current fuel into Forge Energy ({@link Config#DYNAMO_FE_PER_TICK}) and
 * pushes stored FE out to any adjacent receiver. The {@link CombustionDynamoBlock#LIT} blockstate
 * mirrors whether it is burning.
 */
public class CombustionDynamoBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_FUEL = 0;
    public static final int CAPACITY = 100_000;
    public static final int MAX_PUSH_PER_TICK = 2_000;

    /** Generator buffer: cannot be filled by cables (maxReceive 0), only generated internally / pushed out. */
    private static final class DynamoEnergy extends EnergyStorage {
        private DynamoEnergy() {
            super(CAPACITY, 0, MAX_PUSH_PER_TICK);
        }

        void setStored(int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }

        void generate(int value) {
            this.energy = Math.min(this.capacity, this.energy + Math.max(0, value));
        }

        boolean isFull() {
            return this.energy >= this.capacity;
        }
    }

    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(RecipeType.SMELTING) > 0;
        }
    };

    private final DynamoEnergy energy = new DynamoEnergy();
    private int burnTime;       // remaining ticks of the fuel currently burning
    private int burnDuration;   // full duration of the fuel currently burning (for the GUI flame)

    // NOTE: ContainerData syncs each value as a 16-bit short, so the FE buffer (up to 100k) is sent as
    // two shorts (low/high 16 bits) and recombined client-side. burnTime/burnDuration fit in a short.
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnDuration;
                case 2 -> energy.getEnergyStored() & 0xFFFF;
                default -> (energy.getEnergyStored() >>> 16) & 0xFFFF;
            };
        }

        @Override
        public void set(int index, int value) {
            // Client-side sync: store the incoming values so get() reflects them (the client BE has no
            // energy of its own). Energy arrives as two shorts (low/high 16 bits).
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> burnDuration = value;
                case 2 -> energy.setStored((energy.getEnergyStored() & ~0xFFFF) | (value & 0xFFFF));
                default -> energy.setStored(((value & 0xFFFF) << 16) | (energy.getEnergyStored() & 0xFFFF));
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public CombustionDynamoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMBUSTION_DYNAMO.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    public int getEnergyStored() {
        return this.energy.getEnergyStored();
    }

    public IItemHandler getFuel() {
        return this.fuel;
    }

    public ContainerData getData() {
        return this.data;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CombustionDynamoBlockEntity be) {
        boolean wasLit = state.getValue(CombustionDynamoBlock.LIT);
        boolean changed = false;

        // Burn current fuel only while there's room to store the power — pause (don't consume fuel/FE) when
        // the buffer is full, so an idle dynamo with a full buffer never wastes fuel. Resumes once drained.
        if (be.burnTime > 0 && !be.energy.isFull()) {
            be.burnTime--;
            be.energy.generate(Config.DYNAMO_FE_PER_TICK.get());
            changed = true;
        }

        // Out of burn time but there's fuel and room for power -> light the next item.
        if (be.burnTime <= 0 && !be.energy.isFull()) {
            ItemStack fuelStack = be.fuel.getStackInSlot(SLOT_FUEL);
            int burn = fuelStack.getBurnTime(RecipeType.SMELTING);
            if (!fuelStack.isEmpty() && burn > 0) {
                be.fuel.extractItem(SLOT_FUEL, 1, false);
                be.burnTime = burn;
                be.burnDuration = burn;
                changed = true;
            }
        }
        boolean lit = be.burnTime > 0;

        // Push stored energy out to adjacent receivers.
        int budget = MAX_PUSH_PER_TICK;
        for (Direction dir : Direction.values()) {
            if (budget <= 0 || be.energy.getEnergyStored() <= 0) {
                break;
            }
            IEnergyStorage dest = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (dest == null || !dest.canReceive()) {
                continue;
            }
            int offer = Math.min(budget, be.energy.getEnergyStored());
            int accepted = dest.receiveEnergy(offer, false);
            if (accepted > 0) {
                be.energy.extractEnergy(accepted, false);
                budget -= accepted;
                changed = true;
            }
        }

        if (lit != wasLit) {
            level.setBlock(pos, state.setValue(CombustionDynamoBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        if (changed) {
            be.setChanged();
        }
    }

    /** Items to scatter when the block is broken. */
    public List<ItemStack> getDropContents() {
        List<ItemStack> drops = new ArrayList<>();
        // Extract so the fuel leaves the handler — dropped to the world exactly once (called on break).
        ItemStack fuelStack = this.fuel.extractItem(SLOT_FUEL, Integer.MAX_VALUE, false);
        if (!fuelStack.isEmpty()) {
            drops.add(fuelStack);
        }
        return drops;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automechs.combustion_dynamo");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CombustionDynamoMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", this.fuel.serializeNBT(registries));
        tag.putInt("Energy", this.energy.getEnergyStored());
        tag.putInt("BurnTime", this.burnTime);
        tag.putInt("BurnDuration", this.burnDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Fuel")) {
            this.fuel.deserializeNBT(registries, tag.getCompound("Fuel"));
        }
        this.energy.setStored(tag.getInt("Energy"));
        this.burnTime = tag.getInt("BurnTime");
        this.burnDuration = tag.getInt("BurnDuration");
    }
}
