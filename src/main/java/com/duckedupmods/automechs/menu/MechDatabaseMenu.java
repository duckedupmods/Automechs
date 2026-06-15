package com.duckedupmods.automechs.menu;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.MechFolder;
import com.duckedupmods.automechs.item.MechNode;
import com.duckedupmods.automechs.item.MechTabletItem;
import com.duckedupmods.automechs.network.MechListPayload;
import com.duckedupmods.automechs.network.MechSummary;
import com.duckedupmods.automechs.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Menu for the Mech Tablet dashboard — a slotless container. While open, {@link #broadcastChanges()}
 * reads the registered robot nodes from the tablet the viewer is holding, resolves each to its live mech
 * (or marks it offline if unloaded), and pushes a {@link MechListPayload} so the dashboard stays current.
 * Per-node actions (pause/resume, recall, rename, group, remove) travel back as command/text payloads.
 */
public class MechDatabaseMenu extends AbstractContainerMenu {

    /** How many nodes a single payload will carry (matches the codec cap). */
    public static final int MAX_MECHS = 64;
    private static final int REFRESH_INTERVAL = 10;

    private final Player player;
    private List<MechSummary> mechs = List.of();
    private List<MechFolder> folders = List.of();
    private int refreshCooldown;

    public MechDatabaseMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.MECH_DATABASE.get(), containerId);
        this.player = playerInventory.player;
    }

    public List<MechSummary> getMechs() {
        return this.mechs;
    }

    public List<MechFolder> getFolders() {
        return this.folders;
    }

    /** Called on the client when a fresh list arrives over the network. */
    public void setData(List<MechSummary> mechs, List<MechFolder> folders) {
        this.mechs = mechs;
        this.folders = folders;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.player instanceof ServerPlayer serverPlayer && --this.refreshCooldown <= 0) {
            this.refreshCooldown = REFRESH_INTERVAL;
            List<MechFolder> folderList = MechTabletItem.folders(MechTabletItem.held(serverPlayer));
            PacketDistributor.sendToPlayer(serverPlayer, new MechListPayload(gather(serverPlayer), folderList));
        }
    }

    private List<MechSummary> gather(ServerPlayer viewer) {
        ItemStack tablet = MechTabletItem.held(viewer);
        List<MechNode> nodes = new ArrayList<>(MechTabletItem.nodes(tablet));
        ServerLevel level = (ServerLevel) viewer.level();
        List<MechSummary> out = new ArrayList<>();
        boolean rolesUpdated = false;

        for (int i = 0; i < nodes.size(); i++) {
            MechNode node = nodes.get(i);
            if (out.size() >= MAX_MECHS) {
                break;
            }
            Entity entity = level.getEntity(node.id());
            if (entity instanceof MiningMech mech && mech.isOwnedBy(viewer)) {
                int role = mech.getRole().ordinal();
                // Keep the persisted role in sync so the node shows the right badge once the mech unloads.
                if (node.role() != role) {
                    node = node.withRole(role);
                    nodes.set(i, node);
                    rolesUpdated = true;
                }
                out.add(new MechSummary(
                        node.id(), node.name(), node.group(), true,
                        role,
                        mech.isWorkEnabled(),
                        mech.isWorkEnabled() && mech.hasWorkArea(),
                        mech.getDisplayEnergy(),
                        mech.getEnergy().getMaxEnergyStored(),
                        mech.hasWorkArea(),
                        mech.blockPosition(),
                        mech.getUsedSlots(),
                        MiningMech.INVENTORY_SIZE,
                        node.x(), node.y()));
            } else {
                // Offline: fall back to the role we saved on the node, not a hardcoded default.
                out.add(new MechSummary(
                        node.id(), node.name(), node.group(), false,
                        node.role(), false, false, 0, 0, false, BlockPos.ZERO, 0, MiningMech.INVENTORY_SIZE,
                        node.x(), node.y()));
            }
        }
        if (rolesUpdated) {
            MechTabletItem.setNodes(tablet, nodes);
        }
        return out;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !MechTabletItem.held(player).isEmpty();
    }
}
