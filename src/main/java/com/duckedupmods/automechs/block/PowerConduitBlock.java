package com.duckedupmods.automechs.block;

import javax.annotation.Nullable;

import com.duckedupmods.automechs.block.entity.PowerConduitBlockEntity;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * The Power Conduit — a thin cable that carries Forge Energy. Extends {@link PipeBlock} for automatic
 * 6-way connection shapes: a side connects when its neighbour is another conduit or exposes an energy
 * capability (the Combustion Dynamo, Charging Pad, Assembly Bench, or another mod's machine). Energy
 * movement is handled by {@link PowerConduitBlockEntity}.
 */
public class PowerConduitBlock extends PipeBlock implements EntityBlock {

    public static final MapCodec<PowerConduitBlock> CODEC = simpleCodec(PowerConduitBlock::new);

    /** True while energy is actively flowing through this conduit; lights the core (set by the BE). */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public PowerConduitBlock(Properties properties) {
        super(0.1875F, properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<PowerConduitBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connects(level, pos, dir));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), connects(level, pos, dir));
    }

    /** A side connects to another conduit, or to any neighbour that exposes an energy capability. */
    private static boolean connects(LevelReader level, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        if (level.getBlockState(neighbor).getBlock() instanceof PowerConduitBlock) {
            return true;
        }
        if (level instanceof Level realLevel) {
            return realLevel.getCapability(Capabilities.EnergyStorage.BLOCK, neighbor, dir.getOpposite()) != null;
        }
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerConduitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.POWER_CONDUIT.get(), PowerConduitBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> type, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == type ? (BlockEntityTicker<A>) ticker : null;
    }
}
