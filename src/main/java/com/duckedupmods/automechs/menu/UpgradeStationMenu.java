package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.block.entity.UpgradeStationBlockEntity;
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
 * Container menu for the {@link UpgradeStationBlockEntity}: a chassis slot, an upgrade-module slot, and
 * the player inventory. Progress and stored energy are synced via {@link ContainerData}.
 */
public class UpgradeStationMenu extends AbstractContainerMenu {

    private static final int SLOTS = 2;
    private static final int PLAYER_START = SLOTS;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Server-side constructor. */
    public UpgradeStationMenu(int containerId, Inventory playerInventory, UpgradeStationBlockEntity be) {
        super(ModMenus.UPGRADE_STATION.get(), containerId);
        this.access = be != null
                ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                : ContainerLevelAccess.NULL;
        this.data = be != null ? be.getData() : new SimpleContainerData(4);
        IItemHandler items = be != null ? be.getItems() : new ItemStackHandler(SLOTS);

        // Module is the consumable INPUT (left); the chassis is the mech the upgrade is installed ONTO
        // and stays put (right). The progress arrow flows module → mech: "apply the upgrade to the robot".
        addSlot(new SlotItemHandler(items, UpgradeStationBlockEntity.SLOT_MODULE, 44, 35));
        addSlot(new SlotItemHandler(items, UpgradeStationBlockEntity.SLOT_CHASSIS, 80, 35));
        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    /** Client-side constructor. */
    public UpgradeStationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static UpgradeStationBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof UpgradeStationBlockEntity station ? station : null;
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
        return max <= 0 ? UpgradeStationBlockEntity.MAX_PROGRESS : max;
    }

    public int getEnergy() {
        // Recombine the two synced shorts (see the BE's ContainerData note).
        return ((this.data.get(3) & 0xFFFF) << 16) | (this.data.get(2) & 0xFFFF);
    }

    public int getMaxEnergy() {
        return UpgradeStationBlockEntity.ENERGY_CAPACITY;
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
            } else if (!moveItemStackTo(stack, 0, SLOTS, false)) {
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
        return stillValid(this.access, player, ModBlocks.UPGRADE_STATION.get());
    }
}
