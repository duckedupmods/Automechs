package com.duckedupmods.automechs.datagen;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.CombustionDynamoBlock;
import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Generates blockstates and block models, plus the matching block-item models. {@code cubeAll}
 * textures every face from {@code automechs:block/<name>}.
 */
public class AutomechsBlockStateProvider extends BlockStateProvider {

    public AutomechsBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Automechs.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlockWithItem(ModBlocks.ASSEMBLY_WORKSHOP.get(), cubeAll(ModBlocks.ASSEMBLY_WORKSHOP.get()));
        // Data Cable is a hand-authored multipart pipe (core + connection arms) under resources/.
        registerCombustionDynamo();
        // The Upgrade Station is a GeckoLib ENTITYBLOCK_ANIMATED pedestal: its blockstate, empty particle
        // model, and 2D item model are hand-authored under resources/ (it has no datagen'd cube model).
    }

    /** Orientable generator: a front intake face that glows when {@code LIT}, rotated to {@code FACING}. */
    private void registerCombustionDynamo() {
        var dynamo = ModBlocks.COMBUSTION_DYNAMO.get();
        var side = modLoc("block/combustion_dynamo_side");
        var top = modLoc("block/combustion_dynamo_top");
        ModelFile off = models().orientable("combustion_dynamo", side, modLoc("block/combustion_dynamo_front"), top);
        ModelFile on = models().orientable("combustion_dynamo_on", side, modLoc("block/combustion_dynamo_front_on"), top);
        getVariantBuilder(dynamo).forAllStates(state -> {
            Direction facing = state.getValue(CombustionDynamoBlock.FACING);
            boolean lit = state.getValue(CombustionDynamoBlock.LIT);
            int yRot = ((int) facing.toYRot() + 180) % 360;
            return ConfiguredModel.builder().modelFile(lit ? on : off).rotationY(yRot).build();
        });
        simpleBlockItem(dynamo, off);
    }
}
