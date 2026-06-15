package com.duckedupmods.automechs.network;

import java.util.UUID;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server. Sent when the player confirms a farm-area placement (the in-world overlay). Carries
 * the target mech and the centre block the flat field was placed on; the server recomputes the footprint
 * from the mech's selected Range tier (never trusting a client size) and assigns it as the work order.
 */
public record SetFarmAreaPayload(UUID id, BlockPos center) implements CustomPacketPayload {

    public static final Type<SetFarmAreaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "set_farm_area"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFarmAreaPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetFarmAreaPayload::id,
            BlockPos.STREAM_CODEC, SetFarmAreaPayload::center,
            SetFarmAreaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
