package com.duckedupmods.automechs.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Automechs Holo-Guide — an in-game manual. Right-click to project the holographic guide screen, a
 * paginated tutorial covering power, fabrication, each mech role, upgrades and management. Purely a
 * client-side UI: the screen is opened only on the client so the item is safe on a dedicated server (the
 * client class is never referenced from a server-reachable path).
 */
public class HoloGuideItem extends Item {

    public HoloGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openGuide();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Client-only: open the guide screen. Only ever called inside an {@code isClientSide} guard. */
    private static void openGuide() {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new com.duckedupmods.automechs.client.GuideScreen());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.automechs.holo_guide").withStyle(ChatFormatting.GRAY));
    }
}
