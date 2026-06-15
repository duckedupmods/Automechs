package com.duckedupmods.automechs.client;

import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.network.SetBuildAreaPayload;
import com.duckedupmods.automechs.network.SetFarmAreaPayload;
import com.duckedupmods.automechs.network.SetQuarryPayload;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side work-area placement, shared by mining (a deep <em>quarry</em> box) and farming (a flat
 * <em>field</em> slab). The mech screen's button starts a session: an in-world outline follows the
 * player's aim, left-click confirms (sends the matching payload), right-click cancels. The footprint is
 * computed from the selected Range tier so the preview matches the assigned area exactly.
 */
@EventBusSubscriber(modid = Automechs.MODID, value = Dist.CLIENT)
public final class QuarryPlacer {

    private static UUID mechId;
    private static int extentLo; // blocks from the centre on the −X/−Z side
    private static int extentHi; // blocks from the centre on the +X/+Z side
    private static boolean farmMode;
    private static boolean buildMode;
    private static BlockPos center;

    // Builder ghost: the schematic's bounding size, the rotation, and the free-float controls — how far in
    // front of the player it sits (scroll) and a vertical nudge (X up / Z down). It floats at the aim point
    // rather than snapping to a block, so the player can place it mid-air, on a roof, anywhere.
    private static int buildSx, buildSy, buildSz;
    private static Rotation buildRot = Rotation.NONE;
    private static String buildName = "";
    private static int buildEntityId;
    private static double buildDistance = 6.0D;
    private static int buildOffsetY;

    private QuarryPlacer() {}

    public static boolean isActive() {
        return mechId != null;
    }

    /** Begin placing a quarry box for a mining mech at the given selected size tier. */
    public static void beginQuarry(MiningMech mech, int tier) {
        mechId = mech.getUUID();
        farmMode = false;
        buildMode = false;
        int radius = Config.QUARRY_BASE_RADIUS.get() + Math.max(0, tier);
        extentLo = radius;
        extentHi = radius;
        center = null;
    }

    /** Begin placing a flat farm field (2×2 … 8×8) for a farming mech at the given selected size tier. */
    public static void beginFarm(MiningMech mech, int tier) {
        mechId = mech.getUUID();
        farmMode = true;
        buildMode = false;
        int side = com.duckedupmods.automechs.entity.ai.MechFarmGoal.farmSide(tier);
        extentLo = (side - 1) / 2;
        extentHi = side / 2;
        center = null;
    }

    /** Begin placing a Builder schematic ghost of the given bounding size. R rotates, scroll moves it, X/Z height. */
    public static void beginBuild(MiningMech mech, int sx, int sy, int sz, String name) {
        mechId = mech.getUUID();
        buildEntityId = mech.getId();
        farmMode = false;
        buildMode = true;
        buildSx = sx;
        buildSy = sy;
        buildSz = sz;
        buildRot = Rotation.NONE;
        buildName = name;
        buildDistance = Math.max(4.0D, (Math.max(sx, sz)) * 0.6D + 4.0D); // start a bit beyond the footprint
        buildOffsetY = 0;
        center = null;
    }

    /** Footprint width (X) after rotation — X and Z swap for the 90°/270° turns. */
    private static int rotW() {
        return (buildRot == Rotation.CLOCKWISE_90 || buildRot == Rotation.COUNTERCLOCKWISE_90) ? buildSz : buildSx;
    }

    private static int rotD() {
        return (buildRot == Rotation.CLOCKWISE_90 || buildRot == Rotation.COUNTERCLOCKWISE_90) ? buildSx : buildSz;
    }

    /** The anchor (min corner): the structure is centred horizontally on the aim point, nudged by the Y offset. */
    private static BlockPos buildAnchor() {
        if (center == null) {
            return null;
        }
        return new BlockPos(center.getX() - rotW() / 2, center.getY() + buildOffsetY, center.getZ() - rotD() / 2);
    }

    public static void cancel() {
        mechId = null;
        center = null;
    }

    /**
     * On disconnect / world exit: end any in-progress placement and drop the cached schematic ghost, so stale
     * client state (a placement session, a multi-MB blueprint, a previous world's entity id) can't bleed into
     * the next world. Fires for both single-player exit and multiplayer disconnect.
     */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel();
        SchematicGhost.clear();
    }

    private static void confirm() {
        if (center != null) {
            String setMsg;
            if (buildMode) {
                BlockPos anchor = buildAnchor();
                PacketDistributor.sendToServer(new SetBuildAreaPayload(mechId, anchor, buildRot.ordinal()));
                SchematicGhost.markPlaced(buildEntityId, anchor, buildRot); // keep showing it while it builds
                setMsg = "message.automechs.build_set";
            } else if (farmMode) {
                PacketDistributor.sendToServer(new SetFarmAreaPayload(mechId, center));
                setMsg = "message.automechs.farm_set";
            } else {
                PacketDistributor.sendToServer(new SetQuarryPayload(mechId, center));
                setMsg = "message.automechs.quarry_set";
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(setMsg), true);
            }
        }
        cancel();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition(1.0F);
        if (buildMode) {
            // Free-float: the ghost sits at a scroll-controlled distance in front of the player's view, so it
            // can be placed mid-air or above terrain. X/Z nudge it vertically; it never snaps to a block.
            Vec3 aim = eye.add(mc.player.getViewVector(1.0F).scale(buildDistance));
            center = BlockPos.containing(aim);
            mc.player.displayClientMessage(Component.translatable("message.automechs.build_aim", buildName), true);
            return;
        }
        // Raycast past normal reach so the player can place the area a little way off. Farming uses a
        // COLLIDER clip so it passes through crops/grass and lands on the solid ground beneath.
        Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(48.0D));
        ClipContext.Block clipBlock = farmMode ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE;
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end,
                clipBlock, ClipContext.Fluid.NONE, mc.player));
        center = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        mc.player.displayClientMessage(Component.translatable(
                farmMode ? "message.automechs.farm_aim" : "message.automechs.quarry_aim"), true);
    }

    /** While placing a Builder ghost: R spins it 90°, X raises it, Z lowers it. */
    @SubscribeEvent
    static void onKey(InputEvent.Key event) {
        if (!isActive() || !buildMode || Minecraft.getInstance().screen != null) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS && event.getAction() != GLFW.GLFW_REPEAT) {
            return;
        }
        switch (event.getKey()) {
            case GLFW.GLFW_KEY_R -> buildRot = buildRot.getRotated(Rotation.CLOCKWISE_90);
            case GLFW.GLFW_KEY_X -> buildOffsetY++;
            case GLFW.GLFW_KEY_Z -> buildOffsetY--;
            default -> { }
        }
    }

    /** Scroll moves the Builder ghost nearer/farther in front of the player (instead of scrolling the hotbar). */
    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!isActive() || !buildMode || Minecraft.getInstance().screen != null) {
            return;
        }
        buildDistance = Math.max(2.0D, Math.min(48.0D, buildDistance + event.getScrollDeltaY()));
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onMouse(InputEvent.MouseButton.Pre event) {
        if (!isActive() || Minecraft.getInstance().screen != null || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            confirm();
            event.setCanceled(true);
        } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cancel();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        PoseStack ghostPose = event.getPoseStack();
        Vec3 ghostCam = event.getCamera().getPosition();

        // Persistent ghost of a placed, still-building schematic (only the blocks not yet built) — runs
        // whether or not we're actively positioning a new ghost.
        SchematicGhost.renderPlaced(ghostPose, ghostCam, mc.level);

        if (!isActive() || center == null) {
            return;
        }
        AABB box;
        if (buildMode) {
            // The structure's bounding box, pinned with its min corner one above the aimed surface.
            BlockPos a = buildAnchor();
            box = new AABB(
                    a.getX(), a.getY(), a.getZ(),
                    a.getX() + rotW(), a.getY() + buildSy, a.getZ() + rotD());
        } else if (farmMode) {
            // One-block-tall field plane, sitting on the soil (the crop layer above the clicked ground).
            box = new AABB(
                    center.getX() - extentLo, center.getY() + 1, center.getZ() - extentLo,
                    center.getX() + extentHi + 1, center.getY() + 2, center.getZ() + extentHi + 1);
        } else {
            int floor = Math.max(mc.level.getMinBuildHeight() + 1, center.getY() - Config.QUARRY_MAX_DEPTH.get());
            box = new AABB(
                    center.getX() - extentLo, floor, center.getZ() - extentLo,
                    center.getX() + extentHi + 1, center.getY() + 1, center.getZ() + extentHi + 1);
        }

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        // Builder: draw the actual structure as a translucent ghost (if its blueprint is cached client-side)
        // so the player sees the house/blocks, not just a box. The amber outline below frames it either way.
        if (buildMode) {
            SchematicGhost.renderPlacing(pose, cam, mechId, buildAnchor(), buildRot);
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // Amber for a build ghost, green for farm, cyan for quarry — a quick read of which job is placed.
        if (buildMode) {
            LevelRenderer.renderLineBox(pose, lines, box, 1.0F, 0.66F, 0.18F, 0.9F);
        } else if (farmMode) {
            LevelRenderer.renderLineBox(pose, lines, box, 0.35F, 0.9F, 0.4F, 0.9F);
        } else {
            LevelRenderer.renderLineBox(pose, lines, box, 0.25F, 0.85F, 0.95F, 0.9F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
