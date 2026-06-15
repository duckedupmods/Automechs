package com.duckedupmods.automechs.network;

import java.util.UUID;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server. Sent when the player confirms a Builder ghost placement: the target mech, the anchor
 * (minimum corner) the structure is pinned to, and the chosen rotation ordinal. The server validates
 * ownership/distance, stores the anchor + rotation, and switches the mech on to start building.
 */
public record SetBuildAreaPayload(UUID id, BlockPos anchor, int rotation) implements CustomPacketPayload {

    public static final Type<SetBuildAreaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "set_build_area"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBuildAreaPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetBuildAreaPayload::id,
            BlockPos.STREAM_CODEC, SetBuildAreaPayload::anchor,
            ByteBufCodecs.VAR_INT, SetBuildAreaPayload::rotation,
            SetBuildAreaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
