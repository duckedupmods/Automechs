package com.duckedupmods.automechs.item;

import java.util.List;

import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.registry.ModDataComponents;
import com.duckedupmods.automechs.registry.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Mech Chassis item deploys a {@link MiningMech} when used on a block face. Each chassis is bound
 * to a chassis {@link #tier}: deploying spawns a mech of that tier, and when a mech is packed up (or
 * destroyed) it returns the chassis item matching its current tier — so upgrading a mech and then
 * moving it never loses the upgrade.
 */
public class MechChassisItem extends Item {

    private final int tier;

    public MechChassisItem(Properties properties, int tier) {
        super(properties);
        this.tier = Math.max(1, Math.min(MiningMech.MAX_TIER, tier));
    }

    public int getTier() {
        return this.tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        MiningMech mech = ModEntities.MINING_MECH.get().create(serverLevel);
        if (mech == null) {
            return InteractionResult.FAIL;
        }

        mech.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                context.getRotation(), 0.0F);

        ItemStack stack = context.getItemInHand();
        applyChassisState(stack, mech);

        Player player = context.getPlayer();
        if (player != null) {
            mech.setOwner(player);
        }

        serverLevel.addFreshEntity(mech);
        stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    /** Read the role + upgrade components stamped on the chassis and apply them to the deployed mech. */
    private void applyChassisState(ItemStack stack, MiningMech mech) {
        String roleId = stack.get(ModDataComponents.MECH_ROLE.get());
        MechRole role = roleId != null ? MechRole.byId(roleId) : MechRole.defaultForTier(this.tier);
        mech.setRole(role);
        for (UpgradeType type : UpgradeType.values()) {
            int level = stack.getOrDefault(type.component().get(), 0);
            if (level > 0) {
                mech.setUpgradeLevel(type, level);
            }
        }
        mech.applyUpgrades();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String roleId = stack.get(ModDataComponents.MECH_ROLE.get());
        MechRole role = roleId != null ? MechRole.byId(roleId) : MechRole.defaultForTier(this.tier);
        tooltip.add(Component.translatable("tooltip.automechs.chassis_role",
                Component.translatable(role.translationKey())).withStyle(ChatFormatting.GRAY));
        for (UpgradeType type : UpgradeType.values()) {
            int level = stack.getOrDefault(type.component().get(), 0);
            if (level > 0) {
                tooltip.add(Component.translatable("tooltip.automechs.chassis_upgrade",
                        Component.translatable(type.translationKey()), level).withStyle(ChatFormatting.GREEN));
            }
        }
        tooltip.add(Component.translatable("tooltip.automechs.chassis_deploy")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
