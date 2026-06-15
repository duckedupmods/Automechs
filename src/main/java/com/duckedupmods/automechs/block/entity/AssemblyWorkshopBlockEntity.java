package com.duckedupmods.automechs.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.menu.AssemblyWorkshopMenu;
import com.duckedupmods.automechs.recipe.AssemblyRecipe;
import com.duckedupmods.automechs.recipe.AssemblyRecipeInput;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModRecipes;

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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Block entity for the Assembly Workshop. Holds input + output inventories, looks up a matching
 * {@link AssemblyRecipe} each tick, and after a short assembly time produces the result (consuming
 * the ingredients). Progress is exposed to the menu via {@link ContainerData} for the GUI arrow.
 */
public class AssemblyWorkshopBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOTS = 6;
    public static final int MAX_PROGRESS = 100;
    public static final int ENERGY_CAPACITY = 50_000;
    public static final int ENERGY_MAX_RECEIVE = 5_000;

    private final ItemStackHandler inputItems = new ItemStackHandler(INPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ItemStackHandler outputItems = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Receive-only buffer fed by conduits. {@link #consume} pulls energy for fabrication. */
    private static final class FabEnergy extends EnergyStorage {
        private FabEnergy() {
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

    private final FabEnergy energy = new FabEnergy();
    private int progress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            // index 2/3 carry the FE buffer as two shorts (ContainerData syncs shorts; see menu).
            return switch (index) {
                case 0 -> progress;
                case 1 -> MAX_PROGRESS;
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

    public AssemblyWorkshopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_WORKSHOP.get(), pos, state);
    }

    public IItemHandler getInputItems() {
        return this.inputItems;
    }

    public IItemHandler getOutputItems() {
        return this.outputItems;
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    public ContainerData getData() {
        return this.data;
    }

    /** True when every input slot is empty — used to skip the recipe lookup on idle ticks. */
    private boolean inputsEmpty() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (!this.inputItems.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private AssemblyRecipeInput recipeInput() {
        List<ItemStack> items = new ArrayList<>(INPUT_SLOTS);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            items.add(this.inputItems.getStackInSlot(slot));
        }
        return new AssemblyRecipeInput(items);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyWorkshopBlockEntity be) {
        // Idle fast-path: with no inputs there can be no recipe — skip the recipe-manager lookup and the
        // per-tick input-list allocation entirely (cheap slot check), so an empty workshop costs ~nothing.
        if (be.inputsEmpty()) {
            if (be.progress != 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }
        Optional<RecipeHolder<AssemblyRecipe>> match =
                level.getRecipeManager().getRecipeFor(ModRecipes.ASSEMBLY_TYPE.get(), be.recipeInput(), level);

        if (match.isPresent() && be.canOutput(match.get().value().getResultItem(level.registryAccess()))) {
            // Only advance while powered; otherwise fabrication stalls until energy returns.
            if (be.energy.consume(Config.FABRICATOR_FE_PER_TICK.get())) {
                be.progress++;
                if (be.progress >= MAX_PROGRESS) {
                    be.assemble(match.get().value(), level);
                    be.progress = 0;
                }
                be.setChanged();
            }
        } else if (be.progress != 0) {
            be.progress = 0;
            be.setChanged();
        }
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = this.outputItems.getStackInSlot(0);
        if (out.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(out, result)) {
            return false;
        }
        return out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    private void assemble(AssemblyRecipe recipe, Level level) {
        for (SizedIngredient ingredient : recipe.inputs()) {
            int need = ingredient.count();
            for (int slot = 0; slot < INPUT_SLOTS && need > 0; slot++) {
                ItemStack stack = this.inputItems.getStackInSlot(slot);
                if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                    int take = Math.min(stack.getCount(), need);
                    this.inputItems.extractItem(slot, take, false);
                    need -= take;
                }
            }
        }
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        ItemStack out = this.outputItems.getStackInSlot(0);
        if (out.isEmpty()) {
            this.outputItems.setStackInSlot(0, result);
        } else {
            out.grow(result.getCount());
        }
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
        ItemStack output = this.outputItems.extractItem(0, Integer.MAX_VALUE, false);
        if (!output.isEmpty()) {
            drops.add(output);
        }
        return drops;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.automechs.assembly_workshop");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AssemblyWorkshopMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", this.inputItems.serializeNBT(registries));
        tag.put("Output", this.outputItems.serializeNBT(registries));
        tag.putInt("Progress", this.progress);
        tag.putInt("Energy", this.energy.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Input")) {
            this.inputItems.deserializeNBT(registries, tag.getCompound("Input"));
        }
        if (tag.contains("Output")) {
            this.outputItems.deserializeNBT(registries, tag.getCompound("Output"));
        }
        this.progress = tag.getInt("Progress");
        this.energy.setStored(tag.getInt("Energy"));
    }
}
