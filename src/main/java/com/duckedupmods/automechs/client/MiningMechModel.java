package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MiningMech;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Tier-aware GeckoLib model for the Mining Mech. The geometry and texture are chosen by the entity's
 * chassis tier ({@link MiningMech#getTier()}); all tiers share one animation file (the bone names —
 * body, head, antenna, arms, legs — are identical across tiers, so the walk/idle animations apply to
 * every chassis).
 *
 * <p>GeckoLib 4.7 deprecated the single-argument {@code getModelResource}/{@code getTextureResource}
 * overrides in favour of two-argument variants, but left them {@code abstract}, so they must still be
 * implemented. The {@code deprecation} suppression below acknowledges that transitional API state.
 */
@SuppressWarnings("deprecation")
public class MiningMechModel extends GeoModel<MiningMech> {

    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "animations/entity/mining_mech.animation.json");

    @Override
    public ResourceLocation getModelResource(MiningMech mech) {
        return ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "geo/entity/" + baseName(mech) + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MiningMech mech) {
        return ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "textures/entity/" + baseName(mech) + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(MiningMech mech) {
        return ANIMATION;
    }

    /** Tier 1 keeps the base {@code mining_mech} asset names; higher tiers use {@code mining_mech_tN}. */
    private static String baseName(MiningMech mech) {
        int tier = mech.getModelTier();
        return tier <= 1 ? "mining_mech" : "mining_mech_t" + tier;
    }
}
