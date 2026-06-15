package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws a persistent in-world boundary around every loaded mining/farming mech's assigned work area, so
 * the player can see where a quarry digs (cyan) or a field is tended (green) at a glance — the same read
 * the placement preview gives, but kept up while the mech works. The corners are synced from the server
 * via {@link MiningMech#getSyncedAreaMin()} / {@link MiningMech#getSyncedAreaMax()}.
 *
 * <p>Combat (guard radius) and Building (its own translucent ghost via {@link SchematicGhost}) draw their
 * own markers elsewhere, so this only handles mining + farming.
 */
@EventBusSubscriber(modid = Automechs.MODID, value = Dist.CLIENT)
public final class WorkAreaRenderer {

    /** Skip boxes whose centre is further than this from the camera (cheap cull; lines are depth-tested). */
    private static final double MAX_DIST_SQR = 160.0D * 160.0D;

    private WorkAreaRenderer() {}

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        boolean drewAny = false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof MiningMech mech)) {
                continue;
            }
            MechRole role = mech.getRole();
            if (role != MechRole.MINING && role != MechRole.FARMING) {
                continue;
            }
            if (!mech.isAreaVisible()) {
                continue; // player hid this mech's outline
            }
            BlockPos min = mech.getSyncedAreaMin();
            BlockPos max = mech.getSyncedAreaMax();
            if (min == null || max == null) {
                continue;
            }
            AABB box = new AABB(min.getX(), min.getY(), min.getZ(),
                    max.getX() + 1, max.getY() + 1, max.getZ() + 1);
            if (box.getCenter().distanceToSqr(cam) > MAX_DIST_SQR) {
                continue;
            }
            if (role == MechRole.FARMING) {
                LevelRenderer.renderLineBox(pose, lines, box, 0.35F, 0.9F, 0.4F, 0.85F);   // green field
            } else {
                LevelRenderer.renderLineBox(pose, lines, box, 0.25F, 0.85F, 0.95F, 0.85F); // cyan quarry
            }
            drewAny = true;
        }

        pose.popPose();
        if (drewAny) {
            buffers.endBatch(RenderType.lines());
        }
    }
}
