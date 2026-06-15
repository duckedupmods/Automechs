package com.duckedupmods.automechs;

import com.duckedupmods.automechs.client.AssemblyWorkshopScreen;
import com.duckedupmods.automechs.client.ChargingPadRenderer;
import com.duckedupmods.automechs.client.ChargingPadScreen;
import com.duckedupmods.automechs.client.CacheCrawlerRenderer;
import com.duckedupmods.automechs.client.CombustionDynamoScreen;
import com.duckedupmods.automechs.client.DataRackRenderer;
import com.duckedupmods.automechs.client.MainDriveRenderer;
import com.duckedupmods.automechs.client.MechAssemblyBenchRenderer;
import com.duckedupmods.automechs.client.MechAssemblyBenchScreen;
import com.duckedupmods.automechs.client.MechDatabaseScreen;
import com.duckedupmods.automechs.client.MechScreen;
import com.duckedupmods.automechs.client.MiningMechRenderer;
import com.duckedupmods.automechs.client.GeoBlockItemRenderer;
import com.duckedupmods.automechs.client.TerminalRenderer;
import com.duckedupmods.automechs.client.TerminalScreen;
import com.duckedupmods.automechs.client.UpgradeStationRenderer;
import com.duckedupmods.automechs.client.UpgradeStationScreen;
import com.duckedupmods.automechs.block.entity.ChargingPadBlockEntity;
import com.duckedupmods.automechs.block.entity.MechAssemblyBenchBlockEntity;
import com.duckedupmods.automechs.block.entity.TerminalBlockEntity;
import com.duckedupmods.automechs.block.entity.UpgradeStationBlockEntity;
import com.duckedupmods.automechs.registry.ModBlockEntities;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModEntities;
import com.duckedupmods.automechs.registry.ModItems;
import com.duckedupmods.automechs.registry.ModMenus;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entrypoint. This class is never loaded on a dedicated server, so client-only code
 * (rendering, models, screens) is safe to touch here.
 */
@Mod(value = Automechs.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Automechs.MODID, value = Dist.CLIENT)
public class AutomechsClient {
    public AutomechsClient(ModContainer container) {
        // Lets NeoForge build a config screen for this mod (Mods screen > Automechs > Config).
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Automechs.LOGGER.info("Automechs client setup complete");
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MINING_MECH.get(), MiningMechRenderer::new);
        event.registerEntityRenderer(ModEntities.CACHE_CRAWLER.get(), CacheCrawlerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MECH_ASSEMBLY_BENCH.get(), MechAssemblyBenchRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.UPGRADE_STATION.get(), UpgradeStationRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CHARGING_PAD.get(), ChargingPadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_RACK.get(), DataRackRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAIN_DRIVE.get(), MainDriveRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STORAGE_TERMINAL.get(), TerminalRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // The Robot Builder's invisible filler cells have no texture; their default break/hit particles
        // would sample the missing texture and render as magenta. Suppress them entirely.
        event.registerBlock(new IClientBlockExtensions() {
            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                return true;
            }

            @Override
            public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
                return true;
            }
        }, ModBlocks.MECH_ASSEMBLY_STRUCTURE.get());

        // Placeable machine blocks render their actual GeckoLib model in the inventory/hand (no flat icon)
        // via a BlockEntityWithoutLevelRenderer that reuses the block's GeoBlockRenderer.
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            new ChargingPadBlockEntity(BlockPos.ZERO, ModBlocks.CHARGING_PAD.get().defaultBlockState()),
                            new ChargingPadRenderer(null));
                }
                return this.renderer;
            }
        }, ModItems.CHARGING_PAD.get());

        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            new UpgradeStationBlockEntity(BlockPos.ZERO, ModBlocks.UPGRADE_STATION.get().defaultBlockState()),
                            new UpgradeStationRenderer(null));
                }
                return this.renderer;
            }
        }, ModItems.UPGRADE_STATION.get());

        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            new MechAssemblyBenchBlockEntity(BlockPos.ZERO, ModBlocks.MECH_ASSEMBLY_BENCH.get().defaultBlockState()),
                            new MechAssemblyBenchRenderer(null));
                }
                return this.renderer;
            }
        }, ModItems.MECH_ASSEMBLY_BENCH.get());

        // Storage blocks show their 3D model in the inventory too (consistent with the machines above).
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            new com.duckedupmods.automechs.block.entity.DataRackBlockEntity(
                                    BlockPos.ZERO, ModBlocks.DATA_RACK.get().defaultBlockState()),
                            new DataRackRenderer(null));
                }
                return this.renderer;
            }
        }, ModItems.DATA_RACK.get());

        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            new com.duckedupmods.automechs.block.entity.MainDriveBlockEntity(
                                    BlockPos.ZERO, ModBlocks.MAIN_DRIVE.get().defaultBlockState()),
                            new MainDriveRenderer(null));
                }
                return this.renderer;
            }
        }, ModItems.MAIN_DRIVE.get());

        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoBlockItemRenderer<>(
                            new com.duckedupmods.automechs.block.entity.TerminalBlockEntity(
                                    BlockPos.ZERO, ModBlocks.STORAGE_TERMINAL.get().defaultBlockState()),
                            new TerminalRenderer(null));
                }
                return this.renderer;
            }
        }, ModItems.STORAGE_TERMINAL.get());
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.MECH.get(), MechScreen::new);
        event.register(ModMenus.ASSEMBLY_WORKSHOP.get(), AssemblyWorkshopScreen::new);
        event.register(ModMenus.MECH_ASSEMBLY_BENCH.get(), MechAssemblyBenchScreen::new);
        event.register(ModMenus.COMBUSTION_DYNAMO.get(), CombustionDynamoScreen::new);
        event.register(ModMenus.UPGRADE_STATION.get(), UpgradeStationScreen::new);
        event.register(ModMenus.CHARGING_PAD.get(), ChargingPadScreen::new);
        event.register(ModMenus.STORAGE_TERMINAL.get(), TerminalScreen::new);
        event.register(ModMenus.MECH_DATABASE.get(), MechDatabaseScreen::new);
    }
}
