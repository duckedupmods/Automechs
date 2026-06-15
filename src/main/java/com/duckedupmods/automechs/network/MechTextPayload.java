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
 * Client → server. Sets a text attribute on a registered robot node (by mech UUID) from the Mech Tablet:
 * its display name or its folder/group. The value is stored on the tablet's node; the name is also
 * mirrored onto the live mech (so it floats above it) when the mech is loaded.
 */
public record MechTextPayload(UUID id, int field, String value) implements CustomPacketPayload {

    /** Set the node's display name. */
    public static final int FIELD_NAME = 0;
    /** Set the node's folder/group. */
    public static final int FIELD_GROUP = 1;

    public static final Type<MechTextPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "mech_text"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MechTextPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MechTextPayload::id,
            ByteBufCodecs.VAR_INT, MechTextPayload::field,
            ByteBufCodecs.stringUtf8(64), MechTextPayload::value,
            MechTextPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
