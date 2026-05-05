package org.betterLostItems.better_lost_items;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server-authoritative container menu for the death-loot recovery tab.
 *
 * <p>The left grid is a read-only preview of items that are still lost. The center area accepts
 * payment/fetch supplies. The right grid behaves like an output inventory for already purchased
 * retrieved loot, including normal pickup and shift-click behavior.</p>
 *
 * <p>The menu uses {@link ContainerData} for small synced counters and scroll rows. Actual
 * item-stack contents are mirrored into small visible containers from the larger persistent lists,
 * which keeps the UI finite while the underlying storage can be effectively unbounded.</p>
 */
public class LostItemsRecoveryMenu extends AbstractContainerMenu {
    public static final int GRID_COLUMNS = 4;
    public static final int GRID_ROWS = 3;
    public static final int GRID_SLOT_COUNT = GRID_COLUMNS * GRID_ROWS;
    public static final int IMAGE_WIDTH = 276;
    public static final int IMAGE_HEIGHT = 166;
    public static final int LEFT_GRID_X = 108;
    public static final int RIGHT_GRID_X = 190;
    public static final int GRID_Y = 16;
    public static final int PAYMENT_SLOT_X = 21;
    public static final int PAYMENT_SLOT_Y = 34;
    public static final int FETCH_SUPPLY_SLOT_Y = 102;
    public static final int PLAYER_INV_X = 108;
    public static final int PLAYER_INV_Y = 84;
    public static final int REDEEM_BUTTON_ID = 0;
    public static final int FETCH_BUTTON_ID = 1;

    private static final int DATA_TRADER_ENTITY_ID = 0;
    private static final int DATA_MARKET_COUNT = 1;
    private static final int DATA_LOST_COUNT = 2;
    private static final int DATA_PURCHASED_COUNT = 3;
    private static final int DATA_LEFT_SCROLL = 4;
    private static final int DATA_RIGHT_SCROLL = 5;
    private static final int DATA_TRACKED_CHUNK_COUNT = 6;
    private static final int DATA_FETCH_ACTIVE = 7;
    private static final int DATA_BURNED_COUNT = 8;
    private static final int DATA_FALLEN_COUNT = 9;
    private static final int DATA_PENDING_FETCH_COUNT = 10;
    private static final int DATA_COUNT = 11;

    private static final int LEFT_SLOT_START = 0;
    private static final int LEFT_SLOT_END = LEFT_SLOT_START + GRID_SLOT_COUNT;
    private static final int PAYMENT_SLOT_INDEX = LEFT_SLOT_END;
    private static final int FETCH_FOOD_SLOT_INDEX = PAYMENT_SLOT_INDEX + 1;
    private static final int FETCH_FIRE_SLOT_INDEX = FETCH_FOOD_SLOT_INDEX + 1;
    private static final int FETCH_FALLEN_SLOT_INDEX = FETCH_FIRE_SLOT_INDEX + 1;
    private static final int FETCH_SLOT_START = FETCH_FOOD_SLOT_INDEX;
    private static final int FETCH_SLOT_END = FETCH_FALLEN_SLOT_INDEX + 1;
    private static final int CLAIM_SLOT_START = FETCH_SLOT_END;
    private static final int CLAIM_SLOT_END = CLAIM_SLOT_START + GRID_SLOT_COUNT;
    private static final int PLAYER_SLOT_START = CLAIM_SLOT_END;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 27;
    private static final int HOTBAR_SLOT_START = PLAYER_SLOT_END;
    private static final int HOTBAR_SLOT_END = HOTBAR_SLOT_START + 9;

    private static final double TRADER_INTERACTION_RANGE_SQR = 64.0D;

    private final Inventory playerInventory;
    private final SimpleContainer lostPreviewContainer = new SimpleContainer(GRID_SLOT_COUNT);
    private final SimpleContainer purchasedContainer = new SimpleContainer(GRID_SLOT_COUNT);
    private final SimpleContainer paymentContainer = new SimpleContainer(1);
    private final SimpleContainer fetchSupplyContainer = new SimpleContainer(3);
    private final ContainerData menuData = new SimpleContainerData(DATA_COUNT);
    private final ServerPlayer serverPlayer;

    private List<LostItemEntry> lostEntries = List.of();
    private List<LostItemEntry> purchasedEntries = List.of();

    /**
     * Client-side constructor used by the synced menu type.
     */
    public LostItemsRecoveryMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, null, 0, 0);
    }

    /**
     * Server-side constructor used when opening the recovery tab from a wandering trader.
     */
    public LostItemsRecoveryMenu(int containerId, Inventory playerInventory, ServerPlayer serverPlayer, int traderEntityId, int marketCount) {
        super(Better_lost_items.LOST_ITEMS_RECOVERY_MENU, containerId);
        this.playerInventory = playerInventory;
        this.serverPlayer = serverPlayer;

        this.menuData.set(DATA_TRADER_ENTITY_ID, traderEntityId);
        this.menuData.set(DATA_MARKET_COUNT, marketCount);

        this.addRecoverySlots();
        this.addPlayerInventorySlots(playerInventory, PLAYER_INV_X, PLAYER_INV_Y);
        this.addDataSlots(this.menuData);

        if (this.serverPlayer != null) {
            this.setTraderTradingPlayer(this.serverPlayer);
            this.refreshServerState();
        }
    }

    /**
     * @return entity ID of the trader this menu belongs to
     */
    public int getTraderEntityId() {
        return this.menuData.get(DATA_TRADER_ENTITY_ID);
    }

    /**
     * @return number of public market offers available on the other tab
     */
    public int getMarketCount() {
        return this.menuData.get(DATA_MARKET_COUNT);
    }

    /**
     * @return total hidden-list size behind the left preview grid
     */
    public int getLostCount() {
        return this.menuData.get(DATA_LOST_COUNT);
    }

    /**
     * @return total hidden-list size behind the right retrieved grid
     */
    public int getPurchasedCount() {
        return this.menuData.get(DATA_PURCHASED_COUNT);
    }

    /**
     * @return visible recovery item count, excluding fetch-only hidden buckets
     */
    public int getRecoveryCount() {
        return this.getLostCount() + this.getPurchasedCount();
    }

    /**
     * @return count displayed on the recovery tab, including hidden/fetchable state
     */
    public int getRecoveryTabCount() {
        return this.getRecoveryCount() + this.getBurnedCount() + this.getFallenCount() + this.getTrackedChunkCount() + this.getPendingFetchCount();
    }

    /**
     * @return configured flat payment amount for redeeming all lost loot
     */
    public int getRecoveryPrice() {
        return LostItemsTradeController.getRecoveryPrice();
    }

    /**
     * @return number of chunks that may contain unloaded death drops
     */
    public int getTrackedChunkCount() {
        return this.menuData.get(DATA_TRACKED_CHUNK_COUNT);
    }

    /**
     * @return whether a fetch scan or delayed return is active
     */
    public boolean isFetchActive() {
        return this.menuData.get(DATA_FETCH_ACTIVE) != 0;
    }

    /**
     * @return hidden burned-loot entry count
     */
    public int getBurnedCount() {
        return this.menuData.get(DATA_BURNED_COUNT);
    }

    /**
     * @return hidden void/fallen-loot entry count
     */
    public int getFallenCount() {
        return this.menuData.get(DATA_FALLEN_COUNT);
    }

    /**
     * @return delayed fetched-loot count waiting for the trader's return
     */
    public int getPendingFetchCount() {
        return this.menuData.get(DATA_PENDING_FETCH_COUNT);
    }

    /**
     * @return whether the fetch button should be enabled
     */
    public boolean canFetch() {
        return LostItemsConfig.isFetchEnabled()
                && !this.isFetchActive()
                && this.getPendingFetchCount() <= 0
                && this.hasJourneySupply()
                && (this.getTrackedChunkCount() > 0 || this.willFetchBurned() || this.willFetchFallen());
    }

    /**
     * @return current row offset for the left lost-loot grid
     */
    public int getLeftScrollRow() {
        return this.menuData.get(DATA_LEFT_SCROLL);
    }

    /**
     * @return current row offset for the right retrieved-loot grid
     */
    public int getRightScrollRow() {
        return this.menuData.get(DATA_RIGHT_SCROLL);
    }

    /**
     * @return maximum legal row offset for the left grid
     */
    public int getMaxLeftScrollRow() {
        return maxScrollRow(this.getLostCount());
    }

    /**
     * @return maximum legal row offset for the right grid
     */
    public int getMaxRightScrollRow() {
        return maxScrollRow(this.getPurchasedCount());
    }

    /**
     * @return whether the payment slot contains enough configured payment items
     */
    public boolean canRedeem() {
        ItemStack payment = this.paymentContainer.getItem(0);
        return this.getLostCount() > 0 && LostItemsConfig.isDeathLootPaymentItem(payment) && payment.getCount() >= this.getRecoveryPrice();
    }

    /**
     * @return current stack in the payment slot
     */
    public ItemStack getPaymentInputStack() {
        return this.paymentContainer.getItem(0);
    }

    /**
     * @return current stack in the journey supply slot
     */
    public ItemStack getJourneyInputStack() {
        return this.fetchSupplyContainer.getItem(0);
    }

    /**
     * @return current stack in the burned-loot supply slot
     */
    public ItemStack getBurnedInputStack() {
        return this.fetchSupplyContainer.getItem(1);
    }

    /**
     * @return current stack in the void/fallen-loot supply slot
     */
    public ItemStack getFallenInputStack() {
        return this.fetchSupplyContainer.getItem(2);
    }

    /**
     * Updates grid scroll positions received from the client.
     */
    public void setScrollRows(int leftScrollRow, int rightScrollRow) {
        if (this.serverPlayer == null) {
            return;
        }

        this.menuData.set(DATA_LEFT_SCROLL, leftScrollRow);
        this.menuData.set(DATA_RIGHT_SCROLL, rightScrollRow);
        this.refreshServerState();
    }

    /**
     * Handles custom GUI buttons sent through vanilla inventory button packets.
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (this.serverPlayer == null || player != this.serverPlayer) {
            return false;
        }

        if (buttonId == REDEEM_BUTTON_ID) {
            if (!this.canRedeem()) {
                return false;
            }

            this.paymentContainer.removeItem(0, this.getRecoveryPrice());
            LostItemsStorageManager.get(this.serverPlayer.level().getServer()).movePlayerLostItemsToPurchased(this.serverPlayer.getUUID());
            this.refreshServerState();
            return true;
        }

        if (buttonId == FETCH_BUTTON_ID) {
            if (!this.canFetch()) {
                return false;
            }

            boolean fetchBurned = this.willFetchBurned();
            boolean fetchFallen = this.willFetchFallen();
            boolean started = LostItemsTraderJourneyManager.startFetchJourney(this.serverPlayer, this.getTraderEntityId(), fetchBurned, fetchFallen);
            if (!started) {
                this.refreshServerState();
                return false;
            }

            this.fetchSupplyContainer.removeItem(FETCH_FOOD_SLOT_INDEX - FETCH_SLOT_START, LostItemsConfig.journeySupplyAmount());
            if (fetchBurned) {
                this.fetchSupplyContainer.removeItem(FETCH_FIRE_SLOT_INDEX - FETCH_SLOT_START, LostItemsConfig.burnedFetchAmount());
            }
            if (fetchFallen) {
                this.fetchSupplyContainer.removeItem(FETCH_FALLEN_SLOT_INDEX - FETCH_SLOT_START, LostItemsConfig.voidFetchAmount());
            }

            this.refreshServerState();
            return true;
        }

        return false;
    }

    /**
     * Implements shift-click transfers for payment, fetch supplies, player inventory, and output.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack original = slotStack.copy();

        if (slotIndex >= LEFT_SLOT_START && slotIndex < LEFT_SLOT_END) {
            return ItemStack.EMPTY;
        }

        if (slotIndex == PAYMENT_SLOT_INDEX || (slotIndex >= FETCH_SLOT_START && slotIndex < FETCH_SLOT_END)) {
            if (!this.moveItemStackTo(slotStack, PLAYER_SLOT_START, HOTBAR_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
            this.finishQuickMove(slot, slotStack, original, player);
            return original;
        }

        if (slotIndex >= CLAIM_SLOT_START && slotIndex < CLAIM_SLOT_END) {
            if (!this.quickMovePurchasedStackToInventory(slotIndex - CLAIM_SLOT_START)) {
                return ItemStack.EMPTY;
            }
            return original;
        }

        if (slotIndex >= PLAYER_SLOT_START && slotIndex < HOTBAR_SLOT_END) {
            if (LostItemsConfig.isDeathLootPaymentItem(slotStack) && this.moveItemStackTo(slotStack, PAYMENT_SLOT_INDEX, PAYMENT_SLOT_INDEX + 1, false)) {
                this.finishQuickMove(slot, slotStack, original, player);
                return original;
            }

            if (LostItemsFetchSupplies.isJourneySupply(slotStack) && this.moveItemStackTo(slotStack, FETCH_FOOD_SLOT_INDEX, FETCH_FOOD_SLOT_INDEX + 1, false)) {
                this.finishQuickMove(slot, slotStack, original, player);
                return original;
            }

            if (LostItemsFetchSupplies.isBurnedFetchSupply(slotStack) && this.moveItemStackTo(slotStack, FETCH_FIRE_SLOT_INDEX, FETCH_FIRE_SLOT_INDEX + 1, false)) {
                this.finishQuickMove(slot, slotStack, original, player);
                return original;
            }

            if (LostItemsFetchSupplies.isVoidFetchSupply(slotStack) && this.moveItemStackTo(slotStack, FETCH_FALLEN_SLOT_INDEX, FETCH_FALLEN_SLOT_INDEX + 1, false)) {
                this.finishQuickMove(slot, slotStack, original, player);
                return original;
            }

            if (slotIndex < PLAYER_SLOT_END) {
                if (!this.moveItemStackTo(slotStack, HOTBAR_SLOT_START, HOTBAR_SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(slotStack, PLAYER_SLOT_START, PLAYER_SLOT_END, false)) {
                return ItemStack.EMPTY;
            }

            this.finishQuickMove(slot, slotStack, original, player);
            return original;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Returns payment/fetch input stacks when the menu closes and releases the trader focus.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            this.clearTraderTradingPlayer(serverPlayer);
        }

        ItemStack payment = this.paymentContainer.removeItemNoUpdate(0);
        if (!payment.isEmpty()) {
            player.getInventory().placeItemBackInInventory(payment);
        }

        for (int slot = 0; slot < this.fetchSupplyContainer.getContainerSize(); slot++) {
            ItemStack supply = this.fetchSupplyContainer.removeItemNoUpdate(slot);
            if (!supply.isEmpty()) {
                player.getInventory().placeItemBackInInventory(supply);
            }
        }
    }

    /**
     * Keeps the menu valid only while the trader is alive and nearby.
     *
     * <p>Calling {@code setTradingPlayer} here keeps the wandering trader focused while the custom
     * tab is open, matching vanilla trader behavior.</p>
     */
    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) {
            return true;
        }

        Entity entity = player.level().getEntity(this.getTraderEntityId());
        if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()) {
            return false;
        }

        if (player.distanceToSqr(trader) > TRADER_INTERACTION_RANGE_SQR) {
            return false;
        }

        trader.setTradingPlayer(player);
        return true;
    }

    /**
     * Adds all custom slots before the normal player inventory slots.
     */
    private void addRecoverySlots() {
        for (int slot = 0; slot < GRID_SLOT_COUNT; slot++) {
            int x = LEFT_GRID_X + (slot % GRID_COLUMNS) * 18;
            int y = GRID_Y + (slot / GRID_COLUMNS) * 18;
            this.addSlot(new PreviewSlot(this.lostPreviewContainer, slot, x, y));
        }

        this.addSlot(new PaymentSlot(this.paymentContainer, 0, PAYMENT_SLOT_X, PAYMENT_SLOT_Y));
        this.addSlot(new JourneySupplySlot(this.fetchSupplyContainer, 0, LostItemsConfig.journeySlotX(), FETCH_SUPPLY_SLOT_Y));
        this.addSlot(new BurnedFetchSlot(this.fetchSupplyContainer, 1, LostItemsConfig.burnedSlotX(), FETCH_SUPPLY_SLOT_Y));
        this.addSlot(new VoidFetchSlot(this.fetchSupplyContainer, 2, LostItemsConfig.voidSlotX(), FETCH_SUPPLY_SLOT_Y));

        for (int slot = 0; slot < GRID_SLOT_COUNT; slot++) {
            int x = RIGHT_GRID_X + (slot % GRID_COLUMNS) * 18;
            int y = GRID_Y + (slot / GRID_COLUMNS) * 18;
            this.addSlot(new PurchasedSlot(this.purchasedContainer, slot, x, y));
        }
    }

    /**
     * Adds the standard 3-row inventory plus hotbar layout used by vanilla menus in 1.21.1.
     */
    private void addPlayerInventorySlots(Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, x + column * 18, y + 58));
        }
    }

    /**
     * Shared cleanup after moving a stack through {@link #quickMoveStack(Player, int)}.
     */
    private void finishQuickMove(Slot slot, ItemStack slotStack, ItemStack original, Player player) {
        if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() != original.getCount()) {
            slot.onTake(player, slotStack);
        }
    }

    /**
     * Moves a visible retrieved stack directly into the player's inventory.
     */
    private boolean quickMovePurchasedStackToInventory(int visibleSlotIndex) {
        ItemStack visibleStack = this.purchasedContainer.getItem(visibleSlotIndex);
        if (visibleStack.isEmpty() || this.serverPlayer == null) {
            return false;
        }

        ItemStack remaining = visibleStack.copy();
        if (!this.moveItemStackTo(remaining, PLAYER_SLOT_START, HOTBAR_SLOT_END, true)) {
            return false;
        }

        int movedAmount = visibleStack.getCount() - remaining.getCount();
        if (movedAmount <= 0) {
            return false;
        }

        this.removePurchasedAmount(visibleSlotIndex, movedAmount);
        return true;
    }

    /**
     * Removes a clicked stack from persistent retrieved-loot storage.
     */
    private ItemStack removePurchasedStack(int visibleSlotIndex, int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        if (this.serverPlayer == null) {
            return this.purchasedContainer.removeItem(visibleSlotIndex, amount);
        }

        int purchasedIndex = this.getRightScrollRow() * GRID_COLUMNS + visibleSlotIndex;
        if (purchasedIndex < 0 || purchasedIndex >= this.purchasedEntries.size()) {
            return ItemStack.EMPTY;
        }

        LostItemEntry entry = this.purchasedEntries.get(purchasedIndex);
        ItemStack source = entry.stack().copy();
        ItemStack removed = source.split(Math.min(amount, source.getCount()));
        if (removed.isEmpty()) {
            return ItemStack.EMPTY;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(this.serverPlayer.level().getServer());
        if (source.isEmpty()) {
            storage.removePlayerPurchasedItem(this.serverPlayer.getUUID(), entry.id());
        } else {
            storage.replacePlayerPurchasedItem(this.serverPlayer.getUUID(), entry.id(), source);
        }

        this.refreshServerState();
        return removed;
    }

    /**
     * Removes a partial amount from retrieved-loot storage after shift-click insertion.
     */
    private void removePurchasedAmount(int visibleSlotIndex, int amount) {
        if (this.serverPlayer == null || amount <= 0) {
            return;
        }

        int purchasedIndex = this.getRightScrollRow() * GRID_COLUMNS + visibleSlotIndex;
        if (purchasedIndex < 0 || purchasedIndex >= this.purchasedEntries.size()) {
            return;
        }

        LostItemEntry entry = this.purchasedEntries.get(purchasedIndex);
        ItemStack remaining = entry.stack().copy();
        remaining.shrink(amount);

        LostItemsStorage storage = LostItemsStorageManager.get(this.serverPlayer.level().getServer());
        if (remaining.isEmpty()) {
            storage.removePlayerPurchasedItem(this.serverPlayer.getUUID(), entry.id());
        } else {
            storage.replacePlayerPurchasedItem(this.serverPlayer.getUUID(), entry.id(), remaining);
        }

        this.refreshServerState();
    }

    /**
     * Pulls fresh storage state into the visible containers and synced data slots.
     */
    private void refreshServerState() {
        if (this.serverPlayer == null) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(this.serverPlayer.level().getServer());
        this.lostEntries = storage.getPlayerLostItems(this.serverPlayer.getUUID());
        this.purchasedEntries = storage.getPlayerPurchasedItems(this.serverPlayer.getUUID());

        int clampedLeftScroll = Mth.clamp(this.getLeftScrollRow(), 0, maxScrollRow(this.lostEntries.size()));
        int clampedRightScroll = Mth.clamp(this.getRightScrollRow(), 0, maxScrollRow(this.purchasedEntries.size()));
        this.menuData.set(DATA_LEFT_SCROLL, clampedLeftScroll);
        this.menuData.set(DATA_RIGHT_SCROLL, clampedRightScroll);
        this.menuData.set(DATA_LOST_COUNT, this.lostEntries.size());
        this.menuData.set(DATA_PURCHASED_COUNT, this.purchasedEntries.size());
        this.menuData.set(DATA_MARKET_COUNT, this.resolveMarketCount());
        this.menuData.set(DATA_TRACKED_CHUNK_COUNT, LostItemsConfig.isFetchEnabled() ? storage.getTrackedDeathChunks(this.serverPlayer.getUUID()).size() : 0);
        this.menuData.set(DATA_FETCH_ACTIVE, LostItemsConfig.isFetchEnabled() && LostItemsTraderJourneyManager.isFetchJourneyActive(this.serverPlayer.level().getServer(), this.serverPlayer.getUUID()) ? 1 : 0);
        this.menuData.set(DATA_BURNED_COUNT, LostItemsConfig.isBurnedFetchEnabled() ? storage.getPlayerBurnedItems(this.serverPlayer.getUUID()).size() : 0);
        this.menuData.set(DATA_FALLEN_COUNT, LostItemsConfig.isVoidFetchEnabled() ? storage.getPlayerFallenItems(this.serverPlayer.getUUID()).size() : 0);
        this.menuData.set(DATA_PENDING_FETCH_COUNT, LostItemsConfig.isFetchEnabled() ? storage.getPlayerPendingFetchItems(this.serverPlayer.getUUID()).size() : 0);

        this.populateVisibleContainer(this.lostPreviewContainer, this.lostEntries, clampedLeftScroll);
        this.populateVisibleContainer(this.purchasedContainer, this.purchasedEntries, clampedRightScroll);
        this.broadcastChanges();
    }

    /**
     * Recomputes public market count from the backing trader entity.
     */
    private int resolveMarketCount() {
        Entity entity = this.serverPlayer.level().getEntity(this.getTraderEntityId());
        if (entity instanceof WanderingTrader trader && trader.isAlive()) {
            return LostItemsTradeController.getMarketCount(trader, this.serverPlayer.level().getServer());
        }

        return 0;
    }

    /**
     * External refresh hook used by background fetch jobs.
     */
    public void refreshFromServer() {
        this.refreshServerState();
    }

    /**
     * Marks the trader as interacting with this player while the custom tab is open.
     */
    private void setTraderTradingPlayer(ServerPlayer player) {
        Entity entity = player.level().getEntity(this.getTraderEntityId());
        if (entity instanceof WanderingTrader trader) {
            trader.setTradingPlayer(player);
        }
    }

    /**
     * Clears trader focus only when the player is not immediately switching tabs.
     */
    private void clearTraderTradingPlayer(ServerPlayer player) {
        Entity entity = player.level().getEntity(this.getTraderEntityId());
        if (entity instanceof WanderingTrader trader
                && trader.getTradingPlayer() == player
                && !LostItemsTradeController.isTransitioningToTraderMarket(player)
                && !this.isPlayerStillUsingTrader(player)) {
            trader.setTradingPlayer(null);
        }
    }

    /**
     * Checks whether another open menu still belongs to the same trader.
     */
    private boolean isPlayerStillUsingTrader(ServerPlayer player) {
        if (player.containerMenu == this) {
            return false;
        }

        if (player.containerMenu instanceof LostItemsRecoveryMenu recoveryMenu) {
            return recoveryMenu.getTraderEntityId() == this.getTraderEntityId();
        }

        if (player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu merchantMenu) {
            Entity entity = player.level().getEntity(this.getTraderEntityId());
            return entity instanceof WanderingTrader trader && ((org.betterLostItems.better_lost_items.mixin.MerchantMenuAccessor) merchantMenu).betterLostItems$getTrader() == trader;
        }

        return false;
    }

    /**
     * Mirrors one scrolled page from persistent storage into a fixed-size visible container.
     */
    private void populateVisibleContainer(SimpleContainer container, List<LostItemEntry> entries, int scrollRow) {
        int startIndex = scrollRow * GRID_COLUMNS;
        for (int slot = 0; slot < GRID_SLOT_COUNT; slot++) {
            int entryIndex = startIndex + slot;
            ItemStack stack = entryIndex < entries.size() ? entries.get(entryIndex).stack().copy() : ItemStack.EMPTY;
            container.setItem(slot, stack);
        }
    }

    /**
     * @return maximum scroll row for an entry count and fixed 4x3 grid
     */
    private static int maxScrollRow(int entryCount) {
        return Math.max(0, Mth.ceil(entryCount / (float) GRID_COLUMNS) - GRID_ROWS);
    }

    /**
     * @return whether the journey slot has enough configured supplies
     */
    private boolean hasJourneySupply() {
        ItemStack food = this.fetchSupplyContainer.getItem(0);
        return LostItemsFetchSupplies.isJourneySupply(food) && food.getCount() >= LostItemsConfig.journeySupplyAmount();
    }

    /**
     * @return whether the current fetch should include hidden burned loot
     */
    private boolean willFetchBurned() {
        ItemStack burnedSupply = this.fetchSupplyContainer.getItem(1);
        return this.getBurnedCount() > 0
                && LostItemsFetchSupplies.isBurnedFetchSupply(burnedSupply)
                && burnedSupply.getCount() >= LostItemsConfig.burnedFetchAmount();
    }

    /**
     * @return whether the current fetch should include hidden void/fallen loot
     */
    private boolean willFetchFallen() {
        ItemStack pearls = this.fetchSupplyContainer.getItem(2);
        return this.getFallenCount() > 0
                && LostItemsFetchSupplies.isVoidFetchSupply(pearls)
                && pearls.getCount() >= LostItemsConfig.voidFetchAmount();
    }

    /**
     * Read-only slot used for the left lost-loot preview grid.
     */
    private static class PreviewSlot extends Slot {
        private PreviewSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * Output slot backed by persistent retrieved-loot storage instead of a normal inventory.
     */
    private class PurchasedSlot extends Slot {
        private PurchasedSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.hasItem();
        }

        @Override
        public boolean allowModification(Player player) {
            return true;
        }

        @Override
        public ItemStack remove(int amount) {
            return LostItemsRecoveryMenu.this.removePurchasedStack(this.getContainerSlot(), amount);
        }
    }

    /**
     * Input slot for the configured death-loot payment item.
     */
    private static class PaymentSlot extends Slot {
        private PaymentSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return LostItemsConfig.isDeathLootPaymentItem(stack);
        }
    }

    /**
     * Input slot for the fetch journey supply.
     */
    private static class JourneySupplySlot extends Slot {
        private JourneySupplySlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return LostItemsFetchSupplies.isJourneySupply(stack);
        }

        @Override
        public boolean isActive() {
            return LostItemsConfig.isFetchEnabled();
        }
    }

    /**
     * Input slot for optional burned-loot fetch supplies.
     */
    private static class BurnedFetchSlot extends Slot {
        private BurnedFetchSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return LostItemsFetchSupplies.isBurnedFetchSupply(stack);
        }

        @Override
        public boolean isActive() {
            return LostItemsConfig.isBurnedFetchEnabled();
        }
    }

    /**
     * Input slot for optional void/fallen-loot fetch supplies.
     */
    private static class VoidFetchSlot extends Slot {
        private VoidFetchSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return LostItemsFetchSupplies.isVoidFetchSupply(stack);
        }

        @Override
        public boolean isActive() {
            return LostItemsConfig.isVoidFetchEnabled();
        }
    }
}
