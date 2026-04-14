package org.betterLostItems.better_lost_items;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-server scroll update for the two infinite-feeling recovery grids.
 *
 * @param containerId menu ID to protect against stale packets
 * @param leftScrollRow requested lost-loot grid row
 * @param rightScrollRow requested retrieved-loot grid row
 */
public record RecoveryScrollPayload(int containerId, int leftScrollRow, int rightScrollRow) implements CustomPacketPayload {
    public static final Type<RecoveryScrollPayload> TYPE = new CustomPacketPayload.Type<>(Better_lost_items.id("recovery_scroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecoveryScrollPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RecoveryScrollPayload::containerId,
            ByteBufCodecs.VAR_INT,
            RecoveryScrollPayload::leftScrollRow,
            ByteBufCodecs.VAR_INT,
            RecoveryScrollPayload::rightScrollRow,
            RecoveryScrollPayload::new
    );

    /**
     * @return NeoForge custom payload type for this packet
     */
    @Override
    public Type<RecoveryScrollPayload> type() {
        return TYPE;
    }
}
