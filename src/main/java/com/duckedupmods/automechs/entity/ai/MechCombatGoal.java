package com.duckedupmods.automechs.entity.ai;

import java.util.EnumSet;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.entity.MiningMech;
import com.duckedupmods.automechs.item.UpgradeType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The Combat mech's behavior: hold a guard post and fight mobs that come within its guard radius, then
 * return to post when the area is clear — so it doesn't chase across the map on a killing spree. It picks
 * up its kills' drops (see {@link MiningMech} loot vacuum) and, when full or idle for a while, carries
 * the loot to a linked chest. With Follow enabled the "post" is the owner instead of a fixed anchor.
 * Always active for combat mechs (so it can both fight and haul); players and other mechs are never hit.
 */
public class MechCombatGoal extends Goal {

    private static final int ATTACK_COOLDOWN = 18; // ticks between swings
    private static final double EXTRA_REACH = 2.5D; // generous melee reach (raised/offset targets)
    private static final double REACH_SQR = 9.0D; // chest interaction range
    private static final int DEPOSIT_DELAY_TICKS = 100; // ~5s idle with loot → take it to the chest

    private static final int SCAN_INTERVAL = 5; // ticks between target re-scans (the box query is heavy)
    private static final int PATH_INTERVAL = 5; // ticks between navigation re-paths toward a moving target

    private final MiningMech mech;
    private LivingEntity targetMob;
    private int cooldown;
    private int idleTicks;
    private int scanCooldown;
    private int pathCooldown;

    public MechCombatGoal(MiningMech mech) {
        this.mech = mech;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.TARGET));
    }

    /** Guard radius (blocks) for a combat size tier: 8 at base, +4 per tier. */
    public static int guardRadius(int tier) {
        return 8 + Math.max(0, tier) * 4;
    }

    private boolean enabled() {
        return Config.ENABLE_AUTOMECHS.get()
                && this.mech.getRole() == MechRole.COMBAT
                && this.mech.isWorkEnabled();
    }

    @Override
    public boolean canUse() {
        return enabled();
    }

    @Override
    public boolean canContinueToUse() {
        return enabled();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        this.mech.setTarget(null);
        this.targetMob = null;
        this.mech.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.mech.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.mech.getGuardAnchor() == null) {
            this.mech.setGuardAnchor(this.mech.blockPosition());
        }
        BlockPos home = homePos();

        // 1. Fight the nearest mob inside the guard zone. The entity-box query is expensive, so only
        //    re-scan every few ticks; between scans, keep the current target while it's still alive.
        if (--this.scanCooldown <= 0 || this.targetMob == null || !this.targetMob.isAlive()) {
            this.targetMob = findTarget(level, home);
            this.scanCooldown = SCAN_INTERVAL;
        }
        if (this.targetMob != null) {
            this.idleTicks = 0;
            this.mech.setTarget(this.targetMob);
            fight(level);
            return;
        }
        this.mech.setTarget(null);

        // 2. No target: haul loot to the chest when full or idle for a while.
        IItemHandler chest = depositChest(level);
        boolean loot = !isInventoryEmpty();
        this.idleTicks = loot ? this.idleTicks + 1 : 0;
        if (chest != null && loot && (isInventoryFull() || this.idleTicks >= DEPOSIT_DELAY_TICKS)) {
            deposit(level, chest);
            return;
        }

        // 3. Return to the guard post if we've strayed (chasing/hauling pulled us out); else stand watch.
        Vec3 h = Vec3.atCenterOf(home);
        double leash = this.mech.isFollowEnabled() ? 6.0D : guardRadius() + 3.0D;
        if (this.mech.distanceToSqr(h) > leash * leash) {
            this.mech.getNavigation().moveTo(h.x, h.y, h.z, 1.0D);
        } else {
            this.mech.getNavigation().stop();
        }
    }

    /** The post to guard: the owner when Follow is on (a mobile escort), else the fixed anchor. */
    private BlockPos homePos() {
        if (this.mech.isFollowEnabled()) {
            LivingEntity owner = this.mech.getOwner();
            if (owner != null) {
                return owner.blockPosition();
            }
        }
        return this.mech.getGuardAnchor();
    }

    private int guardRadius() {
        return guardRadius(this.mech.getEffectiveRangeTier());
    }

    private void fight(ServerLevel level) {
        this.mech.getLookControl().setLookAt(this.targetMob, 30.0F, 30.0F);
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        double reach = EXTRA_REACH + this.mech.getBbWidth() / 2.0D + this.targetMob.getBbWidth() / 2.0D;
        boolean inReach = this.mech.distanceTo(this.targetMob) <= reach;
        // Re-path on a cooldown (not every tick) so the mech doesn't stutter chasing a moving mob.
        if (--this.pathCooldown <= 0) {
            this.pathCooldown = PATH_INTERVAL;
            if (inReach) {
                this.mech.getNavigation().stop();
            } else {
                this.mech.getNavigation().moveTo(this.targetMob, 1.2D);
            }
        }
        if (this.cooldown <= 0 && inReach) {
            if (this.mech.getEnergy().getEnergyStored() < this.mech.getMineCost()) {
                return; // out of power — no free swings (doHurtTarget would otherwise still land the hit)
            }
            this.cooldown = ATTACK_COOLDOWN;
            this.mech.swing(InteractionHand.MAIN_HAND);
            this.mech.triggerAttackAnim(); // arm chop + sweep "edge"
            this.mech.doHurtTarget(this.targetMob);
            applyCombatModules(this.targetMob); // Fire Aspect / Knockback every swing (even on i-frames)
            bankKillXp(level, this.targetMob); // Soul Tank absorbs the kill's XP directly
            sweepAttack(level, this.targetMob);
        }
    }

    /**
     * Apply the mech's combat-module effects directly to a struck mob. The enchanted weapon already drives
     * Sharpness damage + Looting drops through vanilla; this makes Fire Aspect and Knockback fire reliably
     * on <em>every</em> mob the mech hits — including the sweep-attack secondaries the weapon path skips.
     */
    private void applyCombatModules(LivingEntity victim) {
        int fire = this.mech.getUpgradeLevel(UpgradeType.FIRE_ASPECT);
        if (fire > 0 && !victim.fireImmune()) {
            victim.igniteForSeconds(fire * 4.0F); // 4s per level (only ever extends the burn)
        }
        int knockback = this.mech.getUpgradeLevel(UpgradeType.KNOCKBACK);
        if (knockback > 0) {
            victim.knockback(knockback * 0.5D,
                    this.mech.getX() - victim.getX(), this.mech.getZ() - victim.getZ());
        }
    }

    /**
     * On a killing blow, bank the slain mob's XP value straight into the Soul Tank (combat mechs with the
     * module only). Uses vanilla's {@code getExperienceReward}, so it matches what a player kill would give,
     * but no orbs ever spawn — the tank absorbs it directly. Skips babies and respects the mob-loot gamerule.
     */
    private void bankKillXp(ServerLevel level, LivingEntity victim) {
        if (this.mech.getSoulCapacity() <= 0 || !victim.isDeadOrDying() || !victim.shouldDropExperience()) {
            return;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            return;
        }
        int xp = (int) Math.round(victim.getExperienceReward(level, this.mech) * Config.SOUL_TANK_XP_SCALE.get());
        this.mech.addSoulXp(xp);
    }

    /** Sword-style sweep: clip nearby mobs around the struck one for reduced damage + the combat effects. */
    private void sweepAttack(ServerLevel level, LivingEntity primary) {
        double range = 1.5D + this.mech.getBbWidth() * 0.5D;
        // Base sweep damage + a Sharpness contribution, so the AoE scales with the combat modules too.
        float damage = 1.0F + (float) (this.mech.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4D)
                + this.mech.getUpgradeLevel(UpgradeType.SHARPNESS) * 0.5F;
        AABB area = primary.getBoundingBox().inflate(range, 0.3D, range);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, this::isValidTarget)) {
            if (entity == primary || this.mech.distanceToSqr(entity) > (range + 2.0D) * (range + 2.0D)) {
                continue;
            }
            if (entity.hurt(level.damageSources().mobAttack(this.mech), damage)) {
                applyCombatModules(entity); // Fire Aspect + Knockback on each sweep victim
                bankKillXp(level, entity);  // sweep kills also feed the Soul Tank
            }
        }
    }

    /** Nearest valid mob whose horizontal distance from the guard post is within the guard radius. */
    private LivingEntity findTarget(ServerLevel level, BlockPos home) {
        int r = guardRadius();
        AABB box = new AABB(home).inflate(r, r + 4.0D, r);
        double cx = home.getX() + 0.5D;
        double cz = home.getZ() + 0.5D;
        LivingEntity best = null;
        double bestSqr = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, this::isValidTarget)) {
            double dx = entity.getX() - cx;
            double dz = entity.getZ() - cz;
            if (dx * dx + dz * dz > (double) r * r) {
                continue; // outside the guard zone — leave it alone (no rampage)
            }
            double distSqr = this.mech.distanceToSqr(entity);
            if (distSqr < bestSqr) {
                bestSqr = distSqr;
                best = entity;
            }
        }
        return best;
    }

    /** Never attack players or other mechs. Otherwise hostiles only, unless "attack all" is enabled. */
    private boolean isValidTarget(LivingEntity entity) {
        if (entity == this.mech || !entity.isAlive() || entity instanceof Player || entity instanceof MiningMech) {
            return false;
        }
        return this.mech.isAttackAll() || entity instanceof Enemy;
    }

    private void deposit(ServerLevel level, IItemHandler chest) {
        BlockPos deposit = this.mech.getDepositPos();
        Vec3 center = Vec3.atCenterOf(deposit);
        this.mech.getLookControl().setLookAt(center.x, center.y, center.z);
        if (this.mech.distanceToSqr(center) > REACH_SQR) {
            this.mech.getNavigation().moveTo(center.x, center.y, center.z, 1.0D);
            return;
        }
        this.mech.getNavigation().stop();
        this.idleTicks = 0;
        IItemHandler inventory = this.mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(chest, stack.copy(), false);
            int moved = stack.getCount() - remainder.getCount();
            if (moved > 0) {
                inventory.extractItem(slot, moved, false);
            }
        }
    }

    /** The linked deposit chest's handler; clears the link if its block is loaded but no longer a container. */
    private IItemHandler depositChest(ServerLevel level) {
        BlockPos deposit = this.mech.getDepositPos();
        if (deposit == null) {
            return null;
        }
        IItemHandler handler = Capabilities.ItemHandler.BLOCK.getCapability(level, deposit, null, null, (Direction) null);
        if (handler == null && level.isLoaded(deposit)) {
            this.mech.setDepositPos(null);
        }
        return handler;
    }

    private boolean isInventoryFull() {
        IItemHandler inventory = this.mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isInventoryEmpty() {
        IItemHandler inventory = this.mech.getInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
