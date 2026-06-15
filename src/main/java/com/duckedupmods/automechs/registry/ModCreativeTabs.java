package com.duckedupmods.automechs.registry;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.UpgradeType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Automechs creative tab and its contents.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Automechs.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AUTOMECHS = TABS.register(
            "automechs", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.automechs"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.MECH_CHASSIS.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Guide
                        output.accept(ModItems.HOLO_GUIDE.get());
                        // Machines
                        output.accept(ModItems.ASSEMBLY_WORKSHOP.get());
                        output.accept(ModItems.MECH_ASSEMBLY_BENCH.get());
                        output.accept(ModItems.UPGRADE_STATION.get());
                        output.accept(ModItems.COMBUSTION_DYNAMO.get());
                        output.accept(ModItems.POWER_CONDUIT.get());
                        output.accept(ModItems.CHARGING_PAD.get());
                        output.accept(ModItems.MAIN_DRIVE.get());
                        output.accept(ModItems.DATA_CABLE.get());
                        output.accept(ModItems.DATA_RACK.get());
                        output.accept(ModItems.STORAGE_TERMINAL.get());
                        // Parts
                        output.accept(ModItems.MECH_PLATES.get());
                        output.accept(ModItems.MECH_CORE.get());
                        output.accept(ModItems.AI_CHIP.get());
                        for (MechRole role : MechRole.values()) {
                            output.accept(ModItems.circuit(role).get());
                        }
                        // Upgrades
                        for (UpgradeType type : UpgradeType.values()) {
                            output.accept(ModItems.upgrade(type).get());
                        }
                        // Mechs & tools
                        output.accept(ModItems.MECH_CHASSIS.get());
                        output.accept(ModItems.MECH_CHASSIS_T2.get());
                        output.accept(ModItems.MECH_CHASSIS_T3.get());
                        output.accept(ModItems.MECH_LINKER.get());
                        output.accept(ModItems.MECH_TABLET.get());
                        output.accept(ModItems.CACHE_CRAWLER_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
