package org.betterLostItems.better_lost_items;

import java.util.UUID;

/**
 * Mixin bridge for item entities that originated from a player's death drops.
 */
public interface TrackedItemEntity {
    /**
     * @return death-owner player UUID, or {@code null} for unclaimed/world drops
     */
    UUID betterLostItems$getOwnerId();

    /**
     * Tags an item entity as belonging to a player's death loot.
     *
     * @param ownerId player UUID that should receive the item if it is lost
     */
    void betterLostItems$setOwnerId(UUID ownerId);
}
