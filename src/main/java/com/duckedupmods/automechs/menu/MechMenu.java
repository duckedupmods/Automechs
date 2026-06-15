package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for a {@link MiningMech}: exposes the mech's internal inventory plus the player's
 * inventory. The mech is resolved by entity id passed through the open packet so the same menu class
 * works on both sides. The {@code Work} toggle button is handled via {@link #clickMenuButton}.
 */
public class MechMenu extends AbstractContainerMenu {

    public static final int MECH_SLOTS = MiningMech.INVENTORY_SIZE;

    /** Range-tier select buttons use ids {@code RANGE_BUTTON_BASE + tier}. */
    public static final int RANGE_BUTTON_BASE = 100;

    /** Slot-grid origins, shared with {@link com.duckedupmods.automechs.client.MechScreen}. */
    public static final int MECH_GRID_Y = 44;
    public static final int PLAYER_INV_Y = 114;

    private final MiningMech mech;

    // GUI snapshot sent through the open packet (client) / read from the mech (server).
    private int rangeMax;
    private int selectedTier;
    private int maxEnergy = MiningMech.ENERGY_CAPACITY;
    private int soulXp;
    private int soulCapacity;

    // Builder readout: the loaded schematic's name, bounding size, and block count ("" / 0 when none).
    private String buildName = "";
    private int buildSizeX;
    private int buildSizeY;
    private int buildSizeZ;
    private int buildBlocks;

    /** One block type the build needs: an icon stack and how many to gather. */
    public record Material(ItemStack icon, int count) {}

    private final java.util.List<Material> materials = new java.util.ArrayList<>();

    /** Server-side constructor. */
    public MechMenu(int containerId, Inventory playerInventory, MiningMech mech) {
        super(ModMenus.MECH.get(), containerId);
        this.mech = mech;
        IItemHandler inventory = mech != null ? mech.getInventory() : new ItemStackHandler(MECH_SLOTS);
        if (mech != null) {
            this.rangeMax = mech.maxRangeTier();
            this.selectedTier = mech.getEffectiveRangeTier();
            this.maxEnergy = mech.getEnergy().getMaxEnergyStored();
            this.soulXp = mech.getSoulXp();
            this.soulCapacity = mech.getSoulCapacity();
            this.buildName = mech.getSchematicName();
            com.duckedupmods.automechs.schematic.Blueprint plan = mech.getBlueprint();
            if (plan != null) {
                this.buildSizeX = plan.size().getX();
                this.buildSizeY = plan.size().getY();
                this.buildSizeZ = plan.size().getZ();
                this.buildBlocks = plan.blockCount();
            }
        }

        // Mech inventory: 2 rows of 9.
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(inventory, row * 9 + col, 8 + col * 18, MECH_GRID_Y + row * 18));
            }
        }
        addPlayerInventory(playerInventory);
    }

    /** Client-side constructor (from the open packet). */
    public MechMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readInt()));
        this.rangeMax = buf.readInt();
        this.selectedTier = buf.readInt();
        this.maxEnergy = buf.readInt();
        this.soulXp = buf.readInt();
        this.soulCapacity = buf.readInt();
        this.buildName = buf.readUtf();
        this.buildSizeX = buf.readInt();
        this.buildSizeY = buf.readInt();
        this.buildSizeZ = buf.readInt();
        this.buildBlocks = buf.readInt();
        int matCount = buf.readVarInt();
        for (int i = 0; i < matCount; i++) {
            int itemId = buf.readVarInt();
            int count = buf.readVarInt();
            this.materials.add(new Material(
                    new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(itemId)), count));
        }
    }

    public java.util.List<Material> getMaterials() {
        return this.materials;
    }

    public int getSoulXp() {
        return this.soulXp;
    }

    public int getSoulCapacity() {
        return this.soulCapacity;
    }

    public boolean hasBuildPlan() {
        return this.buildBlocks > 0;
    }

    public String getBuildName() {
        return this.buildName;
    }

    public int getBuildSizeX() {
        return this.buildSizeX;
    }

    public int getBuildSizeY() {
        return this.buildSizeY;
    }

    public int getBuildSizeZ() {
        return this.buildSizeZ;
    }

    public int getBuildBlocks() {
        return this.buildBlocks;
    }

    private static MiningMech resolve(Inventory playerInventory, int entityId) {
        Entity entity = playerInventory.player.level().getEntity(entityId);
        return entity instanceof MiningMech mech ? mech : null;
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    public MiningMech getMech() {
        return this.mech;
    }

    public int getRangeMax() {
        return this.rangeMax;
    }

    /** The currently-selected range tier (kept in sync as the player picks from the GUI dropdown). */
    public int getSelectedTier() {
        return this.selectedTier;
    }

    public void setSelectedTier(int tier) {
        this.selectedTier = tier;
    }

    public int getMaxEnergy() {
        return this.maxEnergy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.mech == null) {
            return false;
        }
        if (id == 0) {
            this.mech.setWorkEnabled(!this.mech.isWorkEnabled());
            return true;
        }
        if (id == 1) {
            this.mech.setFollowEnabled(!this.mech.isFollowEnabled());
            return true;
        }
        if (id == 2) {
            this.mech.setAttackAll(!this.mech.isAttackAll());
            return true;
        }
        if (id == 3) {
            this.mech.setAreaVisible(!this.mech.isAreaVisible());
            return true;
        }
        if (id == 4) {
            this.mech.drainSoulTank(player);
            return true;
        }
        if (id >= RANGE_BUTTON_BASE) {
            this.mech.setRangeTier(id - RANGE_BUTTON_BASE);
            this.selectedTier = this.mech.getEffectiveRangeTier();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int total = this.slots.size();
            if (index < MECH_SLOTS) {
                if (!moveItemStackTo(stack, MECH_SLOTS, total, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, MECH_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.mech != null && this.mech.isAlive()
                && this.mech.distanceToSqr(player) < 64.0D
                && this.mech.isOwnedBy(player);
    }
}
