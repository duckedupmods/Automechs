package com.duckedupmods.automechs.entity;

/**
 * The job a mech performs, decided by the circuit slotted into the Robot Builder. The role also picks
 * which of the three chassis models the mech renders as ({@link #modelTier}), so the roles reuse the
 * existing T1/T2/T3 art (mining/farming = light T1, building = heavy T2, combat = T3).
 */
public enum MechRole {
    MINING("mining", 1),
    FARMING("farming", 1),
    BUILDING("building", 2),
    COMBAT("combat", 3);

    private final String id;
    private final int modelTier;

    MechRole(String id, int modelTier) {
        this.id = id;
        this.modelTier = modelTier;
    }

    public String id() {
        return this.id;
    }

    /** Which chassis model/texture (1..3) this role renders as. */
    public int modelTier() {
        return this.modelTier;
    }

    public String translationKey() {
        return "role.automechs." + this.id;
    }

    public static MechRole byId(String id) {
        for (MechRole role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        return MINING;
    }

    public static MechRole byOrdinal(int ordinal) {
        MechRole[] values = values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : MINING;
    }

    /** Default role for a chassis item of the given visual tier (when no role component is present). */
    public static MechRole defaultForTier(int tier) {
        return switch (tier) {
            case 2 -> BUILDING;
            case 3 -> COMBAT;
            default -> MINING;
        };
    }
}
