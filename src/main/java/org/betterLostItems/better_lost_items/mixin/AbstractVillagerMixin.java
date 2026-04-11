package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.betterLostItems.better_lost_items.LostItemsTradeController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes completed villager trades so custom market entries can be removed from storage.
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
    /**
     * Delegates completed trades to the Better Lost Items controller.
     */
    @Inject(method = "notifyTrade", at = @At("TAIL"))
    private void betterLostItems$handleTradePurchase(MerchantOffer offer, CallbackInfo ci) {
        LostItemsTradeController.handleCompletedTrade((AbstractVillager) (Object) this, offer);
    }
}
