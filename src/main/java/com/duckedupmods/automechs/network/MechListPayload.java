package com.duckedupmods.automechs.network;

import java.util.List;

import com.duckedupmods.automechs.Automechs;
import com.duckedupmods.automechs.item.MechFolder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client. The viewing player's registered robot nodes plus their folders, pushed periodically
 * while the Mech Tablet dashboard is open so the canvas stays live.
 */
public record MechListPayload(List<MechSummary> mechs, List<MechFolder> folders) implements CustomPacketPayload {

    public static final Type<MechListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "mech_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MechListPayload> STREAM_CODEC = StreamCodec.composite(
            MechSummary.STREAM_CODEC.apply(ByteBufCodecs.list(64)), MechListPayload::mechs,
            MechFolder.STREAM_CODEC.apply(ByteBufCodecs.list(64)), MechListPayload::folders,
            MechListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
