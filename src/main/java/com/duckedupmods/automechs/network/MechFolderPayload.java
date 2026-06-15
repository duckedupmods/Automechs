package com.duckedupmods.automechs.network;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server. Manages folders (groups) on the held Mech Tablet: create an (empty) folder, move it,
 * rename it (cascading to its member nodes), or delete it (un-filing its members).
 */
public record MechFolderPayload(int op, String name, String arg, int x, int y) implements CustomPacketPayload {

    public static final int OP_CREATE = 0;
    public static final int OP_MOVE = 1;
    public static final int OP_RENAME = 2;
    public static final int OP_DELETE = 3;
    /** Resize: {@code x}/{@code y} carry the new width/height. */
    public static final int OP_RESIZE = 4;

    public static final Type<MechFolderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "mech_folder"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MechFolderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MechFolderPayload::op,
            ByteBufCodecs.stringUtf8(32), MechFolderPayload::name,
            ByteBufCodecs.stringUtf8(32), MechFolderPayload::arg,
            ByteBufCodecs.VAR_INT, MechFolderPayload::x,
            ByteBufCodecs.VAR_INT, MechFolderPayload::y,
            MechFolderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
