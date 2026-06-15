package com.duckedupmods.automechs.item;

import java.util.ArrayList;
import java.util.List;

import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.menu.MechDatabaseMenu;
import com.duckedupmods.automechs.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Mech Tablet — a handheld command center and the database itself. Shift + right-click a robot you
 * own to register it as a node (it remembers the robot's UUID + your name/folder for it). Right-click in
 * the air to open the dashboard, where each registered robot is a node you can rename, file into folders,
 * pause/resume, recall, or remove. The node list lives in the {@code tablet_nodes} data component, so the
 * database travels with the tablet.
 */
public class MechTabletItem extends Item {

    public MechTabletItem(Properties properties) {
        super(properties);
    }

    /** Returns the tablet the player is holding (main or off hand), or {@link ItemStack#EMPTY}. */
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().getItem() instanceof MechTabletItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof MechTabletItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public static List<MechNode> nodes(ItemStack tablet) {
        return tablet.getOrDefault(ModDataComponents.TABLET_NODES.get(), List.of());
    }

    public static void setNodes(ItemStack tablet, List<MechNode> nodes) {
        tablet.set(ModDataComponents.TABLET_NODES.get(), List.copyOf(nodes));
    }

    public static List<MechFolder> folders(ItemStack tablet) {
        return tablet.getOrDefault(ModDataComponents.TABLET_FOLDERS.get(), List.of());
    }

    public static void setFolders(ItemStack tablet, List<MechFolder> folders) {
        tablet.set(ModDataComponents.TABLET_FOLDERS.get(), List.copyOf(folders));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // Only shift-click registers; a plain click falls through to the mech's own interaction (its GUI).
        if (!player.isShiftKeyDown() || !(target instanceof MiningMech mech) || !mech.isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            // In creative, interactLivingEntity is handed a COPY of the held item (so creative doesn't
            // consume it). Write to the real hand stack instead, or the registration is silently lost.
            ItemStack tablet = player.getItemInHand(hand);
            List<MechNode> nodes = new ArrayList<>(nodes(tablet));
            boolean already = nodes.stream().anyMatch(n -> n.id().equals(mech.getUUID()));
            if (already) {
                player.displayClientMessage(Component.translatable("message.automechs.tablet_already")
                        .withStyle(ChatFormatting.YELLOW), true);
            } else {
                // Stagger new nodes in a grid so they don't stack on top of each other on the canvas.
                int index = nodes.size();
                int nx = 14 + (index % 3) * 104;
                int ny = 16 + (index / 3) * 78;
                nodes.add(new MechNode(mech.getUUID(), "", mech.getGroup(), mech.getRole().ordinal(), nx, ny));
                setNodes(tablet, nodes);
                player.displayClientMessage(Component.translatable("message.automechs.tablet_added")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Don't hijack a shift-click meant for registering on an entity.
        if (!level.isClientSide && !player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (containerId, inv, p) -> new MechDatabaseMenu(containerId, inv),
                    Component.translatable("item.automechs.mech_tablet"));
            serverPlayer.openMenu(provider);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.automechs.tablet_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.automechs.tablet_add").withStyle(ChatFormatting.DARK_GRAY));
        int count = nodes(stack).size();
        if (count > 0) {
            tooltip.add(Component.translatable("tooltip.automechs.tablet_count", count).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
