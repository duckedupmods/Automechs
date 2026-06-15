package com.duckedupmods.automechs.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * One aggregated entry in a Storage Terminal's network view: a single representative {@link ItemStack}
 * (for the icon / tooltip / components) plus the total count of that item across every connected Data Rack.
 * The total is carried separately as a VarInt so it can exceed a single stack's max size.
 */
public record NetworkItem(ItemStack icon, int count) {

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkItem> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, NetworkItem::icon,
            ByteBufCodecs.VAR_INT, NetworkItem::count,
            NetworkItem::new);
}
