package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.block.entity.ChargingPadBlockEntity;
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
 * Container menu for the {@link ChargingPadBlockEntity}: a Range upgrade slot, a Capacity upgrade slot,
 * and the player inventory. The stored FE and current capacity are synced via {@link ContainerData} for
 * the GUI's energy gauge.
 */
public class ChargingPadMenu extends AbstractContainerMenu {

    private static final int UPGRADE_SLOTS = 2;
    private static final int PLAYER_START = UPGRADE_SLOTS;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Server-side constructor. */
    public ChargingPadMenu(int containerId, Inventory playerInventory, ChargingPadBlockEntity be) {
        super(ModMenus.CHARGING_PAD.get(), containerId);
        this.access = be != null
                ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                : ContainerLevelAccess.NULL;
        this.data = be != null ? be.getData() : new SimpleContainerData(4);
        IItemHandler upgrades = be != null ? be.getUpgrades() : new ItemStackHandler(UPGRADE_SLOTS);

        addSlot(new SlotItemHandler(upgrades, ChargingPadBlockEntity.SLOT_RANGE, 62, 35));
        addSlot(new SlotItemHandler(upgrades, ChargingPadBlockEntity.SLOT_CAPACITY, 98, 35));
        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    /** Client-side constructor. */
    public ChargingPadMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static ChargingPadBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof ChargingPadBlockEntity pad ? pad : null;
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

    public int getEnergy() {
        return ((this.data.get(1) & 0xFFFF) << 16) | (this.data.get(0) & 0xFFFF);
    }

    public int getMaxEnergy() {
        int max = ((this.data.get(3) & 0xFFFF) << 16) | (this.data.get(2) & 0xFFFF);
        return max <= 0 ? 1 : max;
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
                if (!moveItemStackTo(stack, PLAYER_START, total, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, UPGRADE_SLOTS, false)) {
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
        return stillValid(this.access, player, ModBlocks.CHARGING_PAD.get());
    }
}
