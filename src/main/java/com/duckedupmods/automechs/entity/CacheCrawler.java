package com.duckedupmods.automechs.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.duckedupmods.automechs.entity.ai.CrawlerDefragGoal;
import com.duckedupmods.automechs.registry.ModBlocks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Cache Crawler — a small spider-bot that scuttles over Data Racks and defragments their stored
 * "data". For now it is a simple wandering critter so the model and its idle/scuttle animations can be
 * tested; the defrag AI (assign to a rack via the Mech Linker, walk to it, compact its sectors) and the
 * FE/owner binding are wired in a later step.
 */
public class CacheCrawler extends PathfinderMob implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("work");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** WORK flag, synced to the client so the renderer's animation controller can see it (it runs client-side). */
    private static final EntityDataAccessor<Boolean> WORKING =
            SynchedEntityData.defineId(CacheCrawler.class, EntityDataSerializers.BOOLEAN);

    /** How far the bot may roam from its anchor (a nearby rack/drive) — kept small so it hugs the network. */
    private static final int HOME_RADIUS = 7;
    /** How far to look for a network block to anchor to. */
    private static final int ANCHOR_SCAN = 24;
    /** Re-evaluate the anchor on this cadence (ticks), so the bot follows the network if it grows/moves. */
    private static final int ANCHOR_INTERVAL = 60;

    private int anchorCooldown;
    private BlockPos anchor; // current network block we're tethered to (null = none found yet)

    public CacheCrawler(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(WORKING, false);
    }

    public void setWorking(boolean working) {
        this.entityData.set(WORKING, working);
    }

    public boolean isWorking() {
        return this.entityData.get(WORKING);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CrawlerDefragGoal(this));
        // High priority + brisk speed so it actively returns home instead of drifting off.
        this.goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 1.1D));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.7D, 60));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /**
     * Keep the bot anchored to the storage network: every {@link #ANCHOR_INTERVAL} ticks it re-homes to the
     * nearest Data Rack / Main Drive within {@link #ANCHOR_SCAN} and restricts itself to a tight
     * {@link #HOME_RADIUS} around it, so a swarm hugs the rack wall instead of wandering off. Falls back to
     * its current spot if no network block is nearby.
     */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.anchorCooldown-- > 0 && hasRestriction()) {
            return;
        }
        this.anchorCooldown = ANCHOR_INTERVAL;
        // Skip the expensive box scan while our current anchor is still a live network block — only
        // re-scan when the rack/drive we were tethered to has gone (broken/moved away).
        if (this.anchor != null && isNetworkBlock(this.anchor)) {
            return;
        }
        this.anchor = nearestNetworkBlock();
        restrictTo(this.anchor != null ? this.anchor : blockPosition(), HOME_RADIUS);
    }

    private boolean isNetworkBlock(BlockPos pos) {
        var state = level().getBlockState(pos);
        return state.is(ModBlocks.DATA_RACK.get()) || state.is(ModBlocks.MAIN_DRIVE.get());
    }

    /** Scan a box around the bot for the closest Data Rack or Main Drive to anchor to. */
    private BlockPos nearestNetworkBlock() {
        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -5; dy <= 5; dy++) {
            for (int dx = -ANCHOR_SCAN; dx <= ANCHOR_SCAN; dx++) {
                for (int dz = -ANCHOR_SCAN; dz <= ANCHOR_SCAN; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    var state = level().getBlockState(cursor);
                    if (!state.is(ModBlocks.DATA_RACK.get()) && !state.is(ModBlocks.MAIN_DRIVE.get())) {
                        continue;
                    }
                    double d = origin.distSqr(cursor);
                    if (d < bestDist) {
                        bestDist = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** A maintenance bot — it shouldn't wander off and despawn. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, state -> {
            if (isWorking()) {
                return state.setAndContinue(WORK);
            }
            // walkAnimation.isMoving() is the reliable "am I actually walking" signal (synced for entities).
            return state.setAndContinue(this.walkAnimation.isMoving() ? WALK : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
