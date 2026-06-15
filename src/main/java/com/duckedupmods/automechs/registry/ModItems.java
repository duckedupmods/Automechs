package com.duckedupmods.automechs.registry;

import java.util.EnumMap;
import java.util.Map;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.HoloGuideItem;
import com.duckedupmods.automechs.item.MechChassisItem;
import com.duckedupmods.automechs.item.MechCircuitItem;
import com.duckedupmods.automechs.item.MechLinkerItem;
import com.duckedupmods.automechs.item.MechTabletItem;
import com.duckedupmods.automechs.item.UpgradeModuleItem;
import com.duckedupmods.automechs.item.UpgradeType;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All items registered by Automechs, including {@link BlockItem}s for our blocks.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Automechs.MODID);

    /** Tier 1 Mech Chassis — deploys a stock Mining Mech. */
    public static final DeferredItem<MechChassisItem> MECH_CHASSIS =
            ITEMS.registerItem("mech_chassis", p -> new MechChassisItem(p, 1), new Item.Properties().stacksTo(16));

    /** Tier 2 Mech Chassis — deploys a Heavy Foreman mech. */
    public static final DeferredItem<MechChassisItem> MECH_CHASSIS_T2 =
            ITEMS.registerItem("mech_chassis_t2", p -> new MechChassisItem(p, 2), new Item.Properties().stacksTo(16));

    /** Tier 3 Mech Chassis — deploys a Combat Core mech. */
    public static final DeferredItem<MechChassisItem> MECH_CHASSIS_T3 =
            ITEMS.registerItem("mech_chassis_t3", p -> new MechChassisItem(p, 3), new Item.Properties().stacksTo(16));

    /** The chassis item that represents a given tier (used when a mech is packed up or destroyed). */
    public static DeferredItem<MechChassisItem> chassisForTier(int tier) {
        return switch (tier) {
            case 2 -> MECH_CHASSIS_T2;
            case 3 -> MECH_CHASSIS_T3;
            default -> MECH_CHASSIS;
        };
    }

    /** The Mech Linker — programs a mech's mining area and deposit chest. */
    public static final DeferredItem<MechLinkerItem> MECH_LINKER =
            ITEMS.registerItem("mech_linker", MechLinkerItem::new, new Item.Properties().stacksTo(1));

    // ----- Mech parts: fabricated at the Fabricator, then assembled at the Robot Builder -----

    /** Chassis Plates — the armored body shell; goes in the Robot Builder's Plates socket. */
    public static final DeferredItem<Item> MECH_PLATES =
            ITEMS.registerSimpleItem("mech_plates");

    /** Power Core — the Forge-Energy core that drives the mech; goes in the Core socket. */
    public static final DeferredItem<Item> MECH_CORE =
            ITEMS.registerSimpleItem("mech_core");

    /** AI Chip — the mech's brain (generic across roles); goes in the AI Chip socket. */
    public static final DeferredItem<Item> AI_CHIP =
            ITEMS.registerSimpleItem("ai_chip");

    /** Role circuits — one per {@link MechRole}; the Circuit socket decides the mech's job. */
    private static final Map<MechRole, DeferredItem<MechCircuitItem>> CIRCUITS = new EnumMap<>(MechRole.class);

    static {
        for (MechRole role : MechRole.values()) {
            CIRCUITS.put(role, ITEMS.registerItem(role.id() + "_circuit",
                    p -> new MechCircuitItem(p, role), new Item.Properties()));
        }
    }

    public static DeferredItem<MechCircuitItem> circuit(MechRole role) {
        return CIRCUITS.get(role);
    }

    /** Upgrade modules — one per {@link UpgradeType}; applied at the Upgrade Station. */
    private static final Map<UpgradeType, DeferredItem<UpgradeModuleItem>> UPGRADES = new EnumMap<>(UpgradeType.class);

    static {
        for (UpgradeType type : UpgradeType.values()) {
            UPGRADES.put(type, ITEMS.registerItem("upgrade_" + type.id(),
                    p -> new UpgradeModuleItem(p, type), new Item.Properties()));
        }
    }

    public static DeferredItem<UpgradeModuleItem> upgrade(UpgradeType type) {
        return UPGRADES.get(type);
    }

    /** BlockItem for the Assembly Workshop. */
    public static final DeferredItem<BlockItem> ASSEMBLY_WORKSHOP =
            ITEMS.registerSimpleBlockItem("assembly_workshop", ModBlocks.ASSEMBLY_WORKSHOP);

    /** BlockItem for the Charging Pad. */
    public static final DeferredItem<BlockItem> CHARGING_PAD =
            ITEMS.registerSimpleBlockItem("charging_pad", ModBlocks.CHARGING_PAD);

    /** BlockItem for the Data Rack. */
    public static final DeferredItem<BlockItem> DATA_RACK =
            ITEMS.registerSimpleBlockItem("data_rack", ModBlocks.DATA_RACK);

    /** BlockItem for the Main Drive. */
    public static final DeferredItem<BlockItem> MAIN_DRIVE =
            ITEMS.registerSimpleBlockItem("main_drive", ModBlocks.MAIN_DRIVE);

    /** BlockItem for the Data Cable. */
    public static final DeferredItem<BlockItem> DATA_CABLE =
            ITEMS.registerSimpleBlockItem("data_cable", ModBlocks.DATA_CABLE);

    /** BlockItem for the Storage Terminal. */
    public static final DeferredItem<BlockItem> STORAGE_TERMINAL =
            ITEMS.registerSimpleBlockItem("storage_terminal", ModBlocks.STORAGE_TERMINAL);

    /** Spawn egg for the Cache Crawler defrag bot (testing / creative). */
    public static final DeferredItem<DeferredSpawnEggItem> CACHE_CRAWLER_SPAWN_EGG =
            ITEMS.registerItem("cache_crawler_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.CACHE_CRAWLER, 0x2C3240, 0x3ACEA8, props),
                    new Item.Properties());

    /** BlockItem for the Mech Assembly Bench. */
    public static final DeferredItem<BlockItem> MECH_ASSEMBLY_BENCH =
            ITEMS.registerSimpleBlockItem("mech_assembly_bench", ModBlocks.MECH_ASSEMBLY_BENCH);

    /** BlockItem for the Combustion Dynamo. */
    public static final DeferredItem<BlockItem> COMBUSTION_DYNAMO =
            ITEMS.registerSimpleBlockItem("combustion_dynamo", ModBlocks.COMBUSTION_DYNAMO);

    /** BlockItem for the Power Conduit. */
    public static final DeferredItem<BlockItem> POWER_CONDUIT =
            ITEMS.registerSimpleBlockItem("power_conduit", ModBlocks.POWER_CONDUIT);

    /** BlockItem for the Upgrade Station. */
    public static final DeferredItem<BlockItem> UPGRADE_STATION =
            ITEMS.registerSimpleBlockItem("upgrade_station", ModBlocks.UPGRADE_STATION);

    /** The Mech Tablet — handheld command center that opens the mech dashboard. */
    public static final DeferredItem<MechTabletItem> MECH_TABLET =
            ITEMS.registerItem("mech_tablet", MechTabletItem::new, new Item.Properties().stacksTo(1));

    /** The Holo-Guide — an in-game manual; right-click projects the paginated tutorial screen. */
    public static final DeferredItem<HoloGuideItem> HOLO_GUIDE =
            ITEMS.registerItem("holo_guide", HoloGuideItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
