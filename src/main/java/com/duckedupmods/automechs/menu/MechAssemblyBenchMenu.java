package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity;
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
 * Container menu for the {@link MechAssemblyBenchBlockEntity}: 6 component input slots and the player
 * inventory. There is no output slot — a finished build spawns the mech in the world. Build progress
 * and the stored energy are synced via {@link ContainerData} for the GUI's arrow and power bar.
 */
public class MechAssemblyBenchMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOTS = MechAssemblyBenchBlockEntity.INPUT_SLOTS;
    private static final int PLAYER_START = INPUT_SLOTS;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Server-side constructor. */
    public MechAssemblyBenchMenu(int containerId, Inventory playerInventory, MechAssemblyBenchBlockEntity be) {
        super(ModMenus.MECH_ASSEMBLY_BENCH.get(), containerId);
        this.access = be != null
                ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                : ContainerLevelAccess.NULL;
        this.data = be != null ? be.getData() : new SimpleContainerData(4);
        IItemHandler input = be != null ? be.getInputItems() : new ItemStackHandler(INPUT_SLOTS);

        // Four typed sockets in a row: Plates, Core, AI Chip, Circuit.
        addSlot(new SlotItemHandler(input, MechAssemblyBenchBlockEntity.SLOT_PLATES, 35, 35));
        addSlot(new SlotItemHandler(input, MechAssemblyBenchBlockEntity.SLOT_CORE, 60, 35));
        addSlot(new SlotItemHandler(input, MechAssemblyBenchBlockEntity.SLOT_AI, 85, 35));
        addSlot(new SlotItemHandler(input, MechAssemblyBenchBlockEntity.SLOT_CIRCUIT, 110, 35));
        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    /** Client-side constructor (resolves the BE from the BlockPos in the open packet). */
    public MechAssemblyBenchMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static MechAssemblyBenchBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof MechAssemblyBenchBlockEntity bench ? bench : null;
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
        return max <= 0 ? 1 : max;
    }

    public int getEnergy() {
        // Recombine the two synced shorts (see the BE's ContainerData note).
        return ((this.data.get(3) & 0xFFFF) << 16) | (this.data.get(2) & 0xFFFF);
    }

    public int getMaxEnergy() {
        return MechAssemblyBenchBlockEntity.ENERGY_CAPACITY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int total = this.slots.size();
            if (index < PLAYER_START) {
                // Bench inputs -> player inventory.
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
        return stillValid(this.access, player, ModBlocks.MECH_ASSEMBLY_BENCH.get());
    }
}
