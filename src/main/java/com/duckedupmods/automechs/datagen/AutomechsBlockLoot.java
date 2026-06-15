package com.duckedupmods.automechs.datagen;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Block loot tables. Every Automechs block drops itself when broken.
 */
public class AutomechsBlockLoot extends BlockLootSubProvider {

    public AutomechsBlockLoot(HolderLookup.Provider registries) {
        super(Set.<Item>of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.ASSEMBLY_WORKSHOP.get());
        dropSelf(ModBlocks.CHARGING_PAD.get());
        dropSelf(ModBlocks.DATA_RACK.get());
        dropSelf(ModBlocks.MAIN_DRIVE.get());
        dropSelf(ModBlocks.DATA_CABLE.get());
        dropSelf(ModBlocks.STORAGE_TERMINAL.get());
        dropSelf(ModBlocks.MECH_ASSEMBLY_BENCH.get());
        dropSelf(ModBlocks.UPGRADE_STATION.get());
        dropSelf(ModBlocks.COMBUSTION_DYNAMO.get());
        dropSelf(ModBlocks.POWER_CONDUIT.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(holder -> (Block) holder.get())
                .collect(Collectors.toList());
    }
}
