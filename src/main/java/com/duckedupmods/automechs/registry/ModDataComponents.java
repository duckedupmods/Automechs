package com.duckedupmods.automechs.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.item.MechFolder;
import com.duckedupmods.automechs.item.MechNode;
import com.duckedupmods.automechs.item.UpgradeType;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom data components used to store state on item stacks (1.21 replaced item NBT with components).
 * The Linker uses these to remember the two area corners and the deposit chest a player has selected.
 */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Automechs.MODID);

    public static final Supplier<DataComponentType<BlockPos>> LINK_POS_1 = registerBlockPos("link_pos_1");
    public static final Supplier<DataComponentType<BlockPos>> LINK_POS_2 = registerBlockPos("link_pos_2");
    public static final Supplier<DataComponentType<BlockPos>> LINK_DEPOSIT = registerBlockPos("link_deposit");

    // ----- Chassis-item state: role + per-upgrade levels (preserved across pack-up/redeploy) -----

    /** The mech's role id (see {@code MechRole}), stamped onto a packed-up chassis. */
    public static final Supplier<DataComponentType<String>> MECH_ROLE = registerString("mech_role");

    /** One Integer "level" component per upgrade module, generated from {@link UpgradeType}. */
    private static final Map<UpgradeType, Supplier<DataComponentType<Integer>>> UPGRADES =
            new EnumMap<>(UpgradeType.class);
    static {
        for (UpgradeType type : UpgradeType.values()) {
            UPGRADES.put(type, registerInt("upgrade_" + type.id()));
        }
    }

    /** The chassis-stack component storing the installed level of the given module. */
    public static Supplier<DataComponentType<Integer>> upgrade(UpgradeType type) {
        return UPGRADES.get(type);
    }

    /** The Mech Tablet's database: the robots the player registered, with names + folders. */
    public static final Supplier<DataComponentType<List<MechNode>>> TABLET_NODES =
            COMPONENTS.register("tablet_nodes", () -> DataComponentType.<List<MechNode>>builder()
                    .persistent(MechNode.CODEC.listOf())
                    .networkSynchronized(MechNode.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    /** The Mech Tablet's folders (groups), including empty ones created with the New Group button. */
    public static final Supplier<DataComponentType<List<MechFolder>>> TABLET_FOLDERS =
            COMPONENTS.register("tablet_folders", () -> DataComponentType.<List<MechFolder>>builder()
                    .persistent(MechFolder.CODEC.listOf())
                    .networkSynchronized(MechFolder.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    private static Supplier<DataComponentType<BlockPos>> registerBlockPos(String name) {
        return COMPONENTS.register(name, () -> DataComponentType.<BlockPos>builder()
                .persistent(BlockPos.CODEC)
                .networkSynchronized(BlockPos.STREAM_CODEC)
                .build());
    }

    private static Supplier<DataComponentType<Integer>> registerInt(String name) {
        return COMPONENTS.register(name, () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build());
    }

    private static Supplier<DataComponentType<String>> registerString(String name) {
        return COMPONENTS.register(name, () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING)
                .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                .build());
    }

    private ModDataComponents() {}

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
