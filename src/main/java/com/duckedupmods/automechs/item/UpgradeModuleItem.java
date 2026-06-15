package com.duckedupmods.automechs.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A stat-upgrade module — inserted into the Upgrade Station alongside a packed-up mech chassis to raise
 * one of the mech's stats by a level. One item per {@link UpgradeType}.
 */
public class UpgradeModuleItem extends Item {

    private final UpgradeType type;

    public UpgradeModuleItem(Properties properties, UpgradeType type) {
        super(properties);
        this.type = type;
    }

    public UpgradeType type() {
        return this.type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.type.descriptionKey()).withStyle(ChatFormatting.GRAY));
        String scopeKey = switch (this.type.category()) {
            case MINING -> "tooltip.automechs.scope_mining";
            case FARMING -> "tooltip.automechs.scope_farming";
            case COMBAT -> "tooltip.automechs.scope_combat";
            default -> "tooltip.automechs.scope_any";
        };
        tooltip.add(Component.translatable(scopeKey).withStyle(ChatFormatting.AQUA));
        if (this.type.usedByChargingPad()) {
            tooltip.add(Component.translatable("tooltip.automechs.scope_pad").withStyle(ChatFormatting.AQUA));
        }
        if (this.type.maxLevel() > 1) {
            tooltip.add(Component.translatable("tooltip.automechs.upgrade_max", this.type.maxLevel())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.automechs.upgrade_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
