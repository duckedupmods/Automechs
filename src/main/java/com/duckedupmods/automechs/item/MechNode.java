package com.duckedupmods.automechs.item;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One registered robot in a Mech Tablet's database, drawn as a draggable node on the dashboard canvas:
 * the mech's UUID, the player-assigned display name and folder/group, and the node's saved canvas
 * position ({@code x},{@code y}). Stored as a list in the {@code tablet_nodes} data component on the
 * tablet item, so the whole node graph (names, folders, layout) travels with the tablet and survives the
 * mech being unloaded.
 */
public record MechNode(UUID id, String name, String group, int role, int x, int y) {

    public static final Codec<MechNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(MechNode::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(MechNode::name),
            Codec.STRING.optionalFieldOf("group", "").forGetter(MechNode::group),
            Codec.INT.optionalFieldOf("role", 0).forGetter(MechNode::role),
            Codec.INT.optionalFieldOf("x", 0).forGetter(MechNode::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(MechNode::y)
    ).apply(instance, MechNode::new));

    public static final StreamCodec<ByteBuf, MechNode> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MechNode::id,
            ByteBufCodecs.STRING_UTF8, MechNode::name,
            ByteBufCodecs.STRING_UTF8, MechNode::group,
            ByteBufCodecs.VAR_INT, MechNode::role,
            ByteBufCodecs.VAR_INT, MechNode::x,
            ByteBufCodecs.VAR_INT, MechNode::y,
            MechNode::new);

    public MechNode withName(String newName) {
        return new MechNode(this.id, newName, this.group, this.role, this.x, this.y);
    }

    public MechNode withGroup(String newGroup) {
        return new MechNode(this.id, this.name, newGroup, this.role, this.x, this.y);
    }

    public MechNode withRole(int newRole) {
        return new MechNode(this.id, this.name, this.group, newRole, this.x, this.y);
    }

    public MechNode withPos(int newX, int newY) {
        return new MechNode(this.id, this.name, this.group, this.role, newX, newY);
    }
}
