package com.duckedupmods.automechs.registry;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.CacheCrawler;
import com.duckedupmods.automechs.entity.MiningMech;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All entity types registered by Automechs.
 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Automechs.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MiningMech>> MINING_MECH =
            ENTITY_TYPES.register("mining_mech", () -> EntityType.Builder.of(MiningMech::new, MobCategory.MISC)
                    .sized(0.7F, 1.0F)
                    .clientTrackingRange(10)
                    .build("mining_mech"));

    public static final DeferredHolder<EntityType<?>, EntityType<CacheCrawler>> CACHE_CRAWLER =
            ENTITY_TYPES.register("cache_crawler", () -> EntityType.Builder.of(CacheCrawler::new, MobCategory.MISC)
                    .sized(0.6F, 0.45F)
                    .clientTrackingRange(10)
                    .build("cache_crawler"));

    private ModEntities() {}

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
