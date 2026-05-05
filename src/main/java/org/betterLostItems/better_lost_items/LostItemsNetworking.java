package org.betterLostItems.better_lost_items;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.betterLostItems.better_lost_items.client.Better_lost_itemsClient;

/**
 * Registers custom payload codecs and serverbound handlers shared by client and server.
 *
 * <p>NeoForge invokes payload handlers on the main thread by default, so the handlers can touch
 * menus and storage directly after validating the sending player and current menu state.</p>
 */
public final class LostItemsNetworking {
    private LostItemsNetworking() {
    }

    /**
     * Registers all payload types and server/client receivers.
     */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(TraderTabStatePayload.TYPE, TraderTabStatePayload.STREAM_CODEC, LostItemsNetworking::handleTraderTabState);
        registrar.playToClient(RecoveryScreenPayload.TYPE, RecoveryScreenPayload.STREAM_CODEC, LostItemsNetworking::handleRecoveryScreen);
        registrar.playToServer(RecoveryScrollPayload.TYPE, RecoveryScrollPayload.STREAM_CODEC, LostItemsNetworking::handleRecoveryScroll);
        registrar.playToServer(SwitchTraderTabPayload.TYPE, SwitchTraderTabPayload.STREAM_CODEC, LostItemsNetworking::handleSwitchTraderTab);
        registrar.playToServer(OpenTraderMarketPayload.TYPE, OpenTraderMarketPayload.STREAM_CODEC, LostItemsNetworking::handleOpenTraderMarket);
        registrar.playToServer(PurchaseRecoveryItemsPayload.TYPE, PurchaseRecoveryItemsPayload.STREAM_CODEC, LostItemsNetworking::handlePurchaseRecoveryItems);
        registrar.playToServer(CollectRecoveryItemPayload.TYPE, CollectRecoveryItemPayload.STREAM_CODEC, LostItemsNetworking::handleCollectRecoveryItem);
    }

    private static void handleTraderTabState(TraderTabStatePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            Better_lost_itemsClient.handleTraderTabState(payload);
        }
    }

    private static void handleRecoveryScreen(RecoveryScreenPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            Better_lost_itemsClient.handleRecoveryScreen(payload);
        }
    }

    private static void handleRecoveryScroll(RecoveryScrollPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof LostItemsRecoveryMenu menu
                && menu.containerId == payload.containerId()) {
            menu.setScrollRows(payload.leftScrollRow(), payload.rightScrollRow());
        }
    }

    private static void handleSwitchTraderTab(SwitchTraderTabPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LostItemsTradeController.handleTabSwitch(player, payload.recoveryTab());
        }
    }

    private static void handleOpenTraderMarket(OpenTraderMarketPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LostItemsTradeController.handleOpenMarket(player, payload.traderEntityId());
        }
    }

    private static void handlePurchaseRecoveryItems(PurchaseRecoveryItemsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LostItemsTradeController.handlePurchaseRecovery(player, payload.traderEntityId(), payload.emeraldOffer());
        }
    }

    private static void handleCollectRecoveryItem(CollectRecoveryItemPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LostItemsTradeController.handleCollectRecoveryItem(player, payload.traderEntityId(), payload.entryId());
        }
    }
}
