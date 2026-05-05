package org.betterLostItems.better_lost_items;

import net.minecraft.world.item.trading.MerchantOffers;

import java.util.List;
import java.util.UUID;

/**
 * Mixin bridge for persistent wandering-trader state added by this mod.
 *
 * <p>The market selection is stored on the trader entity so a specific trader
 * keeps the same offered items between screen opens and world saves.</p>
 */
public interface LostTraderSession {
    /**
     * @return currently active Better Lost Items tab for this trader
     */
    LostOfferKind betterLostItems$getActiveTab();

    /**
     * @param kind tab currently represented by the trader's offer/menu state
     */
    void betterLostItems$setActiveTab(LostOfferKind kind);

    /**
     * @return {@code true} once the trader has chosen its random market entries
     */
    boolean betterLostItems$hasLockedMarketSelection();

    /**
     * @param locked whether this trader should keep its current market IDs
     */
    void betterLostItems$setLockedMarketSelection(boolean locked);

    /**
     * @return storage entry IDs currently offered by this trader's market tab
     */
    List<UUID> betterLostItems$getMarketSelectionIds();

    /**
     * @param ids storage entry IDs this trader should offer
     */
    void betterLostItems$setMarketSelectionIds(List<UUID> ids);

    /**
     * @return copy of the trader's original vanilla offers before custom offers were applied
     */
    MerchantOffers betterLostItems$getRegularOffers();

    /**
     * @param offers vanilla wandering-trader offers to restore for the regular trading tab
     */
    void betterLostItems$setRegularOffers(MerchantOffers offers);
}
