package com.duckedupmods.automechs.datagen;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.entity.MechRole;
import com.duckedupmods.automechs.item.UpgradeType;
import com.duckedupmods.automechs.registry.ModItems;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Generates item models for non-block items. {@code basicItem} emits a
 * {@code minecraft:item/generated} model with {@code layer0} at {@code automechs:item/<name>}, so
 * each item needs a matching texture under {@code assets/automechs/textures/item/}.
 *
 * <p>Block items (e.g. the Assembly Workshop) get their item models from the block state provider via
 * {@code simpleBlockWithItem}, so they are not handled here.
 */
public class AutomechsItemModelProvider extends ItemModelProvider {

    public AutomechsItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Automechs.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.MECH_CHASSIS.get());
        basicItem(ModItems.MECH_CHASSIS_T2.get());
        basicItem(ModItems.MECH_CHASSIS_T3.get());
        basicItem(ModItems.MECH_LINKER.get());
        // MECH_ASSEMBLY_BENCH uses a hand-authored builtin/entity item model so its GeckoLib block model
        // renders in the inventory/hand (see AutomechsClient GeoBlockItemRenderer) — not a flat icon.
        basicItem(ModItems.MECH_PLATES.get());
        basicItem(ModItems.MECH_CORE.get());
        basicItem(ModItems.AI_CHIP.get());
        for (MechRole role : MechRole.values()) {
            basicItem(ModItems.circuit(role).get());
        }
        for (UpgradeType type : UpgradeType.values()) {
            basicItem(ModItems.upgrade(type).get());
        }
        basicItem(ModItems.MECH_TABLET.get());
        basicItem(ModItems.HOLO_GUIDE.get());
    }
}
