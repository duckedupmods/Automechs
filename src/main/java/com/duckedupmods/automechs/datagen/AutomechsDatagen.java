package com.duckedupmods.automechs.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Datagen entrypoint. Listens for {@link GatherDataEvent} on the mod bus and registers every data
 * provider. All of {@code assets/} and {@code data/} for this mod is generated and reproducible —
 * nothing here is hand-written where datagen can produce it (an ATM requirement).
 *
 * <p>Run with {@code gradlew runData}; output lands in {@code src/generated/resources}, which is on
 * the resources source set.
 */
@EventBusSubscriber(modid = Automechs.MODID)
public class AutomechsDatagen {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        // Client assets
        generator.addProvider(event.includeClient(), new AutomechsBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new AutomechsItemModelProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new AutomechsLanguageProvider(output));

        // Server data
        generator.addProvider(event.includeServer(), new AutomechsRecipeProvider(output, lookup));
        generator.addProvider(event.includeServer(), new AutomechsBlockTagsProvider(output, lookup, existingFileHelper));
        generator.addProvider(event.includeServer(), new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(AutomechsBlockLoot::new, LootContextParamSets.BLOCK)),
                lookup));
    }
}
