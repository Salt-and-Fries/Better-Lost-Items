package org.betterLostItems.better_lost_items;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-to-server request from the vanilla merchant screen to open the custom recovery tab.
 *
 * @param containerId merchant menu ID to protect against stale packets
 * @param recoveryTab {@code true} when switching to recovery; reserved for future tab states
 */
public record SwitchTraderTabPayload(int containerId, boolean recoveryTab) implements CustomPacketPayload {
    public static final Type<SwitchTraderTabPayload> TYPE = CustomPacketPayload.createType("switch_trader_tab");
    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchTraderTabPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SwitchTraderTabPayload::containerId,
            ByteBufCodecs.BOOL,
            SwitchTraderTabPayload::recoveryTab,
            SwitchTraderTabPayload::new
    );

    /**
     * @return Fabric custom payload type for this packet
     */
    @Override
    public Type<SwitchTraderTabPayload> type() {
        return TYPE;
    }
}
