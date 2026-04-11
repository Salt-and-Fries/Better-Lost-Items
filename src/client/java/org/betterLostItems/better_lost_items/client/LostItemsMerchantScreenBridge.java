package org.betterLostItems.better_lost_items.client;

import org.betterLostItems.better_lost_items.TraderTabStatePayload;

/**
 * Client mixin bridge implemented by the vanilla merchant screen.
 */
public interface LostItemsMerchantScreenBridge {
    /**
     * @return menu/container ID currently displayed by the screen
     */
    int betterLostItems$getContainerId();

    /**
     * Applies server-provided tab counts and selected-tab state.
     */
    void betterLostItems$applyTabState(TraderTabStatePayload state);
}
