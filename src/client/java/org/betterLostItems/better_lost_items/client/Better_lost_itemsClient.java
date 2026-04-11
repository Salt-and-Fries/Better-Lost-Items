package org.betterLostItems.better_lost_items.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.RecoveryScreenPayload;
import org.betterLostItems.better_lost_items.TraderTabStatePayload;

/**
 * Client-only Fabric entry point for Better Lost Items.
 */
public class Better_lost_itemsClient implements ClientModInitializer {

    /**
     * Registers the recovery menu screen and clientbound packet handlers.
     */
    @Override
    public void onInitializeClient() {
        MenuScreens.register(Better_lost_items.LOST_ITEMS_RECOVERY_MENU, LostItemsRecoveryMenuScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(TraderTabStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> LostItemsClientState.apply(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(RecoveryScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> LostItemsClientState.openOrRefreshRecovery(payload))
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> LostItemsClientState.tick(client));
    }
}
