package com.duckedupmods.automechs.client;

import java.util.List;
import java.util.UUID;

import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.schematic.Blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only translucent preview of a Builder schematic: the actual block models, ghosted, rendered at the
 * placement anchor so the player can see exactly what (and where) the mech will build before committing. The
 * blueprint is cached when the player picks the file ({@link SchematicScreen}); {@link QuarryPlacer} draws it
 * while a build ghost is being positioned. Falls back to just the wireframe box when no blueprint is cached
 * (e.g. after a relog without re-picking).
 */
public final class SchematicGhost {

    private static final int GHOST_ALPHA = 130;           // ~50% — clearly a ghost, still readable
    private static final int MAX_BLOCKS = 80_000;         // safety cap on per-frame ghost block models
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private static final double VIEW_RANGE_SQR = 80.0D * 80.0D; // don't draw a placed ghost from across the map

    private static UUID mechId;
    private static Blueprint blueprint;

    // A committed placement, kept so the ghost keeps showing the remaining blocks while the mech builds.
    private static boolean placed;
    private static int placedEntityId;
    private static BlockPos placedAnchor;
    private static Rotation placedRot = Rotation.NONE;

    // Rotated-placement cache so we don't rebuild the (possibly huge) list every frame.
    private static Rotation cachedRot;
    private static Blueprint cachedBase;
    private static List<Blueprint.Placement> cachedPlacements;

    private SchematicGhost() {}

    /** Cache a freshly-picked blueprint for the given mech (client-side preview only). */
    public static void set(UUID id, Blueprint plan) {
        mechId = id;
        blueprint = plan;
        placed = false; // reconfiguring — drop any old committed placement
        cachedBase = null; // force rotation re-cache
    }

    /**
     * Drop all cached state. Called on world unload / disconnect so a (potentially multi-MB) blueprint and
     * its rotated placement list aren't pinned in static memory across worlds, and so a stale entity id can't
     * resolve to an unrelated mech in the next world.
     */
    public static void clear() {
        mechId = null;
        blueprint = null;
        placed = false;
        placedEntityId = 0;
        placedAnchor = null;
        placedRot = Rotation.NONE;
        cachedBase = null;
        cachedRot = null;
        cachedPlacements = null;
    }

    /** Remember a committed placement so the remaining-blocks ghost keeps showing while it's built. */
    public static void markPlaced(int entityId, BlockPos anchor, Rotation rot) {
        placedEntityId = entityId;
        placedAnchor = anchor;
        placedRot = rot;
        placed = anchor != null;
    }

    public static Blueprint forMech(UUID id) {
        return id != null && id.equals(mechId) ? blueprint : null;
    }

    private static void ensureRotated(Rotation rot) {
        if (cachedPlacements == null || cachedBase != blueprint || cachedRot != rot) {
            cachedBase = blueprint;
            cachedRot = rot;
            cachedPlacements = blueprint.rotated(rot).placements();
        }
    }

    /** While positioning a new ghost: draw the whole structure at {@code anchor}/{@code rot}. */
    public static void renderPlacing(PoseStack pose, Vec3 cam, UUID id, BlockPos anchor, Rotation rot) {
        if (blueprint == null || id == null || !id.equals(mechId) || anchor == null) {
            return;
        }
        draw(pose, cam, anchor, rot, null);
    }

    /**
     * Persistent ghost of a committed build: only the blocks not yet placed, so it shrinks as the mech works
     * and vanishes when done. Skips drawing if the mech is gone, switched off, or too far from the camera.
     */
    public static void renderPlaced(PoseStack pose, Vec3 cam, net.minecraft.world.level.Level level) {
        if (!placed || blueprint == null || placedAnchor == null) {
            return;
        }
        if (placedAnchor.distToCenterSqr(cam.x, cam.y, cam.z) > VIEW_RANGE_SQR) {
            return;
        }
        if (!(level.getEntity(placedEntityId) instanceof MiningMech mech)
                || !mech.isAlive() || !mech.isWorkEnabled() || mech.getRole() != MechRole.BUILDING) {
            return;
        }
        draw(pose, cam, placedAnchor, placedRot, level);
    }

    /** Draw the ghost; when {@code level} is non-null, cells already matching the world are skipped. */
    private static void draw(PoseStack pose, Vec3 cam, BlockPos anchor, Rotation rot, net.minecraft.world.level.Level level) {
        ensureRotated(rot);
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource parent = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        MultiBufferSource ghost = new GhostBuffers(new GhostConsumer(parent.getBuffer(type)));

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        int n = Math.min(cachedPlacements.size(), MAX_BLOCKS);
        for (int i = 0; i < n; i++) {
            Blueprint.Placement p = cachedPlacements.get(i);
            BlockState state = p.state();
            if (state.isAir()) {
                continue;
            }
            BlockPos wp = anchor.offset(p.pos());
            if (level != null && level.getBlockState(wp) == state) {
                continue; // already built — don't ghost it
            }
            pose.pushPose();
            pose.translate(wp.getX(), wp.getY(), wp.getZ());
            dispatcher.renderSingleBlock(state, pose, ghost, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
        parent.endBatch(type);
    }

    /** Routes every render layer the block model asks for into one translucent, alpha-forced buffer. */
    private record GhostBuffers(VertexConsumer consumer) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return this.consumer;
        }
    }

    /** Delegating vertex consumer that forces a fixed alpha, turning solid block models into a ghost. */
    private record GhostConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, GHOST_ALPHA);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }
    }
}
