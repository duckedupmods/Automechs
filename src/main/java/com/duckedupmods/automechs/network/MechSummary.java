package com.duckedupmods.automechs.network;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A network-friendly snapshot of one registered robot node for the Mech Tablet dashboard. {@code id} is
 * the mech's UUID; {@code name}/{@code group} come from the tablet's stored node (so they show even while
 * the mech is unloaded). The live fields are only meaningful when {@code online} is true (the mech is
 * loaded); otherwise they are placeholders.
 */
public record MechSummary(UUID id, String name, String group, boolean online, int roleOrdinal,
                          boolean enabled, boolean working, int energy, int maxEnergy, boolean hasArea,
                          BlockPos pos, int usedSlots, int totalSlots, int canvasX, int canvasY) {

    public static final StreamCodec<RegistryFriendlyByteBuf, MechSummary> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeUUID(s.id);
                buf.writeUtf(s.name, 64);
                buf.writeUtf(s.group, 32);
                buf.writeBoolean(s.online);
                buf.writeByte(s.roleOrdinal);
                buf.writeBoolean(s.enabled);
                buf.writeBoolean(s.working);
                buf.writeVarInt(s.energy);
                buf.writeVarInt(s.maxEnergy);
                buf.writeBoolean(s.hasArea);
                buf.writeBlockPos(s.pos);
                buf.writeVarInt(s.usedSlots);
                buf.writeVarInt(s.totalSlots);
                buf.writeVarInt(s.canvasX);
                buf.writeVarInt(s.canvasY);
            },
            buf -> new MechSummary(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readUtf(32),
                    buf.readBoolean(),
                    buf.readByte(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBlockPos(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));
}
