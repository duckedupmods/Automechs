package com.duckedupmods.automechs.network;

import java.util.UUID;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server. Persists a node's new canvas position after the player drags it on the Mech Tablet
 * dashboard. The server writes it back onto the tablet's stored node.
 */
public record MechMovePayload(UUID id, int x, int y) implements CustomPacketPayload {

    public static final Type<MechMovePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "mech_move"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MechMovePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MechMovePayload::id,
            ByteBufCodecs.VAR_INT, MechMovePayload::x,
            ByteBufCodecs.VAR_INT, MechMovePayload::y,
            MechMovePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
