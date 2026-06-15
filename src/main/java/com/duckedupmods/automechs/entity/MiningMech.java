package com.duckedupmods.automechs.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.duckedupmods.automechs.Config;
import com.duckedupmods.automechs.entity.ai.MechCombatGoal;
import com.duckedupmods.automechs.entity.ai.MechFarmGoal;
import com.duckedupmods.automechs.entity.ai.MechFollowOwnerGoal;
import com.duckedupmods.automechs.entity.ai.MechWorkGoal;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.menu.MechMenu;
import com.duckedupmods.automechs.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Mining Mech — an owner-bound worker mech. This MVP version handles ownership, following its
 * owner, and the internal storage (item inventory + Forge Energy buffer) that later steps will use
 * for the dig → deposit → recharge loop. Both stores are exposed to other blocks/mods through
 * NeoForge capabilities (registered in the mod constructor).
 *
 * <p>All logic here is server-authoritative; only owner identity is synced to the client.
 */
public class MiningMech extends PathfinderMob implements GeoEntity {

    /** Number of internal inventory slots for mined items. */
    public static final int INVENTORY_SIZE = 18;
    /** Energy buffer capacity (FE) and max charge rate (FE/tick). */
    public static final int ENERGY_CAPACITY = 100_000;
    public static final int ENERGY_MAX_RECEIVE = 2_000;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_ENERGY =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_WORK_ENABLED =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.BOOLEAN);
    /** Whether the mech follows its owner. Off by default — a worker mech stays at its job until told. */
    private static final EntityDataAccessor<Boolean> DATA_FOLLOW =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.BOOLEAN);
    /** Synced role ordinal (drives the rendered model and the active behavior). */
    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.INT);
    /** Player-chosen working-range tier (0..Range level); -1 = auto (use the full installed Range). */
    private static final EntityDataAccessor<Integer> DATA_RANGE_TIER =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.INT);
    /** Combat: whether to attack all mobs (for mob farms), not just hostiles. Synced for the GUI button. */
    private static final EntityDataAccessor<Boolean> DATA_ATTACK_ALL =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.BOOLEAN);
    /** Work-area corners, synced so the client can draw the quarry/farm boundary in-world (empty = none). */
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_AREA_MIN =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_AREA_MAX =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    /** Whether the in-world work-area outline is drawn (player toggle, so it isn't on screen forever). */
    private static final EntityDataAccessor<Boolean> DATA_SHOW_AREA =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.BOOLEAN);
    /** Soul Tank charge (combat XP buffer), synced so the GUI gauge updates live as it fills/drains. */
    private static final EntityDataAccessor<Integer> DATA_SOUL_XP =
            SynchedEntityData.defineId(MiningMech.class, EntityDataSerializers.INT);

    /** Highest chassis tier (1 = light, 2 = heavy, 3 = combat) — used to pick the chassis item/model. */
    public static final int MAX_TIER = 3;
    /** FE the energy buffer grows by per level of the Capacity upgrade. */
    public static final int CAPACITY_PER_LEVEL = 50_000;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);
    private final MechEnergyStorage energy = new MechEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE);

    // Installed module levels (0..max), indexed by UpgradeType.ordinal(); applied via applyUpgrades().
    private final int[] upgradeLevels = new int[UpgradeType.values().length];

    // Work order (assigned by the Linker): a mining area (min..max) and an optional deposit chest.
    private BlockPos areaMin;
    private BlockPos areaMax;
    private BlockPos depositPos;

    // Combat: the guard post a combat mech holds (defaults to where it's deployed); it only engages and
    // returns within its guard radius of this anchor, so it doesn't wander off on a map-wide killing spree.
    private BlockPos guardAnchor;

    // Organisational group the player filed this mech under in the Mech Tablet (e.g. "Farming"). "" = none.
    private String group = "";

    // Combat: experience siphoned from kills into the Soul Tank, drained to the owner from its GUI. Stored
    // in synced data (DATA_SOUL_XP) so the GUI gauge reflects it live.

    // Builder: the loaded schematic (raw gzipped-NBT bytes + display name), the anchor it builds from, and
    // the rotation to apply. The parsed Blueprint is cached lazily from the bytes (server-side).
    private byte[] schematicData;
    private String schematicName = "";
    private BlockPos buildAnchor;
    private com.duckedupmods.automechs.schematic.Blueprint buildPlan; // transient cache, re-parsed on demand
    private int buildRotation; // ordinal into net.minecraft.world.level.block.Rotation

    // GeckoLib animation.
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation OFFLINE_ANIM = RawAnimation.begin().thenPlayAndHold("offline");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlay("attack");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Server-side latch so we only toggle the goal control flags on an online/offline transition. */
    private boolean aiSuspended;

    public MiningMech(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // Owner-bound constructs should not despawn.
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Combat role: find, chase, and swing at mobs (its own targeting + generous reach).
        this.goalSelector.addGoal(1, new MechCombatGoal(this));
        this.goalSelector.addGoal(2, new MechWorkGoal(this));
        this.goalSelector.addGoal(2, new MechFarmGoal(this));
        this.goalSelector.addGoal(2, new com.duckedupmods.automechs.entity.ai.MechBuildGoal(this));
        this.goalSelector.addGoal(3, new MechFollowOwnerGoal(this, 1.1D, 5.0F, 2.5F));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_ENERGY, 0);
        builder.define(DATA_WORK_ENABLED, true);
        builder.define(DATA_FOLLOW, false);
        builder.define(DATA_ROLE, MechRole.MINING.ordinal());
        builder.define(DATA_RANGE_TIER, -1);
        builder.define(DATA_ATTACK_ALL, false);
        builder.define(DATA_AREA_MIN, Optional.empty());
        builder.define(DATA_AREA_MAX, Optional.empty());
        builder.define(DATA_SHOW_AREA, true);
        builder.define(DATA_SOUL_XP, 0);
    }

    /** Whether to draw this mech's in-world work-area outline (mining quarry / farming field). */
    public boolean isAreaVisible() {
        return this.entityData.get(DATA_SHOW_AREA);
    }

    public void setAreaVisible(boolean visible) {
        this.entityData.set(DATA_SHOW_AREA, visible);
    }

    /** Combat: whether this mech attacks all mobs (mob farms), not just hostiles. */
    public boolean isAttackAll() {
        return this.entityData.get(DATA_ATTACK_ALL);
    }

    public void setAttackAll(boolean attackAll) {
        this.entityData.set(DATA_ATTACK_ALL, attackAll);
    }

    /** The mech's role; drives both the rendered model and which behavior is active. */
    public MechRole getRole() {
        return MechRole.byOrdinal(this.entityData.get(DATA_ROLE));
    }

    public void setRole(MechRole role) {
        this.entityData.set(DATA_ROLE, role.ordinal());
    }

    /** Visual chassis tier (1..{@link #MAX_TIER}) derived from the role. */
    public int getModelTier() {
        return getRole().modelTier();
    }

    // --- Upgrades ------------------------------------------------------------

    public int getUpgradeLevel(UpgradeType type) {
        return this.upgradeLevels[type.ordinal()];
    }

    public void setUpgradeLevel(UpgradeType type, int level) {
        this.upgradeLevels[type.ordinal()] = Math.max(0, Math.min(type.maxLevel(), level));
    }

    /** Convenience: whether an ability module is installed (level &gt; 0). */
    public boolean hasUpgrade(UpgradeType type) {
        return getUpgradeLevel(type) > 0;
    }

    /** The raw stored range-tier selection (-1 = auto/max), as set from the mech GUI dropdown. */
    public int getRangeTierRaw() {
        return this.entityData.get(DATA_RANGE_TIER);
    }

    /**
     * The largest selectable size tier. Farming fields run 2×2 (tier 0) … 8×8 (tier 6), with the base
     * 2×2/3×3 always available and each Range level unlocking one larger size. Mining quarries scale
     * straight off the installed Range level.
     */
    public int maxRangeTier() {
        int range = getUpgradeLevel(UpgradeType.RANGE);
        return switch (getRole()) {
            case FARMING -> Math.min(6, range + 1);
            // Combat: a few guard sizes always available, more unlocked by Range, so the player can rein
            // the mech in even before installing Range modules.
            case COMBAT -> Math.min(5, range + 2);
            default -> range;
        };
    }

    /**
     * The effective working-size tier: the player's GUI choice, or a sensible default when set to auto
     * (-1). Drives the Farming field size and the Quarry footprint, clamped to {@link #maxRangeTier()}.
     */
    public int getEffectiveRangeTier() {
        int max = maxRangeTier();
        int raw = getRangeTierRaw();
        if (raw < 0) {
            // Defaults: a modest 3×3 field for farming, a tight guard for combat (anti-rampage), the full
            // footprint for mining.
            return switch (getRole()) {
                case FARMING -> Math.min(1, max);
                case COMBAT -> 0;
                default -> max;
            };
        }
        return Math.max(0, Math.min(max, raw));
    }

    /** Set the working-size tier from the GUI; clamped to what's allowed for this mech. */
    public void setRangeTier(int tier) {
        this.entityData.set(DATA_RANGE_TIER, Math.max(0, Math.min(maxRangeTier(), tier)));
    }

    /** Re-derive attribute/energy effects from the current upgrade levels. Server-side. */
    public void applyUpgrades() {
        this.energy.setCapacity(ENERGY_CAPACITY + getUpgradeLevel(UpgradeType.CAPACITY) * CAPACITY_PER_LEVEL);

        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.3D + getUpgradeLevel(UpgradeType.SPEED) * 0.03D);
        }
        AttributeInstance reach = getAttribute(Attributes.FOLLOW_RANGE);
        if (reach != null) {
            reach.setBaseValue(24.0D + getUpgradeLevel(UpgradeType.RANGE) * 8.0D);
        }
        // Armor module: extra health + armour for the combat chassis.
        AttributeInstance health = getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(20.0D + getUpgradeLevel(UpgradeType.ARMOR) * 4.0D);
        }
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(getUpgradeLevel(UpgradeType.ARMOR) * 2.0D);
        }
        // Combat: an invisible enchanted weapon drives Sharpness/Looting/Fire Aspect/Knockback through
        // vanilla mob combat (no custom hit code needed).
        equipCombatGear();
        if (this.entityData.get(DATA_ENERGY) > this.energy.getMaxEnergyStored()) {
            this.entityData.set(DATA_ENERGY, this.energy.getEnergyStored());
        }
    }

    /**
     * Give a combat mech an enchanted main-hand weapon matching its installed combat modules, so vanilla
     * applies Sharpness (damage), Looting (drops), Fire Aspect, and Knockback automatically. The weapon is
     * never rendered (the GeckoLib model has no hand item) and never drops. Non-combat mechs are unarmed.
     */
    private void equipCombatGear() {
        if (this.level().isClientSide) {
            return;
        }
        if (getRole() != MechRole.COMBAT) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return;
        }
        ItemStack weapon = new ItemStack(Items.IRON_SWORD);
        HolderLookup.RegistryLookup<Enchantment> enchants =
                this.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        enchant(weapon, enchants, Enchantments.SHARPNESS, getUpgradeLevel(UpgradeType.SHARPNESS));
        enchant(weapon, enchants, Enchantments.LOOTING, getUpgradeLevel(UpgradeType.LOOTING));
        enchant(weapon, enchants, Enchantments.FIRE_ASPECT, getUpgradeLevel(UpgradeType.FIRE_ASPECT));
        enchant(weapon, enchants, Enchantments.KNOCKBACK, getUpgradeLevel(UpgradeType.KNOCKBACK));
        setItemSlot(EquipmentSlot.MAINHAND, weapon);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    private static void enchant(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> registry,
                                ResourceKey<Enchantment> key, int level) {
        if (level > 0) {
            stack.enchant(registry.getOrThrow(key), level);
        }
    }

    /** FE cost of one action, reduced 15% per Power Efficiency level (min 1). */
    public int getMineCost() {
        int base = Config.FE_PER_BLOCK.get();
        double factor = Math.max(0.1D, 1.0D - getUpgradeLevel(UpgradeType.EFFICIENCY) * 0.15D);
        return Math.max(1, (int) Math.round(base * factor));
    }

    /** Base dig time in ticks, shortened by Speed levels (min 2). */
    public int getDigTicks() {
        int base = Config.DIG_TICKS.get();
        return Math.max(2, base - getUpgradeLevel(UpgradeType.SPEED) * 2);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            int stored = this.energy.getEnergyStored();
            if (this.entityData.get(DATA_ENERGY) != stored) {
                this.entityData.set(DATA_ENERGY, stored);
            }
            // Vacuum nearby dropped items: any mech with a Magnet Coil, and combat mechs always (to gather
            // the loot from their kills). Every few ticks.
            if ((hasUpgrade(UpgradeType.MAGNET) || getRole() == MechRole.COMBAT) && this.tickCount % 4 == 0) {
                vacuumItems();
            }
        }
    }

    /** Total experience currently held in the Soul Tank. */
    public int getSoulXp() {
        return this.entityData.get(DATA_SOUL_XP);
    }

    private void setSoulXp(int xp) {
        this.entityData.set(DATA_SOUL_XP, Math.max(0, xp));
    }

    /**
     * Bank XP into the Soul Tank (clamped to capacity). The combat goal calls this with a slain mob's XP
     * value on the killing blow, so the tank absorbs the souls of the mech's <em>own</em> kills directly —
     * no XP orbs spawn, so it never steals the owner's experience when they fight nearby.
     */
    public void addSoulXp(int amount) {
        if (amount <= 0) {
            return;
        }
        setSoulXp(Math.min(getSoulCapacity(), getSoulXp() + amount));
    }

    /** Maximum XP the Soul Tank can hold (2000 per upgrade level; 0 without the module). */
    public int getSoulCapacity() {
        return getUpgradeLevel(UpgradeType.SOUL_TANK) * 2000;
    }

    /** Pull loose item entities within range into the internal inventory (Magnet, or combat loot pickup). */
    private void vacuumItems() {
        double radius = 2.5D + getUpgradeLevel(UpgradeType.MAGNET) * 2.0D;
        if (getRole() == MechRole.COMBAT) {
            radius = Math.max(radius, 5.0D); // combat mechs sweep up their kills' drops
        }
        List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(radius));
        for (ItemEntity item : items) {
            if (!item.isAlive() || item.hasPickUpDelay()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(this.inventory, item.getItem(), false);
            if (remainder.isEmpty()) {
                item.discard();
            } else {
                item.setItem(remainder);
            }
        }
    }

    /** Hazard Seal makes the mech immune to fire and lava damage while it works near hazards. */
    @Override
    public boolean fireImmune() {
        return hasUpgrade(UpgradeType.HAZARD_SEAL) || super.fireImmune();
    }

    /** Each swing draws Forge Energy, so a combat mech stops fighting (goes offline) when its buffer empties. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide && !consumeEnergy(getMineCost())) {
            // Not enough for a full swing — drain the remaining dregs so it powers down next tick.
            this.energy.consume(this.energy.getEnergyStored());
        }
        return hit;
    }

    @Override
    public void aiStep() {
        // When offline, hard-lock all rotation: snapshot before the AI/look-control run and restore after,
        // so a powered-down mech stays perfectly still instead of turning to track the player. (The look
        // control ticks inside super.aiStep(), after customServerAiStep, so flag-disabling alone can leave
        // a residual turn — this guarantees it.)
        boolean lockRotation = !this.level().isClientSide && isOffline();
        float headLock = this.yHeadRot;
        float bodyLock = this.yBodyRot;
        float yLock = this.getYRot();
        float xLock = this.getXRot();

        super.aiStep();

        if (lockRotation) {
            this.setYHeadRot(headLock);
            this.yHeadRotO = headLock;
            this.yBodyRot = bodyLock;
            this.yBodyRotO = bodyLock;
            this.setYRot(yLock);
            this.yRotO = yLock;
            this.setXRot(xLock);
            this.xRotO = xLock;
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // No power → suspend the AI entirely: stop moving, looking, and targeting, so the mech just
        // slumps in place instead of following the owner or wandering. FloatGoal (JUMP flag) stays on so
        // a powered-down mech still bobs to the surface rather than sinking and getting lost.
        boolean offline = isOffline();
        if (offline != this.aiSuspended) {
            this.aiSuspended = offline;
            if (offline) {
                this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
                this.goalSelector.disableControlFlag(Goal.Flag.LOOK);
                this.targetSelector.disableControlFlag(Goal.Flag.TARGET);
            } else {
                this.goalSelector.enableControlFlag(Goal.Flag.MOVE);
                this.goalSelector.enableControlFlag(Goal.Flag.LOOK);
                this.targetSelector.enableControlFlag(Goal.Flag.TARGET);
            }
        }
        if (offline) {
            this.getNavigation().stop();
            this.setTarget(null);
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }
    }

    // --- Ownership -----------------------------------------------------------

    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwnerUUID(UUID uuid) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(uuid));
    }

    public void setOwner(Player player) {
        setOwnerUUID(player.getUUID());
    }

    /** The owning player, if currently loaded (works on both sides for player owners). */
    public LivingEntity getOwner() {
        UUID uuid = getOwnerUUID();
        return uuid == null ? null : this.level().getPlayerByUUID(uuid);
    }

    // --- Capabilities backing stores ----------------------------------------

    public IItemHandler getInventory() {
        return this.inventory;
    }

    public IEnergyStorage getEnergy() {
        return this.energy;
    }

    /** Spend energy for the mech's own work. Returns false (spending nothing) if insufficient. */
    public boolean consumeEnergy(int fe) {
        return this.energy.consume(fe);
    }

    /** Energy mirrored to the client for the GUI gauge. */
    public int getDisplayEnergy() {
        return this.entityData.get(DATA_ENERGY);
    }

    /**
     * Client-only: force the displayed energy on a non-synced preview entity (the one rendered inside the
     * Robot Builder / Upgrade Station) so it reads as powered and never shows the offline slump.
     */
    public void setDisplayEnergy(int fe) {
        this.entityData.set(DATA_ENERGY, fe);
    }

    public boolean isWorkEnabled() {
        return this.entityData.get(DATA_WORK_ENABLED);
    }

    public void setWorkEnabled(boolean enabled) {
        this.entityData.set(DATA_WORK_ENABLED, enabled);
    }

    /** Whether the mech is set to follow its owner (off by default; toggled from the mech GUI). */
    public boolean isFollowEnabled() {
        return this.entityData.get(DATA_FOLLOW);
    }

    public void setFollowEnabled(boolean enabled) {
        this.entityData.set(DATA_FOLLOW, enabled);
    }

    public boolean isOwnedBy(Player player) {
        return player.getUUID().equals(getOwnerUUID());
    }

    // --- Tablet organisation (group + cargo readout) -------------------------

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String group) {
        this.group = group == null ? "" : group;
    }

    /** Number of inventory slots currently holding items (for the tablet's cargo readout). */
    public int getUsedSlots() {
        int used = 0;
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            if (!this.inventory.getStackInSlot(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    // --- Work order ----------------------------------------------------------

    public boolean hasWorkArea() {
        return this.areaMin != null && this.areaMax != null;
    }

    public void setWorkOrder(BlockPos cornerA, BlockPos cornerB, BlockPos deposit) {
        this.areaMin = new BlockPos(
                Math.min(cornerA.getX(), cornerB.getX()),
                Math.min(cornerA.getY(), cornerB.getY()),
                Math.min(cornerA.getZ(), cornerB.getZ()));
        this.areaMax = new BlockPos(
                Math.max(cornerA.getX(), cornerB.getX()),
                Math.max(cornerA.getY(), cornerB.getY()),
                Math.max(cornerA.getZ(), cornerB.getZ()));
        this.depositPos = deposit;
        syncArea();
    }

    /** Push the work-area corners onto the synced data so clients can draw the boundary. Server-side. */
    private void syncArea() {
        this.entityData.set(DATA_AREA_MIN, Optional.ofNullable(this.areaMin));
        this.entityData.set(DATA_AREA_MAX, Optional.ofNullable(this.areaMax));
    }

    public BlockPos getAreaMin() {
        return this.areaMin;
    }

    public BlockPos getAreaMax() {
        return this.areaMax;
    }

    /** Synced work-area corners for client-side boundary rendering (null when no area is assigned). */
    public BlockPos getSyncedAreaMin() {
        return this.entityData.get(DATA_AREA_MIN).orElse(null);
    }

    public BlockPos getSyncedAreaMax() {
        return this.entityData.get(DATA_AREA_MAX).orElse(null);
    }

    public BlockPos getDepositPos() {
        return this.depositPos;
    }

    /** Bind (or clear) the deposit chest without touching the work area — used for quarry mechs. */
    public void setDepositPos(BlockPos pos) {
        this.depositPos = pos;
    }

    /** The combat mech's guard post (null until first set, then captured at its deploy spot). */
    public BlockPos getGuardAnchor() {
        return this.guardAnchor;
    }

    public void setGuardAnchor(BlockPos pos) {
        this.guardAnchor = pos;
    }

    // --- Builder schematic ---------------------------------------------------

    /** Store a freshly-loaded schematic (raw bytes + already-parsed plan), clearing any prior placement. */
    public void setSchematic(byte[] data, String name, com.duckedupmods.automechs.schematic.Blueprint plan) {
        this.schematicData = data;
        this.schematicName = name == null ? "" : name;
        this.buildPlan = plan;
        this.buildAnchor = null; // must be re-placed in the world
        setWorkEnabled(false);
    }

    public boolean hasSchematic() {
        return this.schematicData != null && getBlueprint() != null;
    }

    public String getSchematicName() {
        return this.schematicName;
    }

    /** The parsed base (un-rotated) blueprint, lazily re-parsed from the stored bytes. Null if none/invalid. */
    public com.duckedupmods.automechs.schematic.Blueprint getBlueprint() {
        if (this.buildPlan == null && this.schematicData != null) {
            try {
                this.buildPlan = com.duckedupmods.automechs.schematic.SchematicLoader.fromBytes(this.schematicData);
            } catch (Exception e) {
                this.schematicData = null; // corrupt — forget it
            }
        }
        return this.buildPlan;
    }

    public BlockPos getBuildAnchor() {
        return this.buildAnchor;
    }

    public void setBuildAnchor(BlockPos pos) {
        this.buildAnchor = pos;
    }

    public net.minecraft.world.level.block.Rotation getBuildRotation() {
        net.minecraft.world.level.block.Rotation[] values = net.minecraft.world.level.block.Rotation.values();
        return values[Math.floorMod(this.buildRotation, values.length)];
    }

    public void setBuildRotation(int ordinal) {
        this.buildRotation = ordinal;
    }

    // --- GeckoLib animation --------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animController));
        // A separate controller for the combat swing: idle (no animation) until triggered, so it overlays
        // the arm-chop onto whatever the main controller is doing.
        controllers.add(new AnimationController<>(this, "attack", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK_ANIM));
    }

    /** Play the swing animation and throw a sweep-attack "edge" in front of the mech. Server-side. */
    public void triggerAttackAnim() {
        if (this.level() instanceof ServerLevel server) {
            triggerAnim("attack", "attack");
            Vec3 look = getLookAngle();
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    getX() + look.x * 0.9D, getY() + getBbHeight() * 0.6D, getZ() + look.z * 0.9D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private PlayState animController(AnimationState<MiningMech> state) {
        if (isOffline()) {
            // No power: slump into a powered-down pose until recharged.
            return state.setAndContinue(OFFLINE_ANIM);
        }
        return state.setAndContinue(state.isMoving() ? WALK_ANIM : IDLE_ANIM);
    }

    /** True when the mech has no Forge Energy — drives the offline slump pose and suspends its AI. */
    public boolean isOffline() {
        if (!this.level().isClientSide) {
            return this.energy.getEnergyStored() <= 0;
        }
        return getDisplayEnergy() <= 0;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // --- Interaction & retrieval --------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.getItemInHand(hand).isEmpty()) {
            // Held items (e.g. the Linker) get first say via Item#interactLivingEntity.
            return super.mobInteract(player, hand);
        }
        if (this.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            packUp(player);
        } else if (player instanceof ServerPlayer serverPlayer) {
            // Always open the GUI — the Soul Tank is now drained from a button in there, not on right-click,
            // so a combat mech holding XP can still be opened to reach its range/area controls.
            openMenu(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /** Empty the Soul Tank into the owner as experience (invoked from the combat mech's GUI button). */
    public void drainSoulTank(Player player) {
        int held = getSoulXp();
        if (held <= 0) {
            return;
        }
        player.giveExperiencePoints(held);
        player.displayClientMessage(
                Component.literal("Drained " + held + " XP from the Soul Tank"), true);
        setSoulXp(0);
    }

    private void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, p) -> new MechMenu(containerId, inventory, this),
                getDisplayName()), buf -> {
            buf.writeInt(getId());
            buf.writeInt(maxRangeTier());          // max selectable size tier
            buf.writeInt(getEffectiveRangeTier()); // current selection
            buf.writeInt(this.energy.getMaxEnergyStored());   // capacity (for an accurate gauge)
            buf.writeInt(getSoulXp());             // Soul Tank charge (combat readout)
            buf.writeInt(getSoulCapacity());       // Soul Tank capacity (0 = no module → gauge hidden)
            // Builder readout: loaded schematic name + dimensions + block count (0 / "" when none).
            com.duckedupmods.automechs.schematic.Blueprint plan = getBlueprint();
            buf.writeUtf(this.schematicName == null ? "" : this.schematicName);
            buf.writeInt(plan != null ? plan.size().getX() : 0);
            buf.writeInt(plan != null ? plan.size().getY() : 0);
            buf.writeInt(plan != null ? plan.size().getZ() : 0);
            buf.writeInt(plan != null ? plan.blockCount() : 0);
            // Builder bill of materials: item id + count, most-used first (for the GUI materials panel).
            if (plan != null) {
                java.util.List<java.util.Map.Entry<net.minecraft.world.item.Item, Integer>> mats =
                        new java.util.ArrayList<>(plan.materials().entrySet());
                mats.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                int n = Math.min(mats.size(), 200);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(mats.get(i).getKey()));
                    buf.writeVarInt(mats.get(i).getValue());
                }
            } else {
                buf.writeVarInt(0);
            }
        });
    }

    /** Pack the mech back into a Mech Chassis item (matching its tier), returning its inventory. */
    private void packUp(Player player) {
        ItemStack chassis = createChassisStack();
        if (!player.addItem(chassis)) {
            spawnAtLocation(chassis);
        }
        dropInventoryContents();
        discard();
    }

    /** Build the chassis item for this mech, stamping its role and upgrade levels into components. */
    public ItemStack createChassisStack() {
        ItemStack chassis = new ItemStack(ModItems.chassisForTier(getModelTier()).get());
        chassis.set(com.duckedupmods.automechs.registry.ModDataComponents.MECH_ROLE.get(), getRole().id());
        for (UpgradeType type : UpgradeType.values()) {
            int level = getUpgradeLevel(type);
            if (level > 0) {
                chassis.set(type.component().get(), level);
            }
        }
        return chassis;
    }

    private void dropInventoryContents() {
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            ItemStack stack = this.inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        spawnAtLocation(createChassisStack());
        dropInventoryContents();
    }

    // --- Persistence ---------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Inventory", this.inventory.serializeNBT(this.registryAccess()));
        tag.putInt("Energy", this.energy.getEnergyStored());
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        putPos(tag, "AreaMin", this.areaMin);
        putPos(tag, "AreaMax", this.areaMax);
        putPos(tag, "Deposit", this.depositPos);
        putPos(tag, "Guard", this.guardAnchor);
        putPos(tag, "BuildAnchor", this.buildAnchor);
        if (this.schematicData != null) {
            tag.putByteArray("Schematic", this.schematicData);
            tag.putString("SchematicName", this.schematicName);
            tag.putInt("BuildRot", this.buildRotation);
        }
        tag.putString("Group", this.group);
        tag.putInt("SoulXp", getSoulXp());
        tag.putBoolean("AttackAll", isAttackAll());
        tag.putBoolean("ShowArea", isAreaVisible());
        tag.putBoolean("WorkEnabled", isWorkEnabled());
        tag.putBoolean("Follow", isFollowEnabled());
        tag.putString("Role", getRole().id());
        tag.putInt("RangeTier", getRangeTierRaw());
        for (UpgradeType type : UpgradeType.values()) {
            int level = getUpgradeLevel(type);
            if (level > 0) {
                tag.putInt("Upgrade_" + type.id(), level);
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Inventory")) {
            this.inventory.deserializeNBT(this.registryAccess(), tag.getCompound("Inventory"));
        }
        this.energy.setEnergy(tag.getInt("Energy"));
        if (tag.hasUUID("Owner")) {
            setOwnerUUID(tag.getUUID("Owner"));
        }
        this.areaMin = getPos(tag, "AreaMin");
        this.areaMax = getPos(tag, "AreaMax");
        syncArea();
        this.depositPos = getPos(tag, "Deposit");
        this.guardAnchor = getPos(tag, "Guard");
        this.buildAnchor = getPos(tag, "BuildAnchor");
        if (tag.contains("Schematic")) {
            this.schematicData = tag.getByteArray("Schematic");
            this.schematicName = tag.getString("SchematicName");
            this.buildRotation = tag.getInt("BuildRot");
            this.buildPlan = null; // re-parse lazily
        }
        this.group = tag.getString("Group");
        setSoulXp(tag.getInt("SoulXp"));
        setAttackAll(tag.getBoolean("AttackAll"));
        setAreaVisible(!tag.contains("ShowArea") || tag.getBoolean("ShowArea")); // default visible
        if (tag.contains("WorkEnabled")) {
            setWorkEnabled(tag.getBoolean("WorkEnabled"));
        }
        setFollowEnabled(tag.getBoolean("Follow"));
        this.entityData.set(DATA_RANGE_TIER, tag.contains("RangeTier") ? tag.getInt("RangeTier") : -1);
        if (tag.contains("Role")) {
            setRole(MechRole.byId(tag.getString("Role")));
        } else if (tag.contains("Tier")) {
            // Legacy save: map the old tier to a default role.
            setRole(MechRole.defaultForTier(tag.getInt("Tier")));
        }
        for (UpgradeType type : UpgradeType.values()) {
            setUpgradeLevel(type, tag.getInt("Upgrade_" + type.id()));
        }
        // Legacy saves stored the original four under different keys.
        if (tag.contains("UpgradeSpeed")) {
            setUpgradeLevel(UpgradeType.SPEED, tag.getInt("UpgradeSpeed"));
            setUpgradeLevel(UpgradeType.CAPACITY, tag.getInt("UpgradeCapacity"));
            setUpgradeLevel(UpgradeType.RANGE, tag.getInt("UpgradeRange"));
            setUpgradeLevel(UpgradeType.EFFICIENCY, tag.getInt("UpgradeEfficiency"));
        }
        applyUpgrades();
    }

    private static void putPos(CompoundTag tag, String key, BlockPos pos) {
        if (pos != null) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            tag.put(key, posTag);
        }
    }

    private static BlockPos getPos(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        CompoundTag posTag = tag.getCompound(key);
        return new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
    }
}
