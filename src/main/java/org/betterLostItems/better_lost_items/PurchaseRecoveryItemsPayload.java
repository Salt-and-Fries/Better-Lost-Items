package org.betterLostItems.better_lost_items;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Legacy client-to-server request to purchase all death loot from the older standalone screen.
 *
 * <p>The container menu now performs payment through a real slot and vanilla button packet. This
 * payload remains registered for compatibility with the old screen class.</p>
 *
 * @param traderEntityId trader entity the client is interacting with
 * @param emeraldOffer amount the old screen offered; historically emerald-specific
 */
public record PurchaseRecoveryItemsPayload(int traderEntityId, int emeraldOffer) implements CustomPacketPayload {
    public static final Type<PurchaseRecoveryItemsPayload> TYPE = new CustomPacketPayload.Type<>(Better_lost_items.id("purchase_recovery_items"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseRecoveryItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PurchaseRecoveryItemsPayload::traderEntityId,
            ByteBufCodecs.VAR_INT,
            PurchaseRecoveryItemsPayload::emeraldOffer,
            PurchaseRecoveryItemsPayload::new
    );

    /**
     * @return NeoForge custom payload type for this packet
     */
    @Override
    public Type<PurchaseRecoveryItemsPayload> type() {
        return TYPE;
    }
}
