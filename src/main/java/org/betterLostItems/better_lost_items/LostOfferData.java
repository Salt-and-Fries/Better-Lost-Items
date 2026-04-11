package org.betterLostItems.better_lost_items;

import java.util.UUID;

/**
 * Metadata attached to custom wandering-trader offers.
 *
 * <p>Vanilla {@code MerchantOffer}s do not know which storage entry created
 * them. This small record is attached through a mixin so completed trades can
 * remove the correct lost-item entry after purchase.</p>
 *
 * @param kind source tab/pool that created the offer
 * @param playerId player owner for per-player offers; currently unused by market offers
 * @param entryId storage entry ID represented by the offer result
 */
public record LostOfferData(LostOfferKind kind, UUID playerId, UUID entryId) {
}
