package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.entity.CacheCrawler;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer for the Cache Crawler spider-bot. */
public class CacheCrawlerRenderer extends GeoEntityRenderer<CacheCrawler> {

    public CacheCrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, new CacheCrawlerModel());
        this.shadowRadius = 0.35F;
    }
}
