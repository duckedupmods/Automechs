package com.duckedupmods.automechs.block;

import com.duckedupmods.automechs.registry.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The Data Cable — a thin connecting conduit (a {@link PipeBlock}) that wires Data Racks and the Terminal
 * to a Main Drive. It grows an arm toward any adjacent cable, Main Drive or Data Rack, so a run of cable
 * reads as a real pipe rather than a wall of blocks. The Main Drive floods through it to map its network.
 */
public class DataCableBlock extends PipeBlock {

    public static final com.mojang.serialization.MapCodec<DataCableBlock> CODEC = simpleCodec(DataCableBlock::new);

    public DataCableBlock(Properties properties) {
        super(0.1875F, properties); // 6px-wide core
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, pos.relative(dir)));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, neighborPos));
    }

    /** Cables connect to other cables, the Main Drive, Data Racks, and the Storage Terminal (network nodes). */
    private static boolean connectsTo(LevelReader level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.is(ModBlocks.DATA_CABLE.get())
                || s.is(ModBlocks.MAIN_DRIVE.get())
                || s.is(ModBlocks.DATA_RACK.get())
                || s.is(ModBlocks.STORAGE_TERMINAL.get());
    }
}
