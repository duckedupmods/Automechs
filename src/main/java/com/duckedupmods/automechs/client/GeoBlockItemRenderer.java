package com.duckedupmods.automechs.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Renders a GeckoLib block's animated model as its inventory/hand item, so a placeable machine shows the
 * actual 3D model in the slot rather than a flat icon. It reuses the block's existing
 * {@link GeoBlockRenderer} against a single dummy block entity. Wired up via {@code IClientItemExtensions}
 * in {@link AutomechsClient}; the item model JSON uses {@code builtin/entity} with block display transforms.
 */
public class GeoBlockItemRenderer<T extends BlockEntity & GeoAnimatable> extends BlockEntityWithoutLevelRenderer {

    private final T blockEntity;
    private final GeoBlockRenderer<T> renderer;

    public GeoBlockItemRenderer(T blockEntity, GeoBlockRenderer<T> renderer) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.blockEntity = blockEntity;
        this.renderer = renderer;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Give the dummy BE the CURRENT client level so GeckoLib can read a game-time for its idle anim.
        // Track the live level (not just set-once) so we never pin a previous world's level after a relog.
        Level current = Minecraft.getInstance().level;
        if (current != null && this.blockEntity.getLevel() != current) {
            this.blockEntity.setLevel(current);
        }
        pose.pushPose();
        try {
            this.renderer.render(this.blockEntity, 0.0F, pose, buffers, packedLight, packedOverlay);
        } catch (Exception ignored) {
            // An item-render failure must never crash the game; just skip it this frame.
        }
        pose.popPose();
    }
}
