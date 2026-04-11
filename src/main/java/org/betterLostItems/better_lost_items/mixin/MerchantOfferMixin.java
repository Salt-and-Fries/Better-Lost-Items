package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.item.trading.MerchantOffer;
import org.betterLostItems.better_lost_items.LostOfferData;
import org.betterLostItems.better_lost_items.LostItemsDebug;
import org.betterLostItems.better_lost_items.LostOfferTracking;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds Better Lost Items metadata to vanilla merchant offers.
 *
 * <p>The debug hooks around {@code take} were useful for payment-slot bugs and remain guarded so
 * they only log custom offers.</p>
 */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferMixin implements LostOfferTracking {
    @Unique
    private LostOfferData betterLostItems$offerData;

    /**
     * @return metadata attached by {@link org.betterLostItems.better_lost_items.LostItemsTradeController}
     */
    @Override
    public LostOfferData betterLostItems$getOfferData() {
        return this.betterLostItems$offerData;
    }

    /**
     * Stores metadata on a custom offer.
     */
    @Override
    public void betterLostItems$setOfferData(LostOfferData data) {
        this.betterLostItems$offerData = data;
    }

    /**
     * Logs payment slot state before Minecraft consumes a custom offer's costs.
     */
    @Inject(method = "take", at = @At("HEAD"))
    private void betterLostItems$logBeforeTake(net.minecraft.world.item.ItemStack costAStack, net.minecraft.world.item.ItemStack costBStack, CallbackInfoReturnable<Boolean> cir) {
        if (this.betterLostItems$offerData == null) {
            return;
        }

        MerchantOffer offer = (MerchantOffer) (Object) this;
        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] MerchantOffer.take BEFORE offerKind={} entryId={} inputA={} inputB={} offerCostA={} offerCostB={} result={} uses={}/{}",
                this.betterLostItems$offerData.kind(),
                this.betterLostItems$offerData.entryId(),
                LostItemsDebug.stack(costAStack),
                LostItemsDebug.stack(costBStack),
                LostItemsDebug.stack(offer.getCostA()),
                LostItemsDebug.stack(offer.getCostB()),
                LostItemsDebug.stack(offer.getResult()),
                offer.getUses(),
                offer.getMaxUses()
        );
    }

    /**
     * Logs payment slot state after Minecraft attempts to consume a custom offer's costs.
     */
    @Inject(method = "take", at = @At("RETURN"))
    private void betterLostItems$logAfterTake(net.minecraft.world.item.ItemStack costAStack, net.minecraft.world.item.ItemStack costBStack, CallbackInfoReturnable<Boolean> cir) {
        if (this.betterLostItems$offerData == null) {
            return;
        }

        MerchantOffer offer = (MerchantOffer) (Object) this;
        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] MerchantOffer.take AFTER offerKind={} entryId={} success={} remainingA={} remainingB={} uses={}/{}",
                this.betterLostItems$offerData.kind(),
                this.betterLostItems$offerData.entryId(),
                cir.getReturnValue(),
                LostItemsDebug.stack(costAStack),
                LostItemsDebug.stack(costBStack),
                offer.getUses(),
                offer.getMaxUses()
        );
    }
}
