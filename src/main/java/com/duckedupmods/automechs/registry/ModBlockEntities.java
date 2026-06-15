package com.duckedupmods.automechs.registry;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.block.entity.AssemblyWorkshopBlockEntity;
import com.duckedupmods.automechs.block.entity.ChargingPadBlockEntity;
import com.duckedupmods.automechs.block.entity.CombustionDynamoBlockEntity;
import com.duckedupmods.automechs.block.entity.DataRackBlockEntity;
import com.duckedupmods.automechs.block.entity.MainDriveBlockEntity;
import com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity;
import com.duckedupmods.automechs.block.entity.MechAssemblyStructureBlockEntity;
import com.duckedupmods.automechs.block.entity.PowerConduitBlockEntity;
import com.duckedupmods.automechs.block.entity.TerminalBlockEntity;
import com.duckedupmods.automechs.block.entity.UpgradeStationBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All block entity types registered by Automechs.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Automechs.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChargingPadBlockEntity>> CHARGING_PAD =
            BLOCK_ENTITIES.register("charging_pad", () -> BlockEntityType.Builder
                    .of(ChargingPadBlockEntity::new, ModBlocks.CHARGING_PAD.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyWorkshopBlockEntity>> ASSEMBLY_WORKSHOP =
            BLOCK_ENTITIES.register("assembly_workshop", () -> BlockEntityType.Builder
                    .of(AssemblyWorkshopBlockEntity::new, ModBlocks.ASSEMBLY_WORKSHOP.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechAssemblyBenchBlockEntity>> MECH_ASSEMBLY_BENCH =
            BLOCK_ENTITIES.register("mech_assembly_bench", () -> BlockEntityType.Builder
                    .of(MechAssemblyBenchBlockEntity::new, ModBlocks.MECH_ASSEMBLY_BENCH.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechAssemblyStructureBlockEntity>> MECH_ASSEMBLY_STRUCTURE =
            BLOCK_ENTITIES.register("mech_assembly_structure", () -> BlockEntityType.Builder
                    .of(MechAssemblyStructureBlockEntity::new, ModBlocks.MECH_ASSEMBLY_STRUCTURE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CombustionDynamoBlockEntity>> COMBUSTION_DYNAMO =
            BLOCK_ENTITIES.register("combustion_dynamo", () -> BlockEntityType.Builder
                    .of(CombustionDynamoBlockEntity::new, ModBlocks.COMBUSTION_DYNAMO.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerConduitBlockEntity>> POWER_CONDUIT =
            BLOCK_ENTITIES.register("power_conduit", () -> BlockEntityType.Builder
                    .of(PowerConduitBlockEntity::new, ModBlocks.POWER_CONDUIT.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UpgradeStationBlockEntity>> UPGRADE_STATION =
            BLOCK_ENTITIES.register("upgrade_station", () -> BlockEntityType.Builder
                    .of(UpgradeStationBlockEntity::new, ModBlocks.UPGRADE_STATION.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DataRackBlockEntity>> DATA_RACK =
            BLOCK_ENTITIES.register("data_rack", () -> BlockEntityType.Builder
                    .of(DataRackBlockEntity::new, ModBlocks.DATA_RACK.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MainDriveBlockEntity>> MAIN_DRIVE =
            BLOCK_ENTITIES.register("main_drive", () -> BlockEntityType.Builder
                    .of(MainDriveBlockEntity::new, ModBlocks.MAIN_DRIVE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TerminalBlockEntity>> STORAGE_TERMINAL =
            BLOCK_ENTITIES.register("storage_terminal", () -> BlockEntityType.Builder
                    .of(TerminalBlockEntity::new, ModBlocks.STORAGE_TERMINAL.get())
                    .build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
