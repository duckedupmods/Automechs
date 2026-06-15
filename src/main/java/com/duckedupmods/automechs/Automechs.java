package com.duckedupmods.automechs;

import org.slf4j.Logger;

import com.duckedupmods.automechs.entity.CacheCrawler;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModCreativeTabs;
import com.duckedupmods.automechs.registry.ModDataComponents;
import com.duckedupmods.automechs.registry.ModEntities;
import com.duckedupmods.automechs.registry.ModItems;
import com.duckedupmods.automechs.network.ModNetwork;
import com.duckedupmods.automechs.registry.ModMenus;
import com.duckedupmods.automechs.registry.ModRecipes;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Automechs — build, program, and power autonomous worker mechs.
 *
 * <p>Mod entrypoint. The value passed to {@link Mod} must match the {@code modId} in
 * {@code META-INF/neoforge.mods.toml}. All registration is performed through the
 * {@link com.duckedupmods.automechs.registry registry} package and bound to the mod event bus here —
 * no static-init registry writes.
 */
@Mod(Automechs.MODID)
public class Automechs {
    /** The mod id — referenced everywhere a namespace is needed. */
    public static final String MODID = "automechs";

    public static final Logger LOGGER = LogUtils.getLogger();

    public Automechs(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(ModNetwork::register);

        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Register the config spec so FML creates/loads the config file for us.
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Automechs common setup complete");
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.MINING_MECH.get(), MiningMech.createAttributes().build());
        event.put(ModEntities.CACHE_CRAWLER.get(), CacheCrawler.createAttributes().build());
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Expose the mech's internal stores so blocks/mods can interact with it (e.g. a Charging Pad
        // pushing FE in, or pipes pulling mined items out).
        event.registerEntity(Capabilities.ItemHandler.ENTITY, ModEntities.MINING_MECH.get(),
                (mech, context) -> mech.getInventory());
        event.registerEntity(Capabilities.EnergyStorage.ENTITY, ModEntities.MINING_MECH.get(),
                (mech, side) -> mech.getEnergy());

        // Charging Pad accepts FE from any cable.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.CHARGING_PAD.get(),
                (pad, side) -> pad.getEnergy());

        // Data Rack exposes its sectors so hoppers/pipes can pump items into (and out of) its storage.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.DATA_RACK.get(),
                (rack, side) -> rack.getSectors());

        // Main Drive accepts FE (from any cable/generator) to power its network.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MAIN_DRIVE.get(),
                (drive, side) -> drive.getEnergy());

        // Combustion Dynamo exposes its buffer so neighbours (and other mods' cables) can pull FE.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.COMBUSTION_DYNAMO.get(),
                (dynamo, side) -> dynamo.getEnergy());

        // Power Conduit exposes its buffer on every side so it can both receive and pass energy on.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.POWER_CONDUIT.get(),
                (conduit, side) -> conduit.getEnergy());

        // Mech Assembly Bench accepts FE (from a conduit) to power its builds.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MECH_ASSEMBLY_BENCH.get(),
                (bench, side) -> bench.getEnergy());

        // Robot Builder filler cells proxy the controller's energy, so a cable can connect to ANY edge
        // of the multiblock (not just the one controller block).
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.MECH_ASSEMBLY_STRUCTURE.get(),
                (structure, side) -> {
                    net.minecraft.core.BlockPos ctrl = structure.getController();
                    if (ctrl != null && structure.getLevel() != null
                            && structure.getLevel().getBlockEntity(ctrl)
                                    instanceof com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity bench) {
                        return bench.getEnergy();
                    }
                    return null;
                });

        // Fabricator (Assembly Workshop) accepts FE to power part fabrication.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.ASSEMBLY_WORKSHOP.get(),
                (fab, side) -> fab.getEnergy());

        // Upgrade Station accepts FE to power upgrade cycles.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ModBlockEntities.UPGRADE_STATION.get(),
                (station, side) -> station.getEnergy());
    }
}
