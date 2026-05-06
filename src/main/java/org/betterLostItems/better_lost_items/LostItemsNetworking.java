package org.betterLostItems.better_lost_items;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers custom payload codecs and serverbound handlers shared by client and server.
 *
 * <p>Handlers hop onto the server thread before touching menus or storage. That keeps packet
 * processing safe even though networking callbacks can arrive off the main gameplay path.</p>
 */
public final class LostItemsNetworking {
    private LostItemsNetworking() {
    }

    /**
     * Registers all common payload types and server receivers.
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Better_lost_items.MOD_ID).versioned("1");
        registrar.playToClient(TraderTabStatePayload.TYPE, TraderTabStatePayload.STREAM_CODEC, LostItemsNetworking::handleTraderTabState);
        registrar.playToClient(RecoveryScreenPayload.TYPE, RecoveryScreenPayload.STREAM_CODEC, LostItemsNetworking::handleRecoveryScreen);
        registrar.playToServer(RecoveryScrollPayload.TYPE, RecoveryScrollPayload.STREAM_CODEC, LostItemsNetworking::handleRecoveryScroll);
        registrar.playToServer(SwitchTraderTabPayload.TYPE, SwitchTraderTabPayload.STREAM_CODEC, LostItemsNetworking::handleSwitchTraderTab);
        registrar.playToServer(OpenTraderMarketPayload.TYPE, OpenTraderMarketPayload.STREAM_CODEC, LostItemsNetworking::handleOpenTraderMarket);
        registrar.playToServer(PurchaseRecoveryItemsPayload.TYPE, PurchaseRecoveryItemsPayload.STREAM_CODEC, LostItemsNetworking::handlePurchaseRecoveryItems);
        registrar.playToServer(CollectRecoveryItemPayload.TYPE, CollectRecoveryItemPayload.STREAM_CODEC, LostItemsNetworking::handleCollectRecoveryItem);
    }

    private static void handleTraderTabState(TraderTabStatePayload payload, IPayloadContext context) {
        org.betterLostItems.better_lost_items.client.LostItemsClientState.handleTraderTabState(payload, context);
    }

    private static void handleRecoveryScreen(RecoveryScreenPayload payload, IPayloadContext context) {
        org.betterLostItems.better_lost_items.client.LostItemsClientState.handleRecoveryScreen(payload, context);
    }

    private static void handleRecoveryScroll(RecoveryScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player.containerMenu instanceof LostItemsRecoveryMenu menu && menu.containerId == payload.containerId()) {
                menu.setScrollRows(payload.leftScrollRow(), payload.rightScrollRow());
            }
        });
    }

    private static void handleSwitchTraderTab(SwitchTraderTabPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LostItemsTradeController.handleTabSwitch((ServerPlayer) context.player(), payload.recoveryTab())
        );
    }

    private static void handleOpenTraderMarket(OpenTraderMarketPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LostItemsTradeController.handleOpenMarket((ServerPlayer) context.player(), payload.traderEntityId())
        );
    }

    private static void handlePurchaseRecoveryItems(PurchaseRecoveryItemsPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LostItemsTradeController.handlePurchaseRecovery((ServerPlayer) context.player(), payload.traderEntityId(), payload.emeraldOffer())
        );
    }

    private static void handleCollectRecoveryItem(CollectRecoveryItemPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                LostItemsTradeController.handleCollectRecoveryItem((ServerPlayer) context.player(), payload.traderEntityId(), payload.entryId())
        );
    }
}
