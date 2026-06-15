package com.duckedupmods.automechs.block.entity;

import javax.annotation.Nullable;

import com.duckedupmods.automechs.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A "filler" cell of the Robot Builder's 3×3×3 multiblock. It is invisible and just remembers which
 * controller (the {@link MechAssemblyBenchBlockEntity}) owns it, so right-clicks route to the build menu
 * and breaking any cell tears the whole structure down. Holds no inventory of its own.
 */
public class MechAssemblyStructureBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos controller;

    public MechAssemblyStructureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECH_ASSEMBLY_STRUCTURE.get(), pos, state);
    }

    public void setController(BlockPos controller) {
        this.controller = controller.immutable();
        setChanged();
    }

    @Nullable
    public BlockPos getController() {
        return this.controller;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.controller != null) {
            tag.putInt("CtrlX", this.controller.getX());
            tag.putInt("CtrlY", this.controller.getY());
            tag.putInt("CtrlZ", this.controller.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CtrlX")) {
            this.controller = new BlockPos(tag.getInt("CtrlX"), tag.getInt("CtrlY"), tag.getInt("CtrlZ"));
        }
    }
}
