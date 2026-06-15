package com.duckedupmods.automechs.network;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client → server. A withdraw request from the Storage Terminal GUI: pull up to {@code amount} of the item
 * matching {@code target} out of the network and hand it to the requesting player. The server re-validates
 * that the player has the terminal menu open and that the network is online before honouring it.
 */
public record StorageRequestPayload(ItemStack target, int amount) implements CustomPacketPayload {

    public static final Type<StorageRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "storage_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, StorageRequestPayload::target,
            ByteBufCodecs.VAR_INT, StorageRequestPayload::amount,
            StorageRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
