package com.duckedupmods.automechs.registry;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.AssemblyWorkshopBlock;
import com.duckedupmods.automechs.block.ChargingPadBlock;
import com.duckedupmods.automechs.block.CombustionDynamoBlock;
import com.duckedupmods.automechs.block.DataCableBlock;
import com.duckedupmods.automechs.block.DataRackBlock;
import com.duckedupmods.automechs.block.MainDriveBlock;
import com.duckedupmods.automechs.block.MechAssemblyBenchBlock;
import com.duckedupmods.automechs.block.MechAssemblyStructureBlock;
import com.duckedupmods.automechs.block.PowerConduitBlock;
import com.duckedupmods.automechs.block.TerminalBlock;
import com.duckedupmods.automechs.block.UpgradeStationBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All blocks registered by Automechs. Registered to the mod event bus from the mod constructor.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Automechs.MODID);

    /** The Assembly Workshop — the crafting station where mech gear is built. */
    public static final DeferredBlock<AssemblyWorkshopBlock> ASSEMBLY_WORKSHOP = BLOCKS.register(
            "assembly_workshop",
            () -> new AssemblyWorkshopBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /** The Data Rack — a server cabinet that stores items as fragmented "data" (defragged by Cache Crawler bots). */
    public static final DeferredBlock<DataRackBlock> DATA_RACK = BLOCKS.register(
            "data_rack",
            () -> new DataRackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 6)
                    // GeckoLib block with an empty vanilla model — don't cull neighbour faces.
                    .noOcclusion()));

    /** The Main Drive — powered controller of a Data Rack network. */
    public static final DeferredBlock<MainDriveBlock> MAIN_DRIVE = BLOCKS.register(
            "main_drive",
            () -> new MainDriveBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 8)
                    .noOcclusion()));

    /** The Storage Terminal — AE2-style access console placed on the cable network. */
    public static final DeferredBlock<TerminalBlock> STORAGE_TERMINAL = BLOCKS.register(
            "storage_terminal",
            () -> new TerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    // GeckoLib block with an empty vanilla model — don't cull neighbour faces.
                    .noOcclusion()));

    /** The Data Cable — connects Racks + Terminal to a Main Drive. */
    public static final DeferredBlock<DataCableBlock> DATA_CABLE = BLOCKS.register(
            "data_cable",
            () -> new DataCableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 4)));

    /** The Charging Pad — feeds Forge Energy into nearby mechs. */
    public static final DeferredBlock<ChargingPadBlock> CHARGING_PAD = BLOCKS.register(
            "charging_pad",
            () -> new ChargingPadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 6)
                    // GeckoLib pad with an empty vanilla model — don't cull neighbour faces.
                    .noOcclusion()));

    /** The Mech Assembly Bench — animated GeckoLib fabrication rig where mechs are built. */
    public static final DeferredBlock<MechAssemblyBenchBlock> MECH_ASSEMBLY_BENCH = BLOCKS.register(
            "mech_assembly_bench",
            () -> new MechAssemblyBenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .noOcclusion()));

    /** Invisible filler cell of the Robot Builder's 3×3×3 cube — solid, routes interaction to the controller. */
    public static final DeferredBlock<MechAssemblyStructureBlock> MECH_ASSEMBLY_STRUCTURE = BLOCKS.register(
            "mech_assembly_structure",
            () -> new MechAssemblyStructureBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .noLootTable()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .pushReaction(PushReaction.BLOCK)));

    /** The Combustion Dynamo — burns furnace fuel into Forge Energy and pushes it to neighbours. */
    public static final DeferredBlock<CombustionDynamoBlock> COMBUSTION_DYNAMO = BLOCKS.register(
            "combustion_dynamo",
            () -> new CombustionDynamoBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(CombustionDynamoBlock.LIT) ? 13 : 0)));

    /** The Upgrade Station — stamps stat upgrades onto a packed-up mech chassis. */
    public static final DeferredBlock<UpgradeStationBlock> UPGRADE_STATION = BLOCKS.register(
            "upgrade_station",
            () -> new UpgradeStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> 5)
                    // GeckoLib pedestal: empty vanilla model, so don't let it cull neighbour faces
                    // (otherwise the ground/blocks around it render see-through holes).
                    .noOcclusion()));

    /** The Power Conduit — a cable that carries Forge Energy between machines. */
    public static final DeferredBlock<PowerConduitBlock> POWER_CONDUIT = BLOCKS.register(
            "power_conduit",
            () -> new PowerConduitBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
