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
 * Client → server. Sent when the player confirms a quarry placement (the in-world overlay). Carries the
 * target mech and the centre block the box was placed on; the server recomputes the footprint from the
 * mech's Range and config (never trusting a client size), then assigns it as the mech's work order.
 */
public record SetQuarryPayload(UUID id, BlockPos center) implements CustomPacketPayload {

    public static final Type<SetQuarryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "set_quarry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetQuarryPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetQuarryPayload::id,
            BlockPos.STREAM_CODEC, SetQuarryPayload::center,
            SetQuarryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
