package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.block.entity.TerminalBlockEntity;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** GeckoLib block-entity renderer for the Storage Terminal. */
public class TerminalRenderer extends GeoBlockRenderer<TerminalBlockEntity> {

    public TerminalRenderer(BlockEntityRendererProvider.Context context) {
        super(new TerminalModel());
    }
}
