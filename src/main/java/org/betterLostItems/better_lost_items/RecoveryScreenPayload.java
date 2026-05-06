package org.betterLostItems.better_lost_items;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Legacy clientbound state packet for the old non-container recovery screen.
 *
 * <p>The active recovery UI is {@link LostItemsRecoveryMenu}; this payload is kept registered so
 * development worlds or stale client state do not crash if the old flow is opened.</p>
 *
 * @param traderEntityId trader entity backing the screen
 * @param marketCount number of public market offers available on the trader
 * @param lostItems copied lost-loot preview list
 * @param purchasedItems copied retrieved-loot list
 */
public record RecoveryScreenPayload(int traderEntityId, int marketCount, List<LostItemEntry> lostItems, List<LostItemEntry> purchasedItems) implements CustomPacketPayload {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<LostItemEntry>> LOST_ITEM_LIST_CODEC = LostItemEntry.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Type<RecoveryScreenPayload> TYPE = new Type<>(Better_lost_items.id("recovery_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecoveryScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RecoveryScreenPayload::traderEntityId,
            ByteBufCodecs.VAR_INT,
            RecoveryScreenPayload::marketCount,
            LOST_ITEM_LIST_CODEC,
            RecoveryScreenPayload::lostItems,
            LOST_ITEM_LIST_CODEC,
            RecoveryScreenPayload::purchasedItems,
            RecoveryScreenPayload::new
    );

    /**
     * @return custom payload type for this packet
     */
    @Override
    public Type<RecoveryScreenPayload> type() {
        return TYPE;
    }
}
