package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerLevel;
import org.betterLostItems.better_lost_items.LostItemsTradeController;
import org.betterLostItems.better_lost_items.LostItemsTraderJourneyManager;
import org.betterLostItems.better_lost_items.LostOfferKind;
import org.betterLostItems.better_lost_items.LostTraderSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Replaces wandering-trader interaction with Better Lost Items tab-aware behavior.
 *
 * <p>The mixin also stores each trader's locked public market selection. That makes offers stable
 * across closing/reopening the UI and across world saves.</p>
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin implements LostTraderSession {
    @Unique
    private LostOfferKind betterLostItems$activeTab = LostOfferKind.MARKET;
    @Unique
    private boolean betterLostItems$marketSelectionLocked;
    @Unique
    private final List<UUID> betterLostItems$marketSelectionIds = new ArrayList<>();

    /**
     * @return active Better Lost Items tab represented by this trader's offer state
     */
    @Override
    public LostOfferKind betterLostItems$getActiveTab() {
        return this.betterLostItems$activeTab;
    }

    /**
     * Records which Better Lost Items tab is currently active.
     */
    @Override
    public void betterLostItems$setActiveTab(LostOfferKind kind) {
        this.betterLostItems$activeTab = kind;
    }

    /**
     * @return whether this trader has chosen and locked its market entries
     */
    @Override
    public boolean betterLostItems$hasLockedMarketSelection() {
        return this.betterLostItems$marketSelectionLocked;
    }

    /**
     * Sets whether the market selection should be reused.
     */
    @Override
    public void betterLostItems$setLockedMarketSelection(boolean locked) {
        this.betterLostItems$marketSelectionLocked = locked;
    }

    /**
     * @return copied storage entry IDs for this trader's market offers
     */
    @Override
    public List<UUID> betterLostItems$getMarketSelectionIds() {
        return new ArrayList<>(this.betterLostItems$marketSelectionIds);
    }

    /**
     * Replaces the locked market entry IDs.
     */
    @Override
    public void betterLostItems$setMarketSelectionIds(List<UUID> ids) {
        this.betterLostItems$marketSelectionIds.clear();
        this.betterLostItems$marketSelectionIds.addAll(ids);
    }

    /**
     * Opens either the market tab or recovery tab instead of vanilla wandering-trader behavior.
     */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void betterLostItems$handleLostItemsTrading(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        WanderingTrader trader = (WanderingTrader) (Object) this;
        if (LostItemsTraderJourneyManager.isDeparting(trader)) {
            cir.setReturnValue(player.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME);
            return;
        }

        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.is(Items.VILLAGER_SPAWN_EGG) || !trader.isAlive() || trader.isTrading() || trader.isBaby()) {
            return;
        }

        if (hand == InteractionHand.MAIN_HAND) {
            player.awardStat(Stats.TALKED_TO_VILLAGER);
        }

        if (player.level().isClientSide()) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer && LostItemsTradeController.openPreferredScreen(trader, serverPlayer)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        cir.setReturnValue(InteractionResult.CONSUME);
    }

    /**
     * Locks a market selection when vanilla generates trader offers during spawn.
     */
    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void betterLostItems$lockMarketSelectionOnTradeGeneration(ServerLevel serverLevel, CallbackInfo ci) {
        if (!this.betterLostItems$marketSelectionLocked) {
            LostItemsTradeController.ensureMarketSelectionLocked((WanderingTrader) (Object) this, serverLevel.getServer());
        }
    }

    /**
     * Persists custom market selection data on the trader entity.
     */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void betterLostItems$saveMarketSelection(ValueOutput output, CallbackInfo ci) {
        output.putBoolean("BetterLostItemsMarketSelectionLocked", this.betterLostItems$marketSelectionLocked);
        output.store("BetterLostItemsMarketSelectionIds", UUIDUtil.CODEC.listOf(), List.copyOf(this.betterLostItems$marketSelectionIds));
    }

    /**
     * Restores custom market selection data from the trader entity save data.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void betterLostItems$loadMarketSelection(ValueInput input, CallbackInfo ci) {
        this.betterLostItems$marketSelectionLocked = input.getBooleanOr("BetterLostItemsMarketSelectionLocked", false);
        this.betterLostItems$marketSelectionIds.clear();
        this.betterLostItems$marketSelectionIds.addAll(input.read("BetterLostItemsMarketSelectionIds", UUIDUtil.CODEC.listOf()).orElse(List.of()));
    }
}
