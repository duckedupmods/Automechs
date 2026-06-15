package com.duckedupmods.automechs.network;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.MechFolder;
import com.duckedupmods.automechs.item.MechNode;
import com.duckedupmods.automechs.item.MechTabletItem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Registers Automechs' network payloads and their handlers. Wired to the mod event bus from the
 * {@link Automechs} constructor. Server-bound commands re-validate ownership / tablet possession; the
 * client-bound list handler is dispatched into a nested lambda so the client-only receiver class is
 * never loaded on a dedicated server.
 */
public final class ModNetwork {

    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Automechs.MODID).versioned("1");

        registrar.playToClient(MechListPayload.TYPE, MechListPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.duckedupmods.automechs.client.MechDatabaseClient.receive(payload)));

        registrar.playToServer(MechCommandPayload.TYPE, MechCommandPayload.STREAM_CODEC,
                ModNetwork::handleCommand);

        registrar.playToServer(MechTextPayload.TYPE, MechTextPayload.STREAM_CODEC,
                ModNetwork::handleText);

        registrar.playToServer(MechMovePayload.TYPE, MechMovePayload.STREAM_CODEC,
                ModNetwork::handleMove);

        registrar.playToServer(MechFolderPayload.TYPE, MechFolderPayload.STREAM_CODEC,
                ModNetwork::handleFolder);

        registrar.playToServer(SetQuarryPayload.TYPE, SetQuarryPayload.STREAM_CODEC,
                ModNetwork::handleQuarry);

        registrar.playToServer(SetFarmAreaPayload.TYPE, SetFarmAreaPayload.STREAM_CODEC,
                ModNetwork::handleFarmArea);

        registrar.playToServer(UploadSchematicPayload.TYPE, UploadSchematicPayload.STREAM_CODEC,
                ModNetwork::handleUploadSchematic);

        registrar.playToServer(SetBuildAreaPayload.TYPE, SetBuildAreaPayload.STREAM_CODEC,
                ModNetwork::handleBuildArea);

        registrar.playToClient(StorageSnapshotPayload.TYPE, StorageSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.duckedupmods.automechs.client.TerminalClient.receive(payload)));

        registrar.playToServer(StorageRequestPayload.TYPE, StorageRequestPayload.STREAM_CODEC,
                ModNetwork::handleStorageRequest);
    }

    /** Honour a Storage Terminal withdraw request: pull the item out of the network and hand it over. */
    private static void handleStorageRequest(StorageRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.containerMenu instanceof com.duckedupmods.automechs.menu.TerminalMenu menu) {
                menu.withdraw(player, payload.target(), payload.amount());
            }
        });
    }

    /** Re-parse an uploaded schematic server-side and stamp it onto the Builder mech (size-capped). */
    private static void handleUploadSchematic(UploadSchematicPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            MiningMech mech = resolve(player, payload.id());
            if (mech == null) {
                return;
            }
            try {
                com.duckedupmods.automechs.schematic.Blueprint plan =
                        com.duckedupmods.automechs.schematic.SchematicLoader.fromBytes(payload.data());
                int cap = com.duckedupmods.automechs.Config.BUILD_MAX_BLOCKS.get();
                if (plan.blockCount() > cap) {
                    player.displayClientMessage(Component.translatable(
                            "message.automechs.schematic_too_big", plan.blockCount(), cap), true);
                    return;
                }
                if (plan.blockCount() == 0) {
                    player.displayClientMessage(Component.translatable("message.automechs.schematic_empty"), true);
                    return;
                }
                String name = payload.name();
                mech.setSchematic(payload.data(), name, plan);
                player.displayClientMessage(Component.translatable(
                        "message.automechs.schematic_loaded", name, plan.blockCount()), true);
            } catch (Exception e) {
                player.displayClientMessage(Component.translatable("message.automechs.schematic_bad"), true);
            }
        });
    }

    /** Pin a loaded schematic to a world anchor + rotation and switch the Builder on. */
    private static void handleBuildArea(SetBuildAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            MiningMech mech = resolve(player, payload.id());
            if (mech == null || !mech.hasSchematic()) {
                return;
            }
            BlockPos anchor = payload.anchor();
            if (player.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) > 96.0 * 96.0) {
                return;
            }
            mech.setBuildAnchor(anchor);
            mech.setBuildRotation(payload.rotation());
            mech.setWorkEnabled(true);
        });
    }

    /** Assign a flat farm field: a square of the mech's selected Range tier, centred on the placed block. */
    private static void handleFarmArea(SetFarmAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            MiningMech mech = resolve(player, payload.id());
            if (mech == null) {
                return;
            }
            BlockPos center = payload.center();
            if (player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            int side = com.duckedupmods.automechs.entity.ai.MechFarmGoal.farmSide(mech.getEffectiveRangeTier());
            int lo = (side - 1) / 2;
            int hi = side / 2;
            int y = center.getY() + 1; // the crop layer sits one block above the clicked ground
            BlockPos min = new BlockPos(center.getX() - lo, y, center.getZ() - lo);
            BlockPos max = new BlockPos(center.getX() + hi, y, center.getZ() + hi);
            mech.setWorkOrder(min, max, mech.getDepositPos());
            mech.setWorkEnabled(true);
        });
    }

    /** Assign a quarry work order: footprint recomputed from the mech's Range, dug down to a config floor. */
    private static void handleQuarry(SetQuarryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            MiningMech mech = resolve(player, payload.id());
            if (mech == null) {
                return;
            }
            BlockPos center = payload.center();
            // Anti-grief: only accept placements reasonably close to the player.
            if (player.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) > 64.0 * 64.0) {
                return;
            }
            int radius = com.duckedupmods.automechs.Config.QUARRY_BASE_RADIUS.get() + mech.getEffectiveRangeTier();
            int floor = Math.max(player.level().getMinBuildHeight() + 1,
                    center.getY() - com.duckedupmods.automechs.Config.QUARRY_MAX_DEPTH.get());
            BlockPos min = new BlockPos(center.getX() - radius, floor, center.getZ() - radius);
            BlockPos max = new BlockPos(center.getX() + radius, center.getY(), center.getZ() + radius);
            mech.setWorkOrder(min, max, mech.getDepositPos());
            mech.setWorkEnabled(true);
        });
    }

    private static void handleFolder(MechFolderPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack tablet = MechTabletItem.held(player);
            if (tablet.isEmpty()) {
                return;
            }
            List<MechFolder> folders = new ArrayList<>(MechTabletItem.folders(tablet));
            String name = payload.name().trim();
            switch (payload.op()) {
                case MechFolderPayload.OP_CREATE -> {
                    if (!name.isEmpty() && folders.stream().noneMatch(f -> f.name().equals(name))) {
                        folders.add(new MechFolder(name, payload.x(), payload.y(), MechFolder.DEFAULT_W, MechFolder.DEFAULT_H));
                        MechTabletItem.setFolders(tablet, folders);
                    }
                }
                case MechFolderPayload.OP_MOVE -> {
                    for (int i = 0; i < folders.size(); i++) {
                        if (folders.get(i).name().equals(name)) {
                            folders.set(i, folders.get(i).withPos(payload.x(), payload.y()));
                            MechTabletItem.setFolders(tablet, folders);
                            break;
                        }
                    }
                }
                case MechFolderPayload.OP_RESIZE -> {
                    for (int i = 0; i < folders.size(); i++) {
                        if (folders.get(i).name().equals(name)) {
                            folders.set(i, folders.get(i).withSize(Math.max(70, payload.x()), Math.max(46, payload.y())));
                            MechTabletItem.setFolders(tablet, folders);
                            break;
                        }
                    }
                }
                case MechFolderPayload.OP_RENAME -> {
                    String to = payload.arg().trim();
                    if (to.isEmpty() || folders.stream().anyMatch(f -> f.name().equals(to))) {
                        return;
                    }
                    boolean changed = false;
                    for (int i = 0; i < folders.size(); i++) {
                        MechFolder f = folders.get(i);
                        if (f.name().equals(name)) {
                            folders.set(i, new MechFolder(to, f.x(), f.y(), f.w(), f.h()));
                            changed = true;
                        }
                    }
                    if (changed) {
                        MechTabletItem.setFolders(tablet, folders);
                        reassignGroup(tablet, name, to);
                    }
                }
                case MechFolderPayload.OP_DELETE -> {
                    if (folders.removeIf(f -> f.name().equals(name))) {
                        MechTabletItem.setFolders(tablet, folders);
                        reassignGroup(tablet, name, "");
                    }
                }
                default -> { }
            }
        });
    }

    /** Move every node currently filed under {@code from} to {@code to} ("" = ungrouped). */
    private static void reassignGroup(ItemStack tablet, String from, String to) {
        List<MechNode> nodes = new ArrayList<>(MechTabletItem.nodes(tablet));
        boolean changed = false;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).group().equals(from)) {
                nodes.set(i, nodes.get(i).withGroup(to));
                changed = true;
            }
        }
        if (changed) {
            MechTabletItem.setNodes(tablet, nodes);
        }
    }

    private static void handleMove(MechMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack tablet = MechTabletItem.held(player);
            if (tablet.isEmpty()) {
                return;
            }
            List<MechNode> nodes = new ArrayList<>(MechTabletItem.nodes(tablet));
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).id().equals(payload.id())) {
                    nodes.set(i, nodes.get(i).withPos(payload.x(), payload.y()));
                    MechTabletItem.setNodes(tablet, nodes);
                    break;
                }
            }
        });
    }

    private static MiningMech resolve(Player player, java.util.UUID id) {
        if (player.level() instanceof ServerLevel level) {
            Entity entity = level.getEntity(id);
            if (entity instanceof MiningMech mech && mech.isOwnedBy(player)) {
                return mech;
            }
        }
        return null;
    }

    private static void handleCommand(MechCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            switch (payload.action()) {
                case MechCommandPayload.ACTION_TOGGLE -> {
                    MiningMech mech = resolve(player, payload.id());
                    if (mech != null) {
                        mech.setWorkEnabled(!mech.isWorkEnabled());
                    }
                }
                case MechCommandPayload.ACTION_RECALL -> {
                    MiningMech mech = resolve(player, payload.id());
                    if (mech != null) {
                        mech.setWorkEnabled(false);
                        mech.teleportTo(player.getX(), player.getY(), player.getZ());
                    }
                }
                case MechCommandPayload.ACTION_UNREGISTER -> {
                    ItemStack tablet = MechTabletItem.held(player);
                    if (!tablet.isEmpty()) {
                        List<MechNode> nodes = new ArrayList<>(MechTabletItem.nodes(tablet));
                        nodes.removeIf(n -> n.id().equals(payload.id()));
                        MechTabletItem.setNodes(tablet, nodes);
                    }
                }
                default -> { }
            }
        });
    }

    private static void handleText(MechTextPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            ItemStack tablet = MechTabletItem.held(player);
            if (tablet.isEmpty()) {
                return;
            }
            String value = payload.value().trim();
            List<MechNode> nodes = new ArrayList<>(MechTabletItem.nodes(tablet));
            boolean changed = false;
            for (int i = 0; i < nodes.size(); i++) {
                MechNode node = nodes.get(i);
                if (!node.id().equals(payload.id())) {
                    continue;
                }
                if (payload.field() == MechTextPayload.FIELD_NAME) {
                    nodes.set(i, node.withName(value));
                    // Mirror the name onto the live mech so it floats above it in-world.
                    MiningMech mech = resolve(player, payload.id());
                    if (mech != null) {
                        mech.setCustomName(value.isEmpty() ? null : Component.literal(value));
                    }
                } else if (payload.field() == MechTextPayload.FIELD_GROUP) {
                    nodes.set(i, node.withGroup(value));
                    MiningMech mech = resolve(player, payload.id());
                    if (mech != null) {
                        mech.setGroup(value);
                    }
                }
                changed = true;
                break;
            }
            if (changed) {
                MechTabletItem.setNodes(tablet, nodes);
            }
        });
    }
}
