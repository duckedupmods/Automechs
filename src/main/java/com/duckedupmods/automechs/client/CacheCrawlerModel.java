package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.CacheCrawler;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * GeckoLib model for the Cache Crawler. {@link DefaultedEntityGeoModel} auto-resolves
 * {@code geo/entity/cache_crawler.geo.json}, {@code animations/entity/cache_crawler.animation.json} and
 * {@code textures/entity/cache_crawler.png}.
 */
public class CacheCrawlerModel extends DefaultedEntityGeoModel<CacheCrawler> {

    public CacheCrawlerModel() {
        super(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "cache_crawler"));
    }
}
