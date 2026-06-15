package com.duckedupmods.automechs.network;

import java.util.List;

import com.duckedupmods.automechs.Automechs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client. A live snapshot of a Storage Terminal's network: the aggregated item list plus the
 * network status (online flag, total item count, rack count, stored FE). Pushed periodically while the
 * terminal GUI is open so the AE2-style item grid stays current.
 */
public record StorageSnapshotPayload(List<NetworkItem> items, boolean online, int itemCount, int rackCount,
                                     int storedFe) implements CustomPacketPayload {

    /** How many distinct item entries a single snapshot carries (matches the codec cap). */
    public static final int MAX_ITEMS = 256;

    public static final Type<StorageSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Automechs.MODID, "storage_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            NetworkItem.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), StorageSnapshotPayload::items,
            ByteBufCodecs.BOOL, StorageSnapshotPayload::online,
            ByteBufCodecs.VAR_INT, StorageSnapshotPayload::itemCount,
            ByteBufCodecs.VAR_INT, StorageSnapshotPayload::rackCount,
            ByteBufCodecs.VAR_INT, StorageSnapshotPayload::storedFe,
            StorageSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
