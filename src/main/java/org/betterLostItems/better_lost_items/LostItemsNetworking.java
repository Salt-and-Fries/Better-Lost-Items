package org.betterLostItems.better_lost_items;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers custom payload codecs and serverbound handlers shared by client and server.
 *
 * <p>Handlers hop onto the server thread before touching menus or storage. That keeps packet
 * processing safe even though Fabric receives networking callbacks off the main gameplay path.</p>
 */
public final class LostItemsNetworking {
    private LostItemsNetworking() {
    }

    /**
     * Registers all common payload types and server receivers.
     */
    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(TraderTabStatePayload.TYPE, TraderTabStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RecoveryScreenPayload.TYPE, RecoveryScreenPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RecoveryScrollPayload.TYPE, RecoveryScrollPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SwitchTraderTabPayload.TYPE, SwitchTraderTabPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(OpenTraderMarketPayload.TYPE, OpenTraderMarketPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PurchaseRecoveryItemsPayload.TYPE, PurchaseRecoveryItemsPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CollectRecoveryItemPayload.TYPE, CollectRecoveryItemPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RecoveryScrollPayload.TYPE, (payload, context) ->
                // Scroll packets are accepted only for the exact menu instance currently open.
                context.player().level().getServer().execute(() -> {
                    if (context.player().containerMenu instanceof LostItemsRecoveryMenu menu && menu.containerId == payload.containerId()) {
                        menu.setScrollRows(payload.leftScrollRow(), payload.rightScrollRow());
                    }
                })
        );
        ServerPlayNetworking.registerGlobalReceiver(SwitchTraderTabPayload.TYPE, (payload, context) ->
                context.player().level().getServer().execute(() -> LostItemsTradeController.handleTabSwitch(context.player(), payload.recoveryTab()))
        );
        ServerPlayNetworking.registerGlobalReceiver(OpenTraderMarketPayload.TYPE, (payload, context) ->
                context.player().level().getServer().execute(() -> LostItemsTradeController.handleOpenMarket(context.player(), payload.traderEntityId()))
        );
        ServerPlayNetworking.registerGlobalReceiver(PurchaseRecoveryItemsPayload.TYPE, (payload, context) ->
                context.player().level().getServer().execute(() -> LostItemsTradeController.handlePurchaseRecovery(context.player(), payload.traderEntityId(), payload.emeraldOffer()))
        );
        ServerPlayNetworking.registerGlobalReceiver(CollectRecoveryItemPayload.TYPE, (payload, context) ->
                context.player().level().getServer().execute(() -> LostItemsTradeController.handleCollectRecoveryItem(context.player(), payload.traderEntityId(), payload.entryId()))
        );
    }
}
