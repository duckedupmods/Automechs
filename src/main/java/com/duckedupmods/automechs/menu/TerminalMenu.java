package com.duckedupmods.automechs.menu;

import com.duckedupmods.automechs.block.entity.TerminalBlockEntity;
import com.duckedupmods.automechs.network.StorageSnapshotPayload;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Container menu for the Storage Terminal. The network item grid is virtual (drawn by the screen, served
 * by snapshot packets) — the only real slots here are the player inventory, so shift-clicking an item from
 * the player's bags inserts it into the network. While the menu is open, {@link #broadcastChanges()}
 * pushes a fresh {@link StorageSnapshotPayload} so the AE2-style grid stays live. Withdraws arrive as
 * separate request packets and are handled by {@link #withdraw}.
 */
public class TerminalMenu extends AbstractContainerMenu {

    public static final int PLAYER_X = 7;
    public static final int PLAYER_Y = 138;

    private static final int REFRESH_INTERVAL = 8;

    private final ContainerLevelAccess access;
    private final TerminalBlockEntity terminal; // server-side only (null on the client)
    private final Player player;
    private int refreshCooldown;

    // ---- client-side mirror of the last snapshot (set by TerminalClient) ----
    private java.util.List<com.duckedupmods.automechs.network.NetworkItem> clientItems = java.util.List.of();
    private boolean clientOnline;
    private int clientItemCount;
    private int clientRackCount;
    private int clientFe;

    public void setSnapshot(java.util.List<com.duckedupmods.automechs.network.NetworkItem> items,
                            boolean online, int itemCount, int rackCount, int fe) {
        this.clientItems = items;
        this.clientOnline = online;
        this.clientItemCount = itemCount;
        this.clientRackCount = rackCount;
        this.clientFe = fe;
    }

    public java.util.List<com.duckedupmods.automechs.network.NetworkItem> getClientItems() {
        return this.clientItems;
    }

    public boolean isClientOnline() {
        return this.clientOnline;
    }

    public int getClientItemCount() {
        return this.clientItemCount;
    }

    public int getClientRackCount() {
        return this.clientRackCount;
    }

    public int getClientFe() {
        return this.clientFe;
    }

    /** Server-side constructor. */
    public TerminalMenu(int containerId, Inventory playerInventory, TerminalBlockEntity be) {
        super(ModMenus.STORAGE_TERMINAL.get(), containerId);
        this.terminal = be;
        this.player = playerInventory.player;
        this.access = be != null
                ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                : ContainerLevelAccess.NULL;
        addPlayerInventory(playerInventory);
    }

    /** Client-side constructor. */
    public TerminalMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolve(playerInventory, buf.readBlockPos()));
    }

    private static TerminalBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        BlockEntity be = playerInventory.player.level().getBlockEntity(pos);
        return be instanceof TerminalBlockEntity terminal ? terminal : null;
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, 9 + row * 9 + col, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, PLAYER_X + col * 18, PLAYER_Y + 58));
        }
    }

    // ---- live snapshot push --------------------------------------------------

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // Periodic passive refresh (catches crawler defrags / other players); user actions push immediately.
        if (--this.refreshCooldown <= 0) {
            pushSnapshot();
        }
    }

    /** Send the network snapshot to the viewing player right now, and reset the passive-refresh timer. */
    private void pushSnapshot() {
        this.refreshCooldown = REFRESH_INTERVAL;
        if (this.terminal == null || !(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        var drive = this.terminal.findDrive();
        boolean online = drive != null && drive.isOnline();
        int fe = drive != null ? drive.getStoredEnergy() : 0;
        int racks = drive != null ? drive.liveRacks().size() : 0;
        PacketDistributor.sendToPlayer(serverPlayer,
                new StorageSnapshotPayload(this.terminal.snapshot(), online, this.terminal.networkItemCount(), racks, fe));
    }

    /** Withdraw up to a stack of {@code proto} from the network and give it to the player. */
    public void withdraw(Player player, ItemStack proto, int amount) {
        if (this.terminal == null || proto.isEmpty()) {
            return;
        }
        int capped = Math.min(Math.max(1, amount), proto.getMaxStackSize());
        ItemStack got = this.terminal.withdraw(proto, capped);
        if (got.isEmpty()) {
            return;
        }
        player.getInventory().add(got);
        if (!got.isEmpty()) {
            // add() mutates `got` to the remainder; a partial fit must not silently vanish.
            player.drop(got, false);
        }
        pushSnapshot(); // reflect the withdraw on the client immediately, no 8-tick wait
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Every real slot here is the player inventory; shift-click inserts that stack into the network.
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (this.terminal == null) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack before = inSlot.copy();
        ItemStack leftover = this.terminal.insert(inSlot.copy());
        if (leftover.getCount() == before.getCount()) {
            return ItemStack.EMPTY; // nothing went in (network full/offline)
        }
        slot.set(leftover.isEmpty() ? ItemStack.EMPTY : leftover);
        slot.onTake(player, before);
        pushSnapshot(); // reflect the insert on the client immediately, no 8-tick wait
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.STORAGE_TERMINAL.get());
    }
}
