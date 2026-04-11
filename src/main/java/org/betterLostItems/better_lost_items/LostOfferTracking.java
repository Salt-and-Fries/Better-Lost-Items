package org.betterLostItems.better_lost_items;

/**
 * Mixin bridge for attaching {@link LostOfferData} to vanilla merchant offers.
 */
public interface LostOfferTracking {
    /**
     * @return Better Lost Items metadata for this offer, or {@code null} for vanilla offers
     */
    LostOfferData betterLostItems$getOfferData();

    /**
     * Stores metadata on a merchant offer so trade-completion hooks can update storage.
     *
     * @param data metadata to attach
     */
    void betterLostItems$setOfferData(LostOfferData data);
}
