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
 * Client → server. A command the player issued from the Mech Tablet against one registered robot node
 * (identified by the mech's UUID). The server validates ownership / tablet possession before acting.
 */
public record MechCommandPayload(UUID id, int action) implements CustomPacketPayload {

    /** Toggle the mech's work routine on/off (mech must be loaded). */
    public static final int ACTION_TOGGLE = 0;
    /** Stop the mech and teleport it to the issuing player (mech must be loaded). */
    public static final int ACTION_RECALL = 1;
    /** Remove this node from the tablet's database. */
    public static final int ACTION_UNREGISTER = 2;

    public static final Type<MechCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "mech_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MechCommandPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MechCommandPayload::id,
            ByteBufCodecs.VAR_INT, MechCommandPayload::action,
            MechCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
