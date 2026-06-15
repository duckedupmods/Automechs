package com.duckedupmods.automechs.block;

import javax.annotation.Nullable;

import com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity;
import com.duckedupmods.automechs.block.entity.MechAssemblyStructureBlockEntity;
import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One invisible cell of the Robot Builder's 3×3×3 cube. It renders nothing (the controller draws the
 * whole oversized model), but it is solid so the machine occupies real space. All player interaction is
 * forwarded to the controller, and breaking any cell tears the entire structure down via the controller.
 */
public class MechAssemblyStructureBlock extends Block implements EntityBlock {

    public MechAssemblyStructureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    // Break/hit particle suppression for this invisible cell is registered client-side via
    // RegisterClientExtensionsEvent in AutomechsClient (the default effects would otherwise sample its
    // missing texture and spit out magenta "missingno" particles).

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechAssemblyStructureBlockEntity(pos, state);
    }

    @Nullable
    private static BlockPos controllerOf(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MechAssemblyStructureBlockEntity be) {
            return be.getController();
        }
        return null;
    }

    /** Right-clicking any cell opens the controller's build menu. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos controller = controllerOf(level, pos);
            if (controller != null && level.getBlockEntity(controller) instanceof MechAssemblyBenchBlockEntity bench) {
                serverPlayer.openMenu(bench, controller);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Pick-block on a cell yields the Robot Builder item. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, net.minecraft.world.phys.HitResult target, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(ModBlocks.MECH_ASSEMBLY_BENCH.get());
    }

    /** Mining a cell instead breaks the controller (which drops the builder + contents and cascades). */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !MechMultiblock.isDismantling()) {
            BlockPos controller = controllerOf(level, pos);
            if (controller != null && level.getBlockState(controller).is(ModBlocks.MECH_ASSEMBLY_BENCH.get())) {
                level.destroyBlock(controller, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Non-player removal (explosion, piston, /setblock) also tears down the whole structure. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !MechMultiblock.isDismantling()) {
            BlockPos controller = controllerOf(level, pos);
            if (controller != null && level.getBlockState(controller).is(ModBlocks.MECH_ASSEMBLY_BENCH.get())) {
                level.destroyBlock(controller, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
