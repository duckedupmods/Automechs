package com.duckedupmods.automechs.registry;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.menu.AssemblyWorkshopMenu;
import com.duckedupmods.automechs.menu.ChargingPadMenu;
import com.duckedupmods.automechs.menu.CombustionDynamoMenu;
import com.duckedupmods.automechs.menu.MechAssemblyBenchMenu;
import com.duckedupmods.automechs.menu.MechDatabaseMenu;
import com.duckedupmods.automechs.menu.MechMenu;
import com.duckedupmods.automechs.menu.TerminalMenu;
import com.duckedupmods.automechs.menu.UpgradeStationMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu (container) types registered by Automechs.
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Automechs.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<MechMenu>> MECH =
            MENUS.register("mech", () -> IMenuTypeExtension.create(MechMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<AssemblyWorkshopMenu>> ASSEMBLY_WORKSHOP =
            MENUS.register("assembly_workshop", () -> IMenuTypeExtension.create(AssemblyWorkshopMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MechAssemblyBenchMenu>> MECH_ASSEMBLY_BENCH =
            MENUS.register("mech_assembly_bench", () -> IMenuTypeExtension.create(MechAssemblyBenchMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CombustionDynamoMenu>> COMBUSTION_DYNAMO =
            MENUS.register("combustion_dynamo", () -> IMenuTypeExtension.create(CombustionDynamoMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<UpgradeStationMenu>> UPGRADE_STATION =
            MENUS.register("upgrade_station", () -> IMenuTypeExtension.create(UpgradeStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ChargingPadMenu>> CHARGING_PAD =
            MENUS.register("charging_pad", () -> IMenuTypeExtension.create(ChargingPadMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> STORAGE_TERMINAL =
            MENUS.register("storage_terminal", () -> IMenuTypeExtension.create(TerminalMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MechDatabaseMenu>> MECH_DATABASE =
            MENUS.register("mech_database", () -> IMenuTypeExtension.create(
                    (containerId, inv, buf) -> new MechDatabaseMenu(containerId, inv)));

    private ModMenus() {}

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
