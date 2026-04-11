package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for vanilla merchant menu internals used by tab switching and payment fixes.
 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuAccessor {
    /**
     * @return merchant entity backing this menu
     */
    @Accessor("trader")
    Merchant betterLostItems$getTrader();

    /**
     * @return internal payment/result container
     */
    @Accessor("tradeContainer")
    MerchantContainer betterLostItems$getTradeContainer();
}
