package com.duckedupmods.automechs.datagen;

import java.util.concurrent.CompletableFuture;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Block tags. Tags the Assembly Workshop so it mines correctly (pickaxe, needs iron tool).
 */
public class AutomechsBlockTagsProvider extends BlockTagsProvider {

    public AutomechsBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                      ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Automechs.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.ASSEMBLY_WORKSHOP.get())
                .add(ModBlocks.MECH_ASSEMBLY_BENCH.get())
                .add(ModBlocks.MECH_ASSEMBLY_STRUCTURE.get())
                .add(ModBlocks.UPGRADE_STATION.get())
                .add(ModBlocks.CHARGING_PAD.get())
                .add(ModBlocks.DATA_RACK.get())
                .add(ModBlocks.MAIN_DRIVE.get())
                .add(ModBlocks.DATA_CABLE.get())
                .add(ModBlocks.STORAGE_TERMINAL.get())
                .add(ModBlocks.COMBUSTION_DYNAMO.get())
                .add(ModBlocks.POWER_CONDUIT.get());
        tag(BlockTags.NEEDS_IRON_TOOL)
                .add(ModBlocks.ASSEMBLY_WORKSHOP.get())
                .add(ModBlocks.MECH_ASSEMBLY_BENCH.get())
                .add(ModBlocks.UPGRADE_STATION.get())
                .add(ModBlocks.CHARGING_PAD.get())
                .add(ModBlocks.DATA_RACK.get())
                .add(ModBlocks.MAIN_DRIVE.get())
                .add(ModBlocks.STORAGE_TERMINAL.get())
                .add(ModBlocks.COMBUSTION_DYNAMO.get());

        // Empty by default — packs can append blocks mechs must never mine.
        tag(ModTags.Blocks.MECH_UNMINEABLE);
    }
}
