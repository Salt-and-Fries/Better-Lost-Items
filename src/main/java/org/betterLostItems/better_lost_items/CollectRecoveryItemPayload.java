package org.betterLostItems.better_lost_items;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Client-to-server request from the legacy recovery screen to collect one retrieved stack.
 *
 * <p>The current container-based UI no longer relies on this for normal item movement, but the
 * packet remains registered so older code paths and development tests fail safely.</p>
 *
 * @param traderEntityId trader entity the client believes it is interacting with
 * @param entryId retrieved-loot entry to collect
 */
public record CollectRecoveryItemPayload(int traderEntityId, UUID entryId) implements CustomPacketPayload {
    public static final Type<CollectRecoveryItemPayload> TYPE = new CustomPacketPayload.Type<>(Better_lost_items.id("collect_recovery_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CollectRecoveryItemPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
            CollectRecoveryItemPayload::traderEntityId,
            UUIDUtil.STREAM_CODEC,
            CollectRecoveryItemPayload::entryId,
            CollectRecoveryItemPayload::new
    );

    /**
     * @return NeoForge custom payload type for this packet
     */
    @Override
    public Type<CollectRecoveryItemPayload> type() {
        return TYPE;
    }
}
