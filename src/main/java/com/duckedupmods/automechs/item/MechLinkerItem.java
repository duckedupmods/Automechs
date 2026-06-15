package com.duckedupmods.automechs.item;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The Mech Linker programs a mech's work order:
 * <ul>
 *   <li>Right-click two blocks to mark the mining area's opposite corners.</li>
 *   <li>Shift-right-click a container to set the deposit chest.</li>
 *   <li>Right-click a mech to assign the selection (area + deposit) to it.</li>
 *   <li>Shift-right-click empty air / a non-container to clear the selection.</li>
 * </ul>
 * Selection is stored on the item stack via {@link ModDataComponents}.
 */
public class MechLinkerItem extends Item {

    public MechLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos().immutable();
        ItemStack stack = context.getItemInHand();

        if (player.isShiftKeyDown()) {
            IItemHandler container = Capabilities.ItemHandler.BLOCK.getCapability((ServerLevel) level, pos, null, null, (Direction) null);
            if (container != null) {
                stack.set(ModDataComponents.LINK_DEPOSIT.get(), pos);
                actionBar(player, "Deposit chest set: " + format(pos));
            } else {
                stack.remove(ModDataComponents.LINK_POS_1.get());
                stack.remove(ModDataComponents.LINK_POS_2.get());
                stack.remove(ModDataComponents.LINK_DEPOSIT.get());
                actionBar(player, "Linker selection cleared");
            }
            return InteractionResult.SUCCESS;
        }

        // Set area corners: first click sets corner 1 (and resets), second sets corner 2.
        boolean hasFirst = stack.has(ModDataComponents.LINK_POS_1.get());
        boolean hasSecond = stack.has(ModDataComponents.LINK_POS_2.get());
        if (!hasFirst || hasSecond) {
            stack.set(ModDataComponents.LINK_POS_1.get(), pos);
            stack.remove(ModDataComponents.LINK_POS_2.get());
            actionBar(player, "Corner 1 set: " + format(pos));
        } else {
            stack.set(ModDataComponents.LINK_POS_2.get(), pos);
            actionBar(player, "Corner 2 set: " + format(pos) + " — right-click a mech to assign");
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(target instanceof MiningMech mech)) {
            return InteractionResult.PASS;
        }

        BlockPos corner1 = stack.get(ModDataComponents.LINK_POS_1.get());
        BlockPos corner2 = stack.get(ModDataComponents.LINK_POS_2.get());
        BlockPos deposit = stack.get(ModDataComponents.LINK_DEPOSIT.get());

        // Shift-right-click the mech = bind only the deposit chest (works for quarry mechs too).
        if (player.isShiftKeyDown()) {
            if (deposit != null) {
                mech.setDepositPos(deposit);
                actionBar(player, "Deposit chest linked to mech: " + format(deposit));
            } else {
                actionBar(player, "Shift-right-click a chest first, then shift-right-click the mech");
            }
            return InteractionResult.SUCCESS;
        }

        if (corner1 == null || corner2 == null) {
            actionBar(player, "Set both area corners first");
            return InteractionResult.SUCCESS;
        }

        long volume = areaVolume(corner1, corner2);
        int maxVolume = Config.MAX_WORK_AREA_VOLUME.get();
        if (volume > maxVolume) {
            actionBar(player, "Work area too large: " + volume + " > " + maxVolume + " blocks");
            return InteractionResult.SUCCESS;
        }

        mech.setWorkOrder(corner1, corner2, deposit);
        mech.setWorkEnabled(true); // assigning a job starts it immediately
        actionBar(player, "Work order assigned & started (" + volume + " blocks"
                + (deposit != null ? ", deposit " + format(deposit) : ", no deposit chest") + ")");
        return InteractionResult.SUCCESS;
    }

    private static long areaVolume(BlockPos a, BlockPos b) {
        long dx = Math.abs(a.getX() - b.getX()) + 1L;
        long dy = Math.abs(a.getY() - b.getY()) + 1L;
        long dz = Math.abs(a.getZ() - b.getZ()) + 1L;
        return dx * dy * dz;
    }

    private static String format(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static void actionBar(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
