package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.LostItemsDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes vanilla merchant auto-fill behavior for custom one-use lost-item trades.
 *
 * <p>Vanilla can move more items than needed into the payment slots. For our market trades that
 * made extra emeralds disappear until returned manually. This hook limits the auto-fill to exactly
 * the required cost.</p>
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {
    /**
     * Replaces vanilla payment auto-fill with exact-count movement for wandering traders.
     */
    @Inject(method = "moveFromInventoryToPaymentSlot", at = @At("HEAD"), cancellable = true)
    private void betterLostItems$moveOnlyRequiredCost(int paymentSlot, ItemCost cost, CallbackInfo ci) {
        MerchantMenuAccessor menuAccessor = (MerchantMenuAccessor) this;
        if (!(menuAccessor.betterLostItems$getTrader() instanceof WanderingTrader trader)) {
            return;
        }

        MerchantContainer tradeContainer = menuAccessor.betterLostItems$getTradeContainer();
        ItemStack paymentStack = tradeContainer.getItem(paymentSlot);
        ItemStack requiredStack = cost.itemStack();
        if (!paymentStack.isEmpty() && !ItemStack.isSameItemSameComponents(paymentStack, requiredStack)) {
            ci.cancel();
            return;
        }

        int targetCount = Math.min(cost.count(), requiredStack.getMaxStackSize());
        int currentCount = paymentStack.getCount();
        if (currentCount >= targetCount) {
            ci.cancel();
            return;
        }

        int remaining = targetCount - currentCount;
        NonNullList<Slot> slots = ((AbstractContainerMenuAccessor) this).betterLostItems$getSlots();
        for (int slotIndex = 3; slotIndex < 39 && remaining > 0; slotIndex++) {
            ItemStack inventoryStack = slots.get(slotIndex).getItem();
            if (inventoryStack.isEmpty() || !cost.test(inventoryStack)) {
                continue;
            }

            if (!paymentStack.isEmpty() && !ItemStack.isSameItemSameComponents(inventoryStack, paymentStack)) {
                continue;
            }

            int movedCount = Math.min(remaining, inventoryStack.getCount());
            currentCount += movedCount;
            remaining -= movedCount;
            paymentStack = inventoryStack.copyWithCount(currentCount);
            inventoryStack.shrink(movedCount);
            tradeContainer.setItem(paymentSlot, paymentStack);
        }

        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] MerchantMenu exact autofill trader={} paymentSlot={} required={} finalPayment={}",
                trader.getUUID(),
                paymentSlot,
                LostItemsDebug.cost(cost),
                LostItemsDebug.stack(tradeContainer.getItem(paymentSlot))
        );
        ci.cancel();
    }
}
