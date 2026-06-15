package com.duckedupmods.automechs;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration. Bootstrap version exposes a single master switch; the real Automechs config
 * (FE cost per block, max mechs per player, mine speed, area-size cap, mineable allow/deny tags,
 * respect-block-protection) lands with the features and will move to a SERVER spec.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_AUTOMECHS = BUILDER
            .comment("Master switch for Automechs features. When false, mechs will not operate.")
            .define("enableAutomechs", true);

    public static final ModConfigSpec.IntValue FE_PER_BLOCK = BUILDER
            .comment("Forge Energy consumed by a mech for each block it mines.")
            .defineInRange("fePerBlock", 20, 0, 1_000_000);

    public static final ModConfigSpec.IntValue DIG_TICKS = BUILDER
            .comment("Base ticks a mech takes to mine one block (scaled up by block hardness).")
            .defineInRange("digTicks", 16, 1, 1_200);

    public static final ModConfigSpec.IntValue MAX_WORK_AREA_VOLUME = BUILDER
            .comment("Maximum number of blocks a mech work area may contain.")
            .defineInRange("maxWorkAreaVolume", 4_096, 1, 1_000_000);

    public static final ModConfigSpec.IntValue QUARRY_BASE_RADIUS = BUILDER
            .comment("Quarry mode: base footprint radius in blocks (a radius of 4 = a 9x9 quarry), before Range upgrades add +1 each.")
            .defineInRange("quarryBaseRadius", 4, 1, 64);

    public static final ModConfigSpec.IntValue QUARRY_MAX_DEPTH = BUILDER
            .comment("Quarry mode: how many blocks below the placement surface a quarry digs (clamped to world bottom).")
            .defineInRange("quarryMaxDepth", 64, 1, 512);

    public static final ModConfigSpec.IntValue BUILD_MAX_BLOCKS = BUILDER
            .comment("Builder mech: maximum number of blocks a loaded schematic may contain (larger files are rejected).")
            .defineInRange("buildMaxBlocks", 50_000, 1, 4_000_000);

    public static final ModConfigSpec.IntValue BUILD_MATERIAL_RANGE = BUILDER
            .comment("Builder mech: radius (blocks) around the mech it searches for chests to pull building materials from.")
            .defineInRange("buildMaterialRange", 8, 1, 64);

    public static final ModConfigSpec.IntValue BUILD_TICKS = BUILDER
            .comment("Builder mech: base ticks to place one block (each Speed module shaves one off, minimum 1).")
            .defineInRange("buildTicks", 4, 1, 200);

    public static final ModConfigSpec.BooleanValue BUILD_BREAK_BLOCKS = BUILDER
            .comment("Builder mech: clear blocks that obstruct the schematic (only within the build's own footprint, dropping them). Disable for a strict no-break builder.")
            .define("buildBreakBlocks", true);

    public static final ModConfigSpec.BooleanValue BUILD_EXCAVATE = BUILDER
            .comment("Builder mech: dig out terrain inside the structure's volume (the empty/interior cells) so the building isn't embedded in the ground. Never touches block entities (chests/machines).")
            .define("buildExcavate", true);

    public static final ModConfigSpec.DoubleValue SOUL_TANK_XP_SCALE = BUILDER
            .comment("Combat mech Soul Tank: multiplier on the XP banked per kill (1.0 = exactly what the mob would drop to a player; lower it to make stored XP less generous).")
            .defineInRange("soulTankXpScale", 1.0D, 0.0D, 10.0D);

    public static final ModConfigSpec.IntValue DYNAMO_FE_PER_TICK = BUILDER
            .comment("Forge Energy per tick produced by the Combustion Dynamo while it is burning fuel.")
            .defineInRange("dynamoFePerTick", 60, 1, 100_000);

    public static final ModConfigSpec.IntValue ASSEMBLY_FE_PER_TICK = BUILDER
            .comment("Forge Energy per tick the Robot Builder (Mech Assembly Bench) consumes while building a mech.")
            .defineInRange("assemblyFePerTick", 80, 0, 100_000);

    public static final ModConfigSpec.IntValue FABRICATOR_FE_PER_TICK = BUILDER
            .comment("Forge Energy per tick the Fabricator consumes while fabricating a mech part.")
            .defineInRange("fabricatorFePerTick", 40, 0, 100_000);

    public static final ModConfigSpec.IntValue UPGRADE_FE_PER_TICK = BUILDER
            .comment("Forge Energy per tick the Upgrade Station consumes while applying an upgrade.")
            .defineInRange("upgradeFePerTick", 40, 0, 100_000);

    public static final ModConfigSpec.IntValue PAD_BASE_RADIUS = BUILDER
            .comment("Charging Pad: base radius (blocks) it charges mechs within, before Range upgrades.")
            .defineInRange("padBaseRadius", 4, 1, 32);

    public static final ModConfigSpec.IntValue PAD_RADIUS_PER_RANGE = BUILDER
            .comment("Charging Pad: extra charge radius (blocks) added per Range upgrade module.")
            .defineInRange("padRadiusPerRange", 2, 0, 16);

    public static final ModConfigSpec.IntValue PAD_BASE_CAPACITY = BUILDER
            .comment("Charging Pad: base FE storage capacity, before Capacity upgrades.")
            .defineInRange("padBaseCapacity", 200_000, 1_000, 1_000_000_000);

    public static final ModConfigSpec.IntValue PAD_CAPACITY_PER_LEVEL = BUILDER
            .comment("Charging Pad: extra FE storage capacity added per Capacity upgrade module.")
            .defineInRange("padCapacityPerLevel", 200_000, 0, 1_000_000_000);

    static final ModConfigSpec SPEC = BUILDER.build();
}
