package com.duckedupmods.automechs.entity.ai;

import java.util.EnumSet;

import com.duckedupmods.automechs.entity.MiningMech;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes the mech follow its owner: it starts moving once the owner is more than {@code startDistance}
 * away and stops within {@code stopDistance}. If the owner gets very far (e.g. teleported), the mech
 * teleports to a safe spot near them rather than getting stuck. Modeled on vanilla's
 * {@code FollowOwnerGoal} but without the {@code TamableAnimal} requirement.
 */
public class MechFollowOwnerGoal extends Goal {

    private final MiningMech mech;
    private final PathNavigation navigation;
    private final double speedModifier;
    private final float startDistance;
    private final float stopDistance;

    private LivingEntity owner;
    private int timeToRecalcPath;

    public MechFollowOwnerGoal(MiningMech mech, double speedModifier, float startDistance, float stopDistance) {
        this.mech = mech;
        this.navigation = mech.getNavigation();
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Follow is opt-in (off by default) and never runs while the mech is out of power.
        if (!this.mech.isFollowEnabled() || this.mech.isOffline()) {
            return false;
        }
        LivingEntity candidate = this.mech.getOwner();
        if (candidate == null || candidate.isSpectator()) {
            return false;
        }
        if (this.mech.distanceToSqr(candidate) < (double) (this.startDistance * this.startDistance)) {
            return false;
        }
        this.owner = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.navigation.isDone() || !this.mech.isFollowEnabled() || this.mech.isOffline()) {
            return false;
        }
        return this.owner != null
                && !this.owner.isSpectator()
                && this.mech.distanceToSqr(this.owner) > (double) (this.stopDistance * this.stopDistance);
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
    }

    @Override
    public void tick() {
        this.mech.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mech.getMaxHeadXRot());
        if (--this.timeToRecalcPath > 0) {
            return;
        }
        this.timeToRecalcPath = this.adjustedTickDelay(10);

        if (this.mech.isLeashed() || this.mech.isPassenger()) {
            return;
        }
        if (this.mech.distanceToSqr(this.owner) >= 144.0D) {
            teleportToOwner();
        } else {
            this.navigation.moveTo(this.owner, this.speedModifier);
        }
    }

    private void teleportToOwner() {
        BlockPos ownerPos = this.owner.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = randomIntInclusive(-3, 3);
            int dy = randomIntInclusive(-1, 1);
            int dz = randomIntInclusive(-3, 3);
            if (tryTeleportTo(ownerPos.getX() + dx, ownerPos.getY() + dy, ownerPos.getZ() + dz)) {
                return;
            }
        }
    }

    private boolean tryTeleportTo(int x, int y, int z) {
        if (Math.abs(x - this.owner.getX()) < 2.0D && Math.abs(z - this.owner.getZ()) < 2.0D) {
            return false;
        }
        BlockPos pos = new BlockPos(x, y, z);
        if (!isSafeStandingSpot(pos)) {
            return false;
        }
        this.mech.moveTo(x + 0.5D, y, z + 0.5D, this.mech.getYRot(), this.mech.getXRot());
        this.navigation.stop();
        return true;
    }

    private boolean isSafeStandingSpot(BlockPos pos) {
        Level level = this.mech.level();
        // Solid ground to stand on.
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) {
            return false;
        }
        // Clear space for the mech's bounding box at the target.
        BlockPos delta = pos.subtract(this.mech.blockPosition());
        return level.noCollision(this.mech, this.mech.getBoundingBox().move(delta));
    }

    private int randomIntInclusive(int min, int max) {
        return this.mech.getRandom().nextInt(max - min + 1) + min;
    }
}
