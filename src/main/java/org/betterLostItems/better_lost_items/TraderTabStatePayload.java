package org.betterLostItems.better_lost_items;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server-to-client state used to render Better Lost Items tabs on trader screens.
 *
 * @param containerId active menu ID
 * @param recoveryTab whether the recovery tab is active
 * @param marketCount count shown on the market tab
 * @param recoveryCount count shown on the recovery tab
 */
public record TraderTabStatePayload(int containerId, boolean recoveryTab, int marketCount, int recoveryCount) implements CustomPacketPayload {
    public static final Type<TraderTabStatePayload> TYPE = new Type<>(Better_lost_items.id("trader_tab_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TraderTabStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.CONTAINER_ID,
            TraderTabStatePayload::containerId,
            ByteBufCodecs.BOOL,
            TraderTabStatePayload::recoveryTab,
            ByteBufCodecs.VAR_INT,
            TraderTabStatePayload::marketCount,
            ByteBufCodecs.VAR_INT,
            TraderTabStatePayload::recoveryCount,
            TraderTabStatePayload::new
    );

    /**
     * @return custom payload type for this packet
     */
    @Override
    public Type<TraderTabStatePayload> type() {
        return TYPE;
    }
}
