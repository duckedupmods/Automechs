package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.block.entity.CombustionDynamoBlockEntity;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the {@link CombustionDynamoBlockEntity}: a single fuel slot plus the player
 * inventory. The burn timer and stored energy are synced via {@link ContainerData} for the flame and
 * power bar.
 */
public class CombustionDynamoMenu extends AbstractContainerMenu {

    private static final int SLOTS = 1;
    private static final int PLAYER_START = SLOTS;

    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Server-side constructor. */
    public CombustionDynamoMenu(int containerId, Inventory playerInventory, CombustionDynamoBlockEntity be) {
        super(ModMenus.COMBUSTION_DYNAMO.get(), containerId);
        this.access = be != null
                ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                : ContainerLevelAccess.NULL;
        this.data = be != null ? be.getData() : new SimpleContainerData(4);
        IItemHandler fuel = be != null ? be.getFuel() : new ItemStackHandler(SLOTS);

        addSlot(new SlotItemHandler(fuel, CombustionDynamoBlockEntity.SLOT_FUEL, 80, 38) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getBurnTime(RecipeType.SMELTING) > 0;
            }
        });
        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    /** Client-side constructor. */
    public CombustionDynamoMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static CombustionDynamoBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof CombustionDynamoBlockEntity dynamo ? dynamo : null;
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

    public int getBurnTime() {
        return this.data.get(0);
    }

    public int getBurnDuration() {
        int dur = this.data.get(1);
        return dur <= 0 ? 1 : dur;
    }

    public int getEnergy() {
        // Recombine the two synced shorts (see the BE's ContainerData note).
        return ((this.data.get(3) & 0xFFFF) << 16) | (this.data.get(2) & 0xFFFF);
    }

    public int getMaxEnergy() {
        return CombustionDynamoBlockEntity.CAPACITY;
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
        return stillValid(this.access, player, ModBlocks.COMBUSTION_DYNAMO.get());
    }
}
