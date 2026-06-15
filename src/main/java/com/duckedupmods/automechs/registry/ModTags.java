package com.duckedupmods.automechs.registry;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Tags emitted/used by Automechs. {@link Blocks#MECH_UNMINEABLE} lets packs blacklist blocks from
 * being mined by mechs.
 */
public final class ModTags {

    public static final class Blocks {
        /** Blocks a mech must never mine. Empty by default; packs can append. */
        public static final TagKey<Block> MECH_UNMINEABLE = create("mech_unmineable");

        /** Ores a Mining mech applies its Efficiency (Fortune) bonus to. Aliased to the common {@code c:ores}. */
        public static final TagKey<Block> ORES = common("ores");

        /** Crops a Farming mech harvests/replants beyond the {@code CropBlock} auto-detection. */
        public static final TagKey<Block> MECH_HARVESTABLE = create("mech_harvestable");

        private static TagKey<Block> create(String name) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Automechs.MODID, name));
        }

        private static TagKey<Block> common(String name) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", name));
        }

        private Blocks() {}
    }

    private ModTags() {}
}
