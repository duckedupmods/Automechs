package com.duckedupmods.automechs.item;

import java.util.function.Supplier;

import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.registry.ModDataComponents;

import net.minecraft.core.component.DataComponentType;

/**
 * A module installed on a mech (or, for {@link #CAPACITY}/{@link #RANGE}, the Charging Pad) at the
 * Upgrade Station. Each module is capped at {@link #maxLevel} and stored as an integer level in a data
 * component on the Mech Chassis item, so packing a mech up and redeploying it never loses the module.
 *
 * <p>{@link Category} drives where a module is useful: CORE and UTILITY help every role, while the
 * MINING / FARMING / COMBAT modules only do anything for that role — the Upgrade Station rejects a
 * module a chassis can't use (see {@link #appliesTo(MechRole)}).
 */
public enum UpgradeType {
    // ---- core stats: useful to every role; CAPACITY + RANGE also work in the Charging Pad ----
    SPEED("speed", 5, Category.CORE),
    CAPACITY("capacity", 5, Category.CORE),     // FE battery size (display name: "Capacitor")
    RANGE("range", 5, Category.CORE),           // work area / reach (display name: "Range Extender")
    EFFICIENCY("efficiency", 5, Category.CORE), // FE cost per action (display name: "Power Efficiency")

    // ---- mining ----
    FORTUNE("fortune", 3, Category.MINING),
    SMELTER("smelter", 1, Category.MINING),
    SILK_TOUCH("silk_touch", 1, Category.MINING),
    HAZARD_SEAL("hazard_seal", 1, Category.MINING),

    // ---- farming ----
    FERTILIZER("fertilizer", 1, Category.FARMING),

    // ---- combat ----
    SHARPNESS("sharpness", 5, Category.COMBAT),
    LOOTING("looting", 3, Category.COMBAT),
    FIRE_ASPECT("fire_aspect", 2, Category.COMBAT),
    KNOCKBACK("knockback", 2, Category.COMBAT),
    ARMOR("armor", 5, Category.COMBAT),
    SOUL_TANK("soul_tank", 5, Category.COMBAT),  // raises the kill-XP buffer size

    // ---- utility: useful to every role ----
    MAGNET("magnet", 3, Category.UTILITY),
    SOLAR("solar", 3, Category.UTILITY),
    ENDER_LINK("ender_link", 1, Category.UTILITY);

    /** Which roles a module benefits. */
    public enum Category { CORE, MINING, FARMING, COMBAT, UTILITY }

    private final String id;
    private final int maxLevel;
    private final Category category;

    UpgradeType(String id, int maxLevel, Category category) {
        this.id = id;
        this.maxLevel = maxLevel;
        this.category = category;
    }

    public String id() {
        return this.id;
    }

    public int maxLevel() {
        return this.maxLevel;
    }

    public Category category() {
        return this.category;
    }

    public String translationKey() {
        return "upgrade.automechs." + this.id;
    }

    public String descriptionKey() {
        return "upgrade.automechs." + this.id + ".desc";
    }

    /** Whether installing this module on a mech of the given role does anything. */
    public boolean appliesTo(MechRole role) {
        return switch (this.category) {
            case CORE, UTILITY -> true;
            case MINING -> role == MechRole.MINING;
            case FARMING -> role == MechRole.FARMING;
            case COMBAT -> role == MechRole.COMBAT;
        };
    }

    /** Whether the Charging Pad accepts this module (it only uses its battery + radius stats). */
    public boolean usedByChargingPad() {
        return this == CAPACITY || this == RANGE;
    }

    /** The data component that stores this module's level on a chassis stack. */
    public Supplier<DataComponentType<Integer>> component() {
        return ModDataComponents.upgrade(this);
    }
}
