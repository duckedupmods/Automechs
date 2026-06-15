package com.duckedupmods.automechs.block.entity;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.block.ChargingPadBlock;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.UpgradeModuleItem;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.menu.ChargingPadMenu;
import com.duckedupmods.automechs.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.AABB;
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
 * Backs the Charging Pad. Holds an FE buffer (filled from cables via the block's energy capability or by
 * hand-feeding fuel) and pushes energy into nearby {@link MiningMech}s each tick. Two upgrade slots tune
 * it: a Range module widens the charge radius, a Capacity module raises the FE storage cap. Right-click
 * opens its menu (energy gauge + upgrade slots).
 */
public class ChargingPadBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {

    public static final int MAX_RECEIVE = 5_000;      // FE/tick accepted from cables
    public static final int PUSH_PER_TICK = 1_000;    // FE/tick pushed to each nearby mech

    public static final int SLOT_RANGE = 0;
    public static final int SLOT_CAPACITY = 1;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CHARGING = RawAnimation.begin().thenLoop("charging");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Receive-only buffer with internal write access for fuel feeding, save/load, and a dynamic cap. */
    private static final class PadEnergy extends EnergyStorage {
        private PadEnergy(int capacity) {
            super(capacity, MAX_RECEIVE, 0);
        }

        void setStored(int value) {
            this.energy = Math.max(0, Math.min(this.capacity, value));
        }

        void setCapacity(int cap) {
            this.capacity = Math.max(1, cap);
            if (this.energy > this.capacity) {
                this.energy = this.capacity;
            }
        }

        void add(int value) {
            this.energy = Math.min(this.capacity, this.energy + Math.max(0, value));
        }

        int drain(int amount) {
            int drained = Math.min(this.energy, Math.max(0, amount));
            this.energy -= drained;
            return drained;
        }
    }

    private final PadEnergy energy = new PadEnergy(Config.PAD_BASE_CAPACITY.get());

    /** Two upgrade slots: a Range module and a Capacity module. */
    private final ItemStackHandler upgrades = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            recomputeCapacity();
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return UpgradeType.RANGE.maxLevel(); // both upgrades cap at the same max level
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!(stack.getItem() instanceof UpgradeModuleItem module)) {
                return false;
            }
            return switch (slot) {
                case SLOT_RANGE -> module.type() == UpgradeType.RANGE;
                case SLOT_CAPACITY -> module.type() == UpgradeType.CAPACITY;
                default -> false;
            };
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> (energy.getEnergyStored() >>> 16) & 0xFFFF;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                default -> (energy.getMaxEnergyStored() >>> 16) & 0xFFFF;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energy.setStored((energy.getEnergyStored() & ~0xFFFF) | (value & 0xFFFF));
                case 1 -> energy.setStored(((value & 0xFFFF) << 16) | (energy.getEnergyStored() & 0xFFFF));
                // indices 2/3 (capacity) are display-only; the client recombines them via getMaxEnergy().
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public ChargingPadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHARGING_PAD.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    public IItemHandler getUpgrades() {
        return this.upgrades;
    }

    public ContainerData getData() {
        return this.data;
    }

    /** Add energy from a consumed fuel item (bypasses the per-tick cable cap). */
    public void addFuelEnergy(int fe) {
        this.energy.add(fe);
        setChanged();
    }

    public boolean isFull() {
        return this.energy.getEnergyStored() >= this.energy.getMaxEnergyStored();
    }

    /** Charge radius = base + (Range module count × per-level), config-gated. */
    public int getRadius() {
        int level = this.upgrades.getStackInSlot(SLOT_RANGE).getCount();
        return Config.PAD_BASE_RADIUS.get() + level * Config.PAD_RADIUS_PER_RANGE.get();
    }

    /** Recompute the FE cap from the Capacity module count and clamp stored energy. */
    private void recomputeCapacity() {
        int level = this.upgrades.getStackInSlot(SLOT_CAPACITY).getCount();
        this.energy.setCapacity(Config.PAD_BASE_CAPACITY.get() + level * Config.PAD_CAPACITY_PER_LEVEL.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargingPadBlockEntity be) {
        boolean charged = false;
        if (be.energy.getEnergyStored() > 0) {
            AABB area = new AABB(pos).inflate(be.getRadius());
            List<MiningMech> mechs = level.getEntitiesOfClass(MiningMech.class, area);
            for (MiningMech mech : mechs) {
                if (be.energy.getEnergyStored() <= 0) {
                    break;
                }
                int offer = Math.min(PUSH_PER_TICK, be.energy.getEnergyStored());
                int accepted = mech.getEnergy().receiveEnergy(offer, false);
                if (accepted > 0) {
                    be.energy.drain(accepted);
                    be.setChanged();
                    charged = true;
                }
            }
        }
        // Spin the coil up while actively charging a mech.
        if (state.getValue(ChargingPadBlock.CHARGING) != charged) {
            level.setBlock(pos, state.setValue(ChargingPadBlock.CHARGING, charged), Block.UPDATE_CLIENTS);
        }
    }

    /** Items to scatter when the block is broken (the upgrade modules). */
    public List<ItemStack> getDropContents() {
        List<ItemStack> drops = new ArrayList<>();
        for (int slot = 0; slot < this.upgrades.getSlots(); slot++) {
            // Extract so the module leaves the handler — dropped to the world exactly once (called on break).
            ItemStack stack = this.upgrades.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        return drops;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automechs.charging_pad");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChargingPadMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", this.energy.getEnergyStored());
        tag.put("Upgrades", this.upgrades.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Upgrades")) {
            this.upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
        }
        recomputeCapacity();
        this.energy.setStored(tag.getInt("Energy"));
    }

    // ---- GeckoLib ------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "coil", 5, state ->
                state.setAndContinue(this.getBlockState().getValue(ChargingPadBlock.CHARGING) ? CHARGING : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
