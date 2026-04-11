package org.betterLostItems.better_lost_items;

/**
 * Identifies which Better Lost Items trading surface owns an offer.
 */
public enum LostOfferKind {
    /**
     * Public wandering-trader market populated from unlabeled despawned items.
     */
    MARKET,

    /**
     * Per-player death-loot recovery flow.
     */
    RECOVERY
}
