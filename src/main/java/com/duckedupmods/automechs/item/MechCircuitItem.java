package com.duckedupmods.automechs.item;

import java.util.List;

import com.duckedupmods.automechs.entity.MechRole;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A role circuit — the part slotted into the Robot Builder's Circuit socket that decides the job (and
 * therefore the model) of the mech that gets built. One item per {@link MechRole}.
 */
public class MechCircuitItem extends Item {

    private final MechRole role;

    public MechCircuitItem(Properties properties, MechRole role) {
        super(properties);
        this.role = role;
    }

    public MechRole role() {
        return this.role;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.automechs.circuit_role",
                Component.translatable(this.role.translationKey())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.automechs.circuit_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
