package org.betterLostItems.better_lost_items;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-server request to switch from the custom recovery menu back to the trader market.
 *
 * @param traderEntityId wandering trader entity ID backing the open recovery menu
 */
public record OpenTraderMarketPayload(int traderEntityId) implements CustomPacketPayload {
    public static final Type<OpenTraderMarketPayload> TYPE = new Type<>(Better_lost_items.id("open_trader_market"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTraderMarketPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            OpenTraderMarketPayload::traderEntityId,
            OpenTraderMarketPayload::new
    );

    /**
     * @return custom payload type for this packet
     */
    @Override
    public Type<OpenTraderMarketPayload> type() {
        return TYPE;
    }
}
