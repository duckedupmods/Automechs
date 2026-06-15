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
 * Client → server. Sends a schematic the player picked from their local {@code schematics/} folder so the
 * (server-authoritative) Builder mech can hold it: the target mech, a display name, and the raw gzipped-NBT
 * file bytes. The server re-parses the bytes itself (never trusting a client-side parse), enforces the size
 * cap, and stamps the resulting blueprint onto the mech. Bytes are length-capped in the codec.
 */
public record UploadSchematicPayload(UUID id, String name, byte[] data) implements CustomPacketPayload {

    /** Hard wire-size ceiling on a schematic upload (2 MiB of gzipped NBT — plenty for a 50k-block build). */
    public static final int MAX_BYTES = 2_000_000;

    public static final Type<UploadSchematicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "upload_schematic"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UploadSchematicPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, UploadSchematicPayload::id,
            ByteBufCodecs.stringUtf8(256), UploadSchematicPayload::name,
            ByteBufCodecs.byteArray(MAX_BYTES), UploadSchematicPayload::data,
            UploadSchematicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
