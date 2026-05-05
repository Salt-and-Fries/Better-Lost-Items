package org.betterLostItems.better_lost_items;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.betterLostItems.better_lost_items.mixin.AbstractVillagerAccessor;
import org.betterLostItems.better_lost_items.mixin.MerchantMenuAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side coordinator for all wandering-trader interactions added by this mod.
 *
 * <p>The controller owns the boundary between vanilla merchant screens and the custom recovery
 * menu. Wandering traders keep their public market selection on the entity itself, while each
 * player sees their own death-loot recovery state pulled from {@link LostItemsStorage}.</p>
 */
public final class LostItemsTradeController {
    private static final int MARKET_LIMIT = 8;
    private static final double TRADER_INTERACTION_RANGE_SQR = 64.0D;
    private static final Set<UUID> MARKET_TRANSITIONS = ConcurrentHashMap.newKeySet();

    private LostItemsTradeController() {
    }

    /**
     * @return flat configured price for transferring all lost death loot into retrieved loot
     */
    public static int getRecoveryPrice() {
        return LostItemsConfig.deathLootPaymentAmount();
    }

    /**
     * @return whether the player is currently switching from the recovery menu back to merchant UI
     */
    public static boolean isTransitioningToTraderMarket(ServerPlayer player) {
        return MARKET_TRANSITIONS.contains(player.getUUID());
    }

    /**
     * Opens the trader's best available Better Lost Items screen.
     *
     * <p>The public market is preferred when this trader has offers. If not, the player still gets
     * the recovery tab so they can redeem, retrieve, or fetch death loot.</p>
     *
     * @return {@code true} when vanilla interaction should be considered handled
     */
    public static boolean openPreferredScreen(WanderingTrader trader, ServerPlayer player) {
        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        List<LostItemEntry> marketEntries = getLockedMarketEntries(trader, storage);
        int recoveryCount = getRecoveryTabCount(storage, player.getUUID());
        Better_lost_items.LOGGER.info("[BLI DEBUG] openPreferredScreen trader={} player={} marketCount={} recoveryCount={} lockedSelection={}",
                trader.getUUID(), LostItemsDebug.player(player), marketEntries.size(), recoveryCount, ((LostTraderSession) trader).betterLostItems$hasLockedMarketSelection());
        if (!marketEntries.isEmpty()) {
            openMarketScreen(player, trader, marketEntries, recoveryCount);
            return true;
        }

        openRecoveryScreen(player, trader, marketEntries.size());
        return true;
    }

    /**
     * Sends tab counts for the currently open merchant menu.
     */
    public static void sendCurrentTabState(ServerPlayer player, WanderingTrader trader) {
        if (!(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        if (!(((MerchantMenuAccessor) menu).betterLostItems$getTrader() == trader)) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        List<LostItemEntry> marketEntries = getLockedMarketEntries(trader, storage);
        ServerPlayNetworking.send(player, new TraderTabStatePayload(
                menu.containerId,
                false,
                marketEntries.size(),
                getRecoveryTabCount(storage, player.getUUID())
        ));
    }

    /**
     * Handles the client pressing the recovery tab on the vanilla merchant screen.
     */
    public static void handleTabSwitch(ServerPlayer player, boolean recoveryTab) {
        if (!recoveryTab) {
            return;
        }

        if (!(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        if (!(((MerchantMenuAccessor) menu).betterLostItems$getTrader() instanceof WanderingTrader trader)) {
            return;
        }

        player.closeContainer();
        openRecoveryScreen(player, trader, getLockedMarketEntries(trader, LostItemsStorageManager.get(player.level().getServer())).size());
    }

    /**
     * Handles the client pressing the market tab from the recovery menu.
     */
    public static void handleOpenMarket(ServerPlayer player, int traderEntityId) {
        WanderingTrader trader = getAccessibleTrader(player, traderEntityId);
        if (trader == null) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        List<LostItemEntry> marketEntries = getLockedMarketEntries(trader, storage);
        if (marketEntries.isEmpty()) {
            openRegularMarketScreen(player, trader, getRecoveryTabCount(storage, player.getUUID()));
            return;
        }

        MARKET_TRANSITIONS.add(player.getUUID());
        openMarketScreen(player, trader, marketEntries, getRecoveryTabCount(storage, player.getUUID()));
        player.level().getServer().execute(() -> MARKET_TRANSITIONS.remove(player.getUUID()));
    }

    /**
     * Legacy packet handler for the older non-container recovery screen.
     *
     * <p>The current UI pays through a real slot in {@link LostItemsRecoveryMenu}; this method is
     * retained so old client payloads fail safely instead of doing nothing mysterious.</p>
     */
    public static void handlePurchaseRecovery(ServerPlayer player, int traderEntityId, int emeraldOffer) {
        WanderingTrader trader = getAccessibleTrader(player, traderEntityId);
        if (trader == null || emeraldOffer != getRecoveryPrice()) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        if (storage.getPlayerLostItems(player.getUUID()).isEmpty()) {
            openRecoveryScreen(player, trader, getLockedMarketEntries(trader, storage).size());
            return;
        }

        if (!removeRecoveryPayment(player, getRecoveryPrice())) {
            openRecoveryScreen(player, trader, getLockedMarketEntries(trader, storage).size());
            return;
        }

        storage.movePlayerLostItemsToPurchased(player.getUUID());
        openRecoveryScreen(player, trader, getLockedMarketEntries(trader, storage).size());
    }

    /**
     * Legacy packet handler for collecting one retrieved item from the older custom screen.
     */
    public static void handleCollectRecoveryItem(ServerPlayer player, int traderEntityId, UUID entryId) {
        WanderingTrader trader = getAccessibleTrader(player, traderEntityId);
        if (trader == null) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        Optional<LostItemEntry> entry = storage.getPlayerPurchasedItem(player.getUUID(), entryId);
        if (entry.isEmpty()) {
            openRecoveryScreen(player, trader, getLockedMarketEntries(trader, storage).size());
            return;
        }

        ItemStack remaining = entry.get().stack().copy();
        player.getInventory().add(remaining);
        if (remaining.isEmpty()) {
            storage.removePlayerPurchasedItem(player.getUUID(), entryId);
        } else {
            storage.replacePlayerPurchasedItem(player.getUUID(), entryId, remaining);
        }

        openRecoveryScreen(player, trader, getLockedMarketEntries(trader, storage).size());
    }

    /**
     * Called after a wandering-trader trade completes.
     *
     * <p>Only custom market offers carry {@link LostOfferData}. When one is purchased, the matching
     * storage entry is reduced by the result stack count and the trader's locked selection is
     * updated so the same item is not offered again.</p>
     */
    public static void handleCompletedTrade(AbstractVillager villager, MerchantOffer offer) {
        if (!(villager instanceof WanderingTrader trader)) {
            return;
        }

        LostOfferData offerData = ((LostOfferTracking) offer).betterLostItems$getOfferData();
        if (offerData == null || offerData.kind() != LostOfferKind.MARKET) {
            return;
        }

        if (!(villager.getTradingPlayer() instanceof ServerPlayer player)) {
            return;
        }

        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] handleCompletedTrade trader={} player={} offerKind={} entryId={} offerCostA={} offerCostB={} result={} emeraldsAfterTrade={}",
                trader.getUUID(),
                LostItemsDebug.player(player),
                offerData.kind(),
                offerData.entryId(),
                LostItemsDebug.stack(offer.getCostA()),
                LostItemsDebug.stack(offer.getCostB()),
                LostItemsDebug.stack(offer.getResult()),
                LostItemsDebug.emeraldCount(player)
        );

        returnPaymentRemainders(player, trader);

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        storage.removeUnclaimedAmount(offerData.entryId(), offer.getResult().getCount());
        LostTraderSession session = (LostTraderSession) trader;
        List<UUID> selectionIds = session.betterLostItems$getMarketSelectionIds();
        selectionIds.remove(offerData.entryId());
        session.betterLostItems$setMarketSelectionIds(selectionIds);

        MinecraftServer server = player.level().getServer();
        server.execute(() -> refreshAfterTrade(player, trader));
    }

    /**
     * Rebuilds the open merchant menu after a custom trade consumes one market entry.
     */
    private static void refreshAfterTrade(ServerPlayer player, WanderingTrader trader) {
        if (!player.isAlive()) {
            return;
        }

        if (!(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        if (!(((MerchantMenuAccessor) menu).betterLostItems$getTrader() == trader)) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        List<LostItemEntry> marketEntries = getLockedMarketEntries(trader, storage);
        MerchantOffers offers = buildMarketOffers(marketEntries);
        applyOffers(trader, offers, LostOfferKind.MARKET);
        resetMenu(menu, offers);
        player.sendMerchantOffers(menu.containerId, offers, 1, trader.getVillagerXp(), trader.showProgressBar(), trader.canRestock());
        ServerPlayNetworking.send(player, new TraderTabStatePayload(
                menu.containerId,
                false,
                marketEntries.size(),
                getRecoveryTabCount(storage, player.getUUID())
        ));
    }

    /**
     * Opens the vanilla merchant UI populated with this trader's locked market entries.
     */
    private static void openMarketScreen(ServerPlayer player, WanderingTrader trader, List<LostItemEntry> marketEntries, int recoveryCount) {
        MerchantOffers offers = buildMarketOffers(marketEntries);
        applyOffers(trader, offers, LostOfferKind.MARKET);
        Better_lost_items.LOGGER.info("[BLI DEBUG] openMarketScreen trader={} player={} offerCount={} recoveryCount={} emeraldsBeforeOpen={}",
                trader.getUUID(), LostItemsDebug.player(player), offers.size(), recoveryCount, LostItemsDebug.emeraldCount(player));
        trader.setTradingPlayer(player);
        trader.openTradingScreen(player, trader.getDisplayName(), 1);
        sendCurrentTabState(player, trader);
        if (player.containerMenu instanceof MerchantMenu menu) {
            ServerPlayNetworking.send(player, new TraderTabStatePayload(
                    menu.containerId,
                    false,
                    marketEntries.size(),
                    recoveryCount
            ));
        }
    }

    /**
     * Opens the trader's current vanilla merchant UI when there are no custom market offers.
     */
    private static void openRegularMarketScreen(ServerPlayer player, WanderingTrader trader, int recoveryCount) {
        MerchantOffers offers = ((LostTraderSession) trader).betterLostItems$getRegularOffers();
        if (!offers.isEmpty()) {
            ((AbstractVillagerAccessor) trader).betterLostItems$setOffers(offers);
        }

        ((LostTraderSession) trader).betterLostItems$setActiveTab(LostOfferKind.MARKET);
        trader.setTradingPlayer(player);
        trader.openTradingScreen(player, trader.getDisplayName(), 1);
        if (player.containerMenu instanceof MerchantMenu menu) {
            ServerPlayNetworking.send(player, new TraderTabStatePayload(
                    menu.containerId,
                    false,
                    0,
                    recoveryCount
            ));
        }
    }

    /**
     * Opens the custom container menu for per-player death-loot recovery.
     */
    private static void openRecoveryScreen(ServerPlayer player, WanderingTrader trader, int marketCount) {
        trader.setTradingPlayer(player);
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new LostItemsRecoveryMenu(containerId, inventory, player, trader.getId(), marketCount),
                Component.literal("Lost Item Recovery")
        ));
    }

    /**
     * Builds a vanilla {@link MerchantOffers} list from persisted market entries.
     */
    private static MerchantOffers buildMarketOffers(List<LostItemEntry> entries) {
        MerchantOffers offers = new MerchantOffers();
        for (LostItemEntry entry : entries) {
            offers.add(createMarketOffer(entry));
        }
        return offers;
    }

    /**
     * Creates one custom market trade and attaches storage metadata to it.
     */
    private static MerchantOffer createMarketOffer(LostItemEntry entry) {
        ItemStack result = entry.stack().copyWithCount(Math.min(entry.stack().getCount(), Math.max(1, entry.stack().getMaxStackSize())));
        LostItemPrice price = LostItemPrice.fromStack(result);
        MerchantOffer offer = new MerchantOffer(price.primaryCost(), price.secondaryCost(), result, 1, 0, 0.0F);
        ((LostOfferTracking) offer).betterLostItems$setOfferData(new LostOfferData(LostOfferKind.MARKET, null, entry.id()));
        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] createMarketOffer entryId={} stack={} priceValue={} primaryCost={} secondaryCost={}",
                entry.id(),
                LostItemsDebug.stack(result),
                price.emeraldValue(),
                LostItemsDebug.cost(price.primaryCost()),
                LostItemsDebug.optionalCost(price.secondaryCost())
        );
        return offer;
    }

    /**
     * Forces a merchant menu to re-evaluate its result after offers are replaced.
     */
    private static void resetMenu(MerchantMenu menu, MerchantOffers offers) {
        MerchantContainer tradeContainer = ((MerchantMenuAccessor) menu).betterLostItems$getTradeContainer();
        menu.setSelectionHint(0);
        menu.slotsChanged(tradeContainer);
        Better_lost_items.LOGGER.info("[BLI DEBUG] resetMenu containerId={} offers={} autoMoveItemsDisabled=true", menu.containerId, offers.size());
    }

    /**
     * Replaces the trader's active offer list and records which custom tab it represents.
     */
    private static void applyOffers(WanderingTrader trader, MerchantOffers offers, LostOfferKind activeTab) {
        ((AbstractVillagerAccessor) trader).betterLostItems$setOffers(offers);
        ((LostTraderSession) trader).betterLostItems$setActiveTab(activeTab);
    }

    /**
     * Returns the persisted market selection for a trader, repairing stale IDs when needed.
     */
    private static List<LostItemEntry> getLockedMarketEntries(WanderingTrader trader, LostItemsStorage storage) {
        LostTraderSession session = (LostTraderSession) trader;
        lockMarketSelectionIfNeeded(trader, storage);

        List<UUID> selectionIds = session.betterLostItems$getMarketSelectionIds();
        List<LostItemEntry> selectedEntries = storage.getUnclaimedItems(selectionIds);
        if (!selectedEntries.isEmpty() || selectionIds.isEmpty() || storage.getUnclaimedItems().isEmpty()) {
            return selectedEntries;
        }

        session.betterLostItems$setMarketSelectionIds(List.of());
        session.betterLostItems$setLockedMarketSelection(false);
        lockMarketSelectionIfNeeded(trader, storage);
        return storage.getUnclaimedItems(session.betterLostItems$getMarketSelectionIds());
    }

    /**
     * Ensures a trader spawned by vanilla has selected its Better Lost Items market entries.
     */
    public static void ensureMarketSelectionLocked(WanderingTrader trader, MinecraftServer server) {
        lockMarketSelectionIfNeeded(trader, LostItemsStorageManager.get(server));
    }

    /**
     * @return number of custom market offers currently available from a trader
     */
    public static int getMarketCount(WanderingTrader trader, MinecraftServer server) {
        return getLockedMarketEntries(trader, LostItemsStorageManager.get(server)).size();
    }

    /**
     * Computes the count shown on the recovery tab badge.
     */
    private static int getRecoveryTabCount(LostItemsStorage storage, UUID playerId) {
        return storage.getPlayerLostItems(playerId).size()
                + storage.getPlayerPurchasedItems(playerId).size()
                + (LostItemsConfig.isBurnedFetchEnabled() ? storage.getPlayerBurnedItems(playerId).size() : 0)
                + (LostItemsConfig.isVoidFetchEnabled() ? storage.getPlayerFallenItems(playerId).size() : 0)
                + (LostItemsConfig.isFetchEnabled() ? storage.getPlayerPendingFetchItems(playerId).size() : 0)
                + (LostItemsConfig.isFetchEnabled() ? storage.getTrackedDeathChunks(playerId).size() : 0);
    }

    /**
     * Resolves and validates a trader entity from a client payload.
     */
    private static WanderingTrader getAccessibleTrader(ServerPlayer player, int traderEntityId) {
        Entity entity = player.level().getEntity(traderEntityId);
        if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()) {
            return null;
        }

        if (player.distanceToSqr(trader) > TRADER_INTERACTION_RANGE_SQR) {
            return null;
        }

        return trader;
    }

    /**
     * Randomly chooses up to eight unique item stacks for one wandering trader.
     *
     * <p>The shuffle seed is derived from the trader UUID so the selection is stable if the method
     * is called more than once before entity save data is written.</p>
     */
    private static void lockMarketSelectionIfNeeded(WanderingTrader trader, LostItemsStorage storage) {
        LostTraderSession session = (LostTraderSession) trader;
        if (session.betterLostItems$hasLockedMarketSelection()) {
            return;
        }

        List<LostItemEntry> selectionPool = new ArrayList<>(storage.getUnclaimedItems());
        long seed = trader.getUUID().getMostSignificantBits() ^ trader.getUUID().getLeastSignificantBits();
        Collections.shuffle(selectionPool, new Random(seed));

        List<UUID> selectedIds = new ArrayList<>();
        List<ItemStack> selectedStacks = new ArrayList<>();
        for (LostItemEntry entry : selectionPool) {
            // Avoid duplicate visual offers even if storage has multiple compatible stacks.
            boolean duplicate = selectedStacks.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, entry.stack()));
            if (duplicate) {
                continue;
            }

            selectedIds.add(entry.id());
            selectedStacks.add(entry.stack().copy());
            if (selectedIds.size() >= MARKET_LIMIT) {
                break;
            }
        }

        session.betterLostItems$setMarketSelectionIds(selectedIds);
        session.betterLostItems$setLockedMarketSelection(true);
    }

    /**
     * Removes the configured recovery payment from the player's inventory.
     */
    private static boolean removeRecoveryPayment(ServerPlayer player, int paymentCount) {
        int totalPaymentItems = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (LostItemsConfig.isDeathLootPaymentItem(stack)) {
                totalPaymentItems += stack.getCount();
                if (totalPaymentItems >= paymentCount) {
                    break;
                }
            }
        }

        if (totalPaymentItems < paymentCount) {
            return false;
        }

        int remaining = paymentCount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!LostItemsConfig.isDeathLootPaymentItem(stack)) {
                continue;
            }

            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }

        player.getInventory().setChanged();
        return true;
    }

    /**
     * Returns any extra items left in the vanilla merchant payment slots after a custom trade.
     */
    private static void returnPaymentRemainders(ServerPlayer player, WanderingTrader trader) {
        if (!(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        if (!(((MerchantMenuAccessor) menu).betterLostItems$getTrader() == trader)) {
            return;
        }

        MerchantContainer tradeContainer = ((MerchantMenuAccessor) menu).betterLostItems$getTradeContainer();
        returnPaymentSlot(player, tradeContainer, 0);
        returnPaymentSlot(player, tradeContainer, 1);
        menu.slotsChanged(tradeContainer);
    }

    /**
     * Moves one merchant payment slot back into the player inventory.
     */
    private static void returnPaymentSlot(ServerPlayer player, MerchantContainer tradeContainer, int slot) {
        ItemStack remainder = tradeContainer.removeItemNoUpdate(slot);
        if (!remainder.isEmpty()) {
            player.getInventory().placeItemBackInInventory(remainder);
        }
    }
}
