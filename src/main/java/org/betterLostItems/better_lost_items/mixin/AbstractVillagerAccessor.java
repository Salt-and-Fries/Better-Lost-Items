package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for replacing a wandering trader's offer list with custom lost-item offers.
 */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {
    /**
     * Reads the target villager's current trade offers.
     */
    @Accessor("offers")
    MerchantOffers betterLostItems$getOffers();

    /**
     * Replaces the target villager's trade offers.
     */
    @Accessor("offers")
    void betterLostItems$setOffers(MerchantOffers offers);
}
