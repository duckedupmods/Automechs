package com.duckedupmods.automechs.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A folder (group) on the Mech Tablet's node canvas: a name, an anchor position, and a player-controlled
 * size (resizable by dragging its corner). Folders exist independently of the robots filed in them — you
 * create an empty folder with the dashboard's "New Group" button, then drag robot nodes into it. Stored
 * as a {@code tablet_folders} list on the tablet.
 */
public record MechFolder(String name, int x, int y, int w, int h) {

    public static final int DEFAULT_W = 150;
    public static final int DEFAULT_H = 96;

    public static final Codec<MechFolder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(MechFolder::name),
            Codec.INT.optionalFieldOf("x", 0).forGetter(MechFolder::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(MechFolder::y),
            Codec.INT.optionalFieldOf("w", DEFAULT_W).forGetter(MechFolder::w),
            Codec.INT.optionalFieldOf("h", DEFAULT_H).forGetter(MechFolder::h)
    ).apply(instance, MechFolder::new));

    public static final StreamCodec<ByteBuf, MechFolder> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MechFolder::name,
            ByteBufCodecs.VAR_INT, MechFolder::x,
            ByteBufCodecs.VAR_INT, MechFolder::y,
            ByteBufCodecs.VAR_INT, MechFolder::w,
            ByteBufCodecs.VAR_INT, MechFolder::h,
            MechFolder::new);

    public MechFolder withPos(int newX, int newY) {
        return new MechFolder(this.name, newX, newY, this.w, this.h);
    }

    public MechFolder withSize(int newW, int newH) {
        return new MechFolder(this.name, this.x, this.y, newW, newH);
    }
}
