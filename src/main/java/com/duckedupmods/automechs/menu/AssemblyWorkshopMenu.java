package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.block.entity.AssemblyWorkshopBlockEntity;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the {@link AssemblyWorkshopBlockEntity}: 6 input slots, 1 (take-only) output
 * slot, and the player inventory. The assembly progress is synced via {@link ContainerData}.
 */
public class AssemblyWorkshopMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOTS = AssemblyWorkshopBlockEntity.INPUT_SLOTS;
    private static final int OUTPUT_INDEX = INPUT_SLOTS;
    private static final int PLAYER_START = INPUT_SLOTS + 1;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Server-side constructor. */
    public AssemblyWorkshopMenu(int containerId, Inventory playerInventory, AssemblyWorkshopBlockEntity be) {
        super(ModMenus.ASSEMBLY_WORKSHOP.get(), containerId);
        this.access = be != null
                ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                : ContainerLevelAccess.NULL;
        this.data = be != null ? be.getData() : new SimpleContainerData(4);
        IItemHandler input = be != null ? be.getInputItems() : new ItemStackHandler(INPUT_SLOTS);
        IItemHandler output = be != null ? be.getOutputItems() : new ItemStackHandler(1);

        // Input grid: 3 columns x 2 rows.
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(input, row * 3 + col, 30 + col * 18, 18 + row * 18));
            }
        }
        // Output slot (take-only).
        addSlot(new SlotItemHandler(output, 0, 124, 27) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    /** Client-side constructor (resolves the BE from the BlockPos in the open packet). */
    public AssemblyWorkshopMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static AssemblyWorkshopBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof AssemblyWorkshopBlockEntity workshop ? workshop : null;
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    public int getProgress() {
        return this.data.get(0);
    }

    public int getMaxProgress() {
        int max = this.data.get(1);
        return max <= 0 ? AssemblyWorkshopBlockEntity.MAX_PROGRESS : max;
    }

    public int getEnergy() {
        // Recombine the two synced shorts (see the BE's ContainerData note).
        return ((this.data.get(3) & 0xFFFF) << 16) | (this.data.get(2) & 0xFFFF);
    }

    public int getMaxEnergy() {
        return AssemblyWorkshopBlockEntity.ENERGY_CAPACITY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int total = this.slots.size();
            if (index <= OUTPUT_INDEX) {
                // Workshop input/output -> player inventory.
                if (!moveItemStackTo(stack, PLAYER_START, total, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, INPUT_SLOTS, false)) {
                // Player inventory -> input slots.
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.ASSEMBLY_WORKSHOP.get());
    }
}
