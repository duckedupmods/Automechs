package com.duckedupmods.automechs.datagen;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.registry.ModBlocks;
import com.duckedupmods.automechs.registry.ModEntities;
import com.duckedupmods.automechs.registry.ModItems;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Generates {@code en_us.json}. Every user-facing string lives here so the lang file stays in sync
 * with registered content and there are never raw translation keys on screen (an ATM requirement).
 */
public class AutomechsLanguageProvider extends LanguageProvider {

    public AutomechsLanguageProvider(PackOutput output) {
        super(output, Automechs.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // Creative tab
        add("itemGroup.automechs", "Automechs");

        // Items
        add(ModItems.MECH_CHASSIS.get(), "Light Mech Chassis");
        add(ModItems.MECH_CHASSIS_T2.get(), "Heavy Mech Chassis");
        add(ModItems.MECH_CHASSIS_T3.get(), "Combat Mech Chassis");
        add(ModItems.MECH_LINKER.get(), "Mech Linker");
        add(ModItems.MECH_PLATES.get(), "Chassis Plates");
        add(ModItems.MECH_CORE.get(), "Power Core");
        add(ModItems.AI_CHIP.get(), "AI Chip");
        add(ModItems.circuit(MechRole.MINING).get(), "Mining Circuit");
        add(ModItems.circuit(MechRole.FARMING).get(), "Farming Circuit");
        add(ModItems.circuit(MechRole.BUILDING).get(), "Building Circuit");
        add(ModItems.circuit(MechRole.COMBAT).get(), "Combat Circuit");

        // Roles
        add("role.automechs.mining", "Mining");
        add("role.automechs.farming", "Farming");
        add("role.automechs.building", "Building");
        add("role.automechs.combat", "Combat");

        // ---- Upgrade modules: item name, short stat label, and description ----
        // Core stats (any role; Capacitor + Range Extender also work in the Charging Pad)
        upgradeLang(UpgradeType.SPEED, "Speed Servo", "Speed",
                "Acts and moves faster: quicker digging, harvesting and attacks, plus faster walking.");
        upgradeLang(UpgradeType.CAPACITY, "Capacitor", "Battery",
                "Bigger energy battery: the mech (or Charging Pad) stores more FE.");
        upgradeLang(UpgradeType.RANGE, "Range Extender", "Range",
                "Enlarges the work area and reach: bigger quarry and field; in a Charging Pad, a wider charge radius.");
        upgradeLang(UpgradeType.EFFICIENCY, "Power Efficiency", "Power Efficiency",
                "Lowers the FE cost of every action (per block mined, crop tended, hit landed).");
        // Mining
        upgradeLang(UpgradeType.FORTUNE, "Fortune Matrix", "Fortune",
                "Ore blocks drop extra raw ore and gems, like a Fortune pickaxe.");
        upgradeLang(UpgradeType.SMELTER, "Smelter Core", "Smelter",
                "Auto-smelts mined ores into ingots. Stacks with Fortune.");
        upgradeLang(UpgradeType.SILK_TOUCH, "Silk Touch Drill", "Silk Touch",
                "Mines blocks whole: ore blocks, glass, ice. Overrides Fortune.");
        upgradeLang(UpgradeType.HAZARD_SEAL, "Hazard Seal", "Hazard Seal",
                "Works safely around water and lava and lights its dig site, so a quarry won't flood, burn or spawn mobs.");
        // Farming
        upgradeLang(UpgradeType.FERTILIZER, "Fertilizer Dispenser", "Fertilizer",
                "Lets a Farming mech use bone meal from its inventory to speed up crops.");
        // Combat
        upgradeLang(UpgradeType.SHARPNESS, "Sharpness Edge", "Sharpness",
                "Deals extra attack damage.");
        upgradeLang(UpgradeType.LOOTING, "Looting Circuit", "Looting",
                "Mobs drop more loot, like a Looting sword.");
        upgradeLang(UpgradeType.FIRE_ASPECT, "Fire Aspect Igniter", "Fire Aspect",
                "Attacks set targets on fire, so drops come pre-cooked.");
        upgradeLang(UpgradeType.KNOCKBACK, "Knockback Piston", "Knockback",
                "Attacks shove enemies back.");
        upgradeLang(UpgradeType.ARMOR, "Armor Plating", "Armor",
                "Adds hull plating for more maximum health.");
        upgradeLang(UpgradeType.SOUL_TANK, "Soul Tank", "Soul Tank",
                "Greatly increases how much kill-XP a combat mech can hold. Right-click it empty-handed to drain the XP into you.");
        // Utility (any role)
        upgradeLang(UpgradeType.MAGNET, "Magnet Coil", "Magnet",
                "Vacuums up nearby dropped items into the mech's inventory.");
        upgradeLang(UpgradeType.SOLAR, "Solar Trickle", "Solar",
                "Slowly recharges the mech's battery in daylight.");
        upgradeLang(UpgradeType.ENDER_LINK, "Ender Link", "Ender Link",
                "Deposits into the linked chest from any distance: no walking back.");

        // Item tooltips
        add("tooltip.automechs.chassis_role", "Role: %s");
        add("tooltip.automechs.chassis_upgrade", "%s +%s");
        add("tooltip.automechs.chassis_deploy", "Use on a block to deploy a mech");
        add("tooltip.automechs.circuit_role", "Role: %s");
        add("tooltip.automechs.circuit_use", "Slot into the Robot Builder's Circuit socket");
        add("tooltip.automechs.upgrade_stat", "Upgrades: %s");
        add("tooltip.automechs.upgrade_use", "Apply at the Upgrade Station");
        add("tooltip.automechs.upgrade_max", "Up to level %s");
        add("tooltip.automechs.scope_any", "Fits any mech");
        add("tooltip.automechs.scope_mining", "Mining mechs");
        add("tooltip.automechs.scope_farming", "Farming mechs");
        add("tooltip.automechs.scope_combat", "Combat mechs");
        add("tooltip.automechs.scope_pad", "Also works in the Charging Pad");

        // Quarry placement
        add("message.automechs.quarry_aim", "Aim and left-click to start the quarry · right-click to cancel");
        add("message.automechs.quarry_set", "Quarry set — the mech is on it.");
        add("message.automechs.farm_aim", "Aim and left-click to set the farm field · right-click to cancel");
        add("message.automechs.farm_set", "Farm field set — the mech is on it.");

        // Builder schematic placement
        add("message.automechs.build_aim", "Placing %s · scroll: distance · R: rotate · X/Z: up/down · left-click: build · right-click: cancel");
        add("message.automechs.build_set", "Build placed — the mech is on it.");
        add("message.automechs.build_done", "%s — build complete.");
        add("message.automechs.schematic_loaded", "Loaded %s (%s blocks)");
        add("message.automechs.materials_header", "Materials for %s:");
        add("message.automechs.materials_line", "  %s × %s");
        add("message.automechs.materials_more", "  …and %s more block type(s)");
        add("message.automechs.schematic_too_big", "Schematic too big: %s blocks (max %s).");
        add("message.automechs.schematic_empty", "That schematic has no blocks to build.");
        add("message.automechs.schematic_bad", "Couldn't read that schematic file.");
        add("gui.automechs.schematic.title", "Select Schematic");
        add("gui.automechs.schematic.open_folder", "Open Folder");
        add("gui.automechs.schematic.refresh", "Refresh");
        add("gui.automechs.schematic.empty", "No schematics found. Click \"Open Folder\" and drop .litematic / .nbt files in.");
        add("gui.automechs.schematic.too_big", "That file is too large.");
        add("gui.automechs.schematic.read_error", "Couldn't read that file.");

        // Blocks
        add(ModBlocks.ASSEMBLY_WORKSHOP.get(), "Fabricator");
        add(ModBlocks.CHARGING_PAD.get(), "Charging Pad");
        add(ModBlocks.DATA_RACK.get(), "Data Rack");
        add(ModBlocks.MAIN_DRIVE.get(), "Main Drive");
        add(ModBlocks.DATA_CABLE.get(), "Data Cable");
        add(ModBlocks.STORAGE_TERMINAL.get(), "Storage Terminal");

        // Data-storage network
        add("message.automechs.data_rack.stats", "Data Rack · %s items · %s/%s sectors · %s%% fragmented");
        add("gui.automechs.terminal.title", "Storage Terminal");
        add("gui.automechs.terminal.search", "Search…");
        add("gui.automechs.terminal.offline", "NETWORK OFFLINE");
        add("gui.automechs.terminal.no_drive", "No Main Drive connected");
        add("gui.automechs.terminal.empty", "Network is empty — insert items below.");
        add("gui.automechs.terminal.status", "%s items · %s racks · %s FE");
        add("gui.automechs.terminal.count", "Stored: %s");
        add(ModBlocks.MECH_ASSEMBLY_BENCH.get(), "Robot Builder");
        add(ModBlocks.MECH_ASSEMBLY_STRUCTURE.get(), "Robot Builder Frame");
        add("message.automechs.builder_no_room", "Not enough room — the Robot Builder needs a clear 3×3×3 space.");
        add(ModBlocks.UPGRADE_STATION.get(), "Upgrade Station");
        add(ModBlocks.COMBUSTION_DYNAMO.get(), "Combustion Dynamo");
        add(ModBlocks.POWER_CONDUIT.get(), "Power Conduit");

        // Mech Tablet (handheld command center)
        // ---- Holo-Guide (in-game manual) ----
        add(ModItems.HOLO_GUIDE.get(), "Automechs Holo-Guide");
        add("tooltip.automechs.holo_guide", "Right-click to open the field manual");
        add("gui.automechs.guide.close", "Close");
        add("guide.automechs.contents.title", "Contents");
        guidePage("overview", "Welcome to Automechs",
                "Automechs are mobile worker robots you build, power with Forge Energy, and send out to "
                        + "mine, farm, build and guard. Fabricate and assemble them, slot a role circuit to "
                        + "pick the job, then upgrade and command your fleet. Click a Contents entry to jump, "
                        + "or use the arrows / scroll wheel to page.");
        guidePage("power", "Combustion Dynamo",
                "Everything runs on Forge Energy (FE). The Combustion Dynamo burns ordinary furnace fuel "
                        + "(coal, charcoal, planks, lava bucket) to generate FE, and pauses itself when its "
                        + "buffer is full. Any FE generator from any mod works too — there are no hard deps.");
        guidePage("conduit", "Power Conduit",
                "Cheap cable that carries FE between generators, Charging Pads and machines. One craft makes "
                        + "six. Lay a line from your Dynamo to a Charging Pad so your mechs always have a place "
                        + "to top up. Out of power a mech simply pauses — it never dies or griefs.");
        guidePage("charging_pad", "Charging Pad",
                "Feed it FE from any cable and it pushes charge into every mech standing nearby. Mechs route "
                        + "their recharge step here automatically. A Capacitor module makes its battery bigger; "
                        + "a Range Extender widens its charge radius.");
        guidePage("fabricator", "Fabricator",
                "Your starting machine. Feed it raw materials plus FE and it fabricates the mech parts and "
                        + "role circuits. Open it, drop ingredients in, and let power do the work. Build this "
                        + "first — everything else is made here or from its parts.");
        guidePage("part_plates", "Chassis Plates",
                "The mech's hull. Fabricate a set from iron and copper in the Fabricator (raw materials + FE) "
                        + "— you need one set for every mech. This is the body the Robot Builder wraps around "
                        + "the core and chip.");
        guidePage("part_core", "Power Core",
                "The mech's energy heart: it holds the FE battery that everything runs on. Fabricate it from "
                        + "redstone, copper and a diamond. One Power Core per mech.");
        guidePage("part_chip", "AI Chip",
                "The mech's brain — it's also what a Mech Tablet needs. Fabricate it from gold, lapis and "
                        + "redstone. With plates, a core and a chip in hand, head to the Robot Builder.");
        guidePage("builder_bench", "Robot Builder",
                "Place this where it has a clear 3×3×3 space — it forms a fabrication frame. Slot the three "
                        + "parts plus a role Circuit and power it: a mech assembles and deploys, bound to you. "
                        + "Sneak + right-click a mech to pack it back into a chassis item (upgrades kept).");
        guidePage("circuits", "Role Circuits",
                "A circuit sets a mech's job. Fabricate the one you want — Mining, Farming, Building or "
                        + "Combat — and slot it into the Robot Builder's circuit socket when assembling. "
                        + "Hover each to read its role. The following pages cover every role in turn.");
        guidePage("mining", "Mining Mech",
                "Open it and press Quarry, then aim to place a box: it digs the area top-down, stores drops, "
                        + "deposits to a linked chest, and recharges at a Charging Pad. The cyan outline shows "
                        + "the dig volume (toggle with the eye). Range upgrades enlarge it.");
        guidePage("farming", "Farming Mech",
                "Press Set Area and aim at your field. It harvests ripe crops, replants seeds it carries, and "
                        + "(with a Fertilizer module) bone-meals young crops, depositing produce to a chest. "
                        + "The green outline marks the field.");
        guidePage("builder", "Builder Mech",
                "Load a .litematic or .nbt schematic, place its ghost (scroll = distance, R = rotate, X/Z = "
                        + "up/down), and it builds block by block, pulling materials from any chest in the "
                        + "footprint. Materials lists what it needs. It clears terrain but spares chests.");
        guidePage("combat", "Combat Mech",
                "A combat mech guards a post: it fights mobs in its radius then returns. Set the radius and "
                        + "Foes/All in its screen. With a Soul Tank it banks XP from kills — click the green "
                        + "XP tank to drain it into yourself.");
        guidePage("linker", "Mech Linker",
                "The targeting wand. Right-click a chest to set a mech's deposit/withdraw chest, and use it "
                        + "to place a Mining quarry box or a Farming field. Sneak + right-click resets a link. "
                        + "Keep one in your hotbar — it is how you point mechs at the world.");
        guidePage("tablet", "Mech Tablet",
                "Your fleet command center: shift + right-click a robot to register it, then right-click the "
                        + "air for a dashboard of every mech — rename, group, pause, recall or inspect at a "
                        + "glance. Needs an AI Chip to craft.");
        guidePage("holo", "Holo-Guide",
                "This manual. Cheap to craft — a book, redstone and glowstone — so you can read it from the "
                        + "very start. Right-click to open it anywhere. Keep it handy while you learn the "
                        + "ropes; every machine, role and upgrade is indexed on the Contents page.");
        guidePage("storage", "Data Storage Network",
                "A second kind of automation: an AE2-style storage network running on Forge Energy. A Main "
                        + "Drive powers a web of Data Cables that link Data Racks (the storage) to a Storage "
                        + "Terminal (your access screen). Racks fragment as they fill, and little Cache Crawler "
                        + "bots scuttle over and defragment them. No power, no access — but your items always "
                        + "stay safe.");
        guidePage("main_drive", "Main Drive",
                "The network's controller and power supply. Feed it Forge Energy from any cable and it floods "
                        + "the connected Data Cables to find and run your Racks and Terminal. Its draw scales "
                        + "with how many racks and how much data it powers; if its buffer runs dry the network "
                        + "goes offline (stored items stay safe). Its face shows live status.");
        guidePage("data_cable", "Data Cable",
                "Cheap network wiring — one craft makes eight. Cables connect the Main Drive to its Data Racks "
                        + "and the Storage Terminal; everything joined by an unbroken cable run is one network. "
                        + "Lay them through your storage room to wire it all together.");
        guidePage("data_rack", "Data Rack",
                "The storage cabinet. Each rack holds many 'data sectors'; inserted items scatter across free "
                        + "sectors, so a rack fragments as it fills — its face shows Defrag% and item count. "
                        + "Add more racks for more capacity. Fragmented racks waste space until a Cache Crawler "
                        + "compacts them.");
        guidePage("storage_terminal", "Storage Terminal",
                "Your window into the whole network: an ME-style grid showing every item aggregated across all "
                        + "racks. Shift-click from your inventory to store; click an item to pull a stack out. "
                        + "The left toolbar sorts by amount, name or mod. Needs the Main Drive online to work.");
        guidePage("cache_crawler", "Cache Crawler",
                "A little spider-bot that keeps your storage tidy. It scuttles across nearby Data Racks and "
                        + "defragments them — merging scattered fragments of the same item into full stacks and "
                        + "compacting them to the front, freeing sectors. Craft a few from an AI Chip, place "
                        + "them near your racks, and they maintain the network on their own.");
        guidePage("upgrade_station", "Upgrade Station",
                "Pack a mech into its chassis and place it on the right, a module on the left, then power the "
                        + "station: the module is stamped onto the chassis. Redeploy to apply. The next pages "
                        + "list every module — all share one recipe: a Copper + Redstone frame around a catalyst.");
        add("guide.automechs.upgrades_core.title", "Core Modules");
        add("guide.automechs.upgrades_mining.title", "Mining Modules");
        add("guide.automechs.upgrades_combat.title", "Combat Modules");
        add("guide.automechs.upgrades_field.title", "Field & Utility Modules");

        add(ModItems.MECH_TABLET.get(), "Mech Tablet");
        add("tooltip.automechs.tablet_use", "Right-click to open the node dashboard");
        add("tooltip.automechs.tablet_add", "Shift + right-click a robot to add it");
        add("tooltip.automechs.tablet_count", "%s robot(s) registered");
        add("message.automechs.tablet_added", "Robot added to the tablet");
        add("message.automechs.tablet_already", "That robot is already on the tablet");
        add("gui.automechs.mech_database.empty", "No mechs found. Build one at the Robot Builder.");
        add("gui.automechs.mech_database.title", "Mech Dashboard");
        add("gui.automechs.mech_database.ungrouped", "Ungrouped");
        add("gui.automechs.mech_database.name", "Name");
        add("gui.automechs.mech_database.group", "Group");
        add("gui.automechs.mech_database.folder", "Folder");
        add("gui.automechs.mech_database.new_group", "+ New Group");
        add("gui.automechs.mech_database.select_hint", "Drag to box-select · Shift-drag a robot out of its group");
        add("gui.automechs.mech_database.selected", "%s robots selected");
        add("gui.automechs.mech_database.role", "Role");
        add("gui.automechs.mech_database.energy", "Energy");
        add("gui.automechs.mech_database.position", "Position");
        add("gui.automechs.mech_database.cargo", "Cargo");
        add("gui.automechs.mech_database.status", "Status");
        add("gui.automechs.mech_database.pause", "Pause");
        add("gui.automechs.mech_database.resume", "Resume");
        add("gui.automechs.mech_database.recall", "Recall");
        add("status.automechs.working", "Working");
        add("status.automechs.idle", "Idle");
        add("status.automechs.paused", "Paused");
        add("status.automechs.no_power", "Out of power");

        // Entities
        add(ModEntities.MINING_MECH.get(), "Worker Mech");
        add(ModEntities.CACHE_CRAWLER.get(), "Cache Crawler");
        add(ModItems.CACHE_CRAWLER_SPAWN_EGG.get(), "Cache Crawler Spawn Egg");

        // Config screen
        add("automechs.configuration.title", "Automechs Config");
        add("automechs.configuration.section.automechs.common.toml", "Automechs Config");
        add("automechs.configuration.section.automechs.common.toml.title", "Automechs Config");
        add("automechs.configuration.enableAutomechs", "Enable Automechs");
        add("automechs.configuration.fePerBlock", "FE per Block Mined");
        add("automechs.configuration.digTicks", "Dig Time (ticks)");
        add("automechs.configuration.maxWorkAreaVolume", "Max Work Area (blocks)");
        add("automechs.configuration.dynamoFePerTick", "Dynamo FE / tick");
        add("automechs.configuration.assemblyFePerTick", "Robot Builder FE / tick");
        add("automechs.configuration.fabricatorFePerTick", "Fabricator FE / tick");
        add("automechs.configuration.upgradeFePerTick", "Upgrade Station FE / tick");
    }

    /** Adds the item name, short stat label and description lines for one upgrade module. */
    private void upgradeLang(UpgradeType type, String itemName, String statName, String description) {
        add(ModItems.upgrade(type).get(), itemName);
        add(type.translationKey(), statName);
        add(type.descriptionKey(), description);
    }

    /** A Holo-Guide page: title + body, keyed {@code guide.automechs.<key>.title/.body}. */
    private void guidePage(String key, String title, String body) {
        add("guide.automechs." + key + ".title", title);
        add("guide.automechs." + key + ".body", body);
    }
}
