package org.betterLostItems.better_lost_items.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.LostItemsConfig;
import org.betterLostItems.better_lost_items.LostItemsRecoveryMenu;
import org.betterLostItems.better_lost_items.OpenTraderMarketPayload;
import org.betterLostItems.better_lost_items.RecoveryScrollPayload;

/**
 * Client renderer/controller for the death-loot recovery container menu.
 *
 * <p>The screen draws the custom 276x166 UI cropped from 338x228 texture files, renders ghost
 * items in empty input slots, shows fetch requirement badges/tooltips, and sends scroll updates
 * back to the server so the authoritative menu can page through unbounded item lists.</p>
 */
public final class LostItemsRecoveryMenuScreen extends AbstractContainerScreen<LostItemsRecoveryMenu> {
    private static final int SLOT_SIZE = 18;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLER_HEIGHT = 27;
    private static final int REDEEM_BUTTON_X = LostItemsRecoveryMenu.PAYMENT_SLOT_X + SLOT_SIZE + 5;
    private static final int REDEEM_BUTTON_Y = LostItemsRecoveryMenu.PAYMENT_SLOT_Y - 3;
    private static final int FETCH_BUTTON_X = 27;
    private static final int FETCH_BUTTON_Y = 132;
    private static final int FETCH_BUTTON_WIDTH = 56;
    private static final int FETCH_BUTTON_HEIGHT = 20;
    private static final int COST_LABEL_X = 29;
    private static final int COST_LABEL_Y = 18;
    private static final int COST_VALUE_Y = 56;
    private static final int INVENTORY_LABEL_Y_OFFSET = 12;
    private static final int SECTION_LABEL_Y = 6;
    private static final float GHOST_ITEM_ALPHA = 0.38F;
    private static final int BACKGROUND_TEXTURE_WIDTH = 338;
    private static final int BACKGROUND_TEXTURE_HEIGHT = 228;
    private static final int FETCH_STATUS_SIZE = 4;
    private static final int FETCH_STATUS_Y_OFFSET = 7;
    private static final int FETCH_STATUS_HOVER_PADDING = 3;
    private static final ResourceLocation COMPLETE_TEXTURE = Better_lost_items.id("textures/gui/complete.png");
    private static final ResourceLocation INCOMPLETE_TEXTURE = Better_lost_items.id("textures/gui/incomplete.png");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller");
    private static final ResourceLocation SCROLLER_DISABLED_SPRITE = ResourceLocation.withDefaultNamespace("container/villager/scroller_disabled");

    private LostItemsTabButton marketTab;
    private LostItemsTabButton recoveryTab;
    private LostItemsRedeemButton redeemButton;
    private Button fetchButton;
    private DraggingScrollbar activeScrollbar = DraggingScrollbar.NONE;

    /**
     * Creates the recovery screen for a synced recovery menu.
     */
    public LostItemsRecoveryMenuScreen(LostItemsRecoveryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = LostItemsRecoveryMenu.IMAGE_WIDTH;
        this.imageHeight = LostItemsRecoveryMenu.IMAGE_HEIGHT;
        this.titleLabelX = 0;
        this.titleLabelY = -1000;
        this.inventoryLabelX = LostItemsRecoveryMenu.PLAYER_INV_X - 1;
        this.inventoryLabelY = LostItemsRecoveryMenu.PLAYER_INV_Y - INVENTORY_LABEL_Y_OFFSET;
    }

    /**
     * Builds tab widgets, custom buttons, and restores cursor position after tab switches.
     */
    @Override
    protected void init() {
        super.init();

        this.marketTab = this.addRenderableWidget(new LostItemsTabButton(
                this.leftPos + 10,
                this.topPos - 28,
                true,
                new ItemStack(Items.EMERALD),
                Component.literal("Market"),
                Component.literal("Market"),
                false,
                button -> {
                    if (this.menu.getTraderEntityId() != 0) {
                        PacketDistributor.sendToServer(new OpenTraderMarketPayload(this.menu.getTraderEntityId()));
                    }
                }
        ));

        this.recoveryTab = this.addRenderableWidget(new LostItemsTabButton(
                this.leftPos + 38,
                this.topPos - 28,
                false,
                new ItemStack(Items.CHEST),
                Component.literal("Recovery"),
                Component.literal("Recovery"),
                true,
                button -> {
                }
        ));
        this.recoveryTab.active = false;

        this.redeemButton = this.addRenderableWidget(new LostItemsRedeemButton(
                this.leftPos + REDEEM_BUTTON_X,
                this.topPos + REDEEM_BUTTON_Y,
                Component.literal("Redeem all lost items"),
                button -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, LostItemsRecoveryMenu.REDEEM_BUTTON_ID);
                    }
                }
        ));

        this.fetchButton = this.addRenderableWidget(Button.builder(Component.literal("Fetch"), button -> {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, LostItemsRecoveryMenu.FETCH_BUTTON_ID);
                    }
                })
                .bounds(this.leftPos + FETCH_BUTTON_X, this.topPos + FETCH_BUTTON_Y, FETCH_BUTTON_WIDTH, FETCH_BUTTON_HEIGHT)
                .build());

        this.updateWidgetState();
        LostItemsClientState.restorePendingMouse();
    }

    /**
     * Keeps button enabled/visible state synchronized with the server menu.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        this.updateWidgetState();
    }

    /**
     * Draws the background texture, ghost slot items, fetch badges, and scroll bars.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY) {
        graphics.blit(
                this.backgroundTexture(),
                this.leftPos,
                this.topPos,
                0.0F,
                0.0F,
                this.imageWidth,
                this.imageHeight,
                BACKGROUND_TEXTURE_WIDTH,
                BACKGROUND_TEXTURE_HEIGHT
        );

        this.drawGhostItems(graphics);
        this.drawFetchStatusBadges(graphics);

        int scrollBarY = this.topPos + LostItemsRecoveryMenu.GRID_Y;
        this.drawScrollBar(
                graphics,
                this.leftScrollBarX(),
                scrollBarY,
                this.menu.getLeftScrollRow(),
                this.menu.getMaxLeftScrollRow(),
                this.isHoveringScrollbar(this.leftScrollBarX(), scrollBarY, mouseX, mouseY),
                this.activeScrollbar == DraggingScrollbar.LEFT
        );
        this.drawScrollBar(
                graphics,
                this.rightScrollBarX(),
                scrollBarY,
                this.menu.getRightScrollRow(),
                this.menu.getMaxRightScrollRow(),
                this.isHoveringScrollbar(this.rightScrollBarX(), scrollBarY, mouseX, mouseY),
                this.activeScrollbar == DraggingScrollbar.RIGHT
        );
    }

    /**
     * Draws section labels using the vanilla inventory label style.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        this.drawCenteredLabel(graphics, Component.literal("Lost Loot"), LostItemsRecoveryMenu.LEFT_GRID_X + ((LostItemsRecoveryMenu.GRID_COLUMNS * SLOT_SIZE) / 2), SECTION_LABEL_Y);
        this.drawCenteredLabel(graphics, Component.literal("Retrieved Loot"), LostItemsRecoveryMenu.RIGHT_GRID_X + ((LostItemsRecoveryMenu.GRID_COLUMNS * SLOT_SIZE) / 2), SECTION_LABEL_Y);
        this.drawCenteredLabel(graphics, Component.literal("Costs:"), COST_LABEL_X, COST_LABEL_Y);
        this.drawCenteredLabel(graphics, Component.literal(String.valueOf(this.menu.getRecoveryPrice())), COST_LABEL_X, COST_VALUE_Y);
    }

    /**
     * Scrolls whichever item grid the mouse is currently over.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.insideGrid(mouseX, mouseY, this.leftPos + LostItemsRecoveryMenu.LEFT_GRID_X, this.topPos + LostItemsRecoveryMenu.GRID_Y)) {
            return this.sendScroll(this.nextScrollRow(this.menu.getLeftScrollRow(), this.menu.getMaxLeftScrollRow(), verticalAmount), this.menu.getRightScrollRow());
        }

        if (this.insideGrid(mouseX, mouseY, this.leftPos + LostItemsRecoveryMenu.RIGHT_GRID_X, this.topPos + LostItemsRecoveryMenu.GRID_Y)) {
            return this.sendScroll(this.menu.getLeftScrollRow(), this.nextScrollRow(this.menu.getRightScrollRow(), this.menu.getMaxRightScrollRow(), verticalAmount));
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Adds fetch-badge tooltips after vanilla item/tooltips have had a chance to render.
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        this.setFetchBadgeTooltip(graphics, mouseX, mouseY);
        this.setFetchButtonTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Starts scrollbar dragging when the user clicks a visible scrollbar.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int scrollBarY = this.topPos + LostItemsRecoveryMenu.GRID_Y;
        if (this.tryStartScrollbarDrag(DraggingScrollbar.LEFT, this.leftScrollBarX(), scrollBarY, mouseX, mouseY)) {
            return true;
        }

        if (this.tryStartScrollbarDrag(DraggingScrollbar.RIGHT, this.rightScrollBarX(), scrollBarY, mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Converts mouse drag motion into synced grid scroll rows.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.activeScrollbar == DraggingScrollbar.LEFT) {
            this.dragScrollbar(DraggingScrollbar.LEFT, mouseY);
            return true;
        }

        if (this.activeScrollbar == DraggingScrollbar.RIGHT) {
            this.dragScrollbar(DraggingScrollbar.RIGHT, mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * Ends scrollbar dragging.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.activeScrollbar = DraggingScrollbar.NONE;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Applies current menu counts and permissions to widgets.
     */
    private void updateWidgetState() {
        if (this.marketTab != null) {
            this.marketTab.setSelected(false);
            this.marketTab.active = this.menu.getTraderEntityId() != 0;
            this.marketTab.setTooltipMessage(Component.literal("Market (" + this.menu.getMarketCount() + ")"));
        }

        if (this.recoveryTab != null) {
            this.recoveryTab.setSelected(true);
            this.recoveryTab.setTooltipMessage(Component.literal("Recovery (" + this.menu.getRecoveryTabCount() + ")"));
        }

        if (this.redeemButton != null) {
            this.redeemButton.active = this.menu.canRedeem();
        }

        if (this.fetchButton != null) {
            this.fetchButton.visible = LostItemsConfig.isFetchEnabled();
            this.fetchButton.active = LostItemsConfig.isFetchEnabled() && this.menu.canFetch();
            this.fetchButton.setMessage(this.fetchButtonMessage());
        }
    }

    /**
     * @return current Fetch button label, including disabled reasons that fit the button.
     */
    private Component fetchButtonMessage() {
        if (this.menu.isFetchActive()) {
            return Component.literal("Fetching");
        }

        if (!this.hasFetchableLoot()) {
            return Component.literal("No Fetch");
        }

        return Component.literal("Fetch");
    }

    /**
     * Sends a scroll update only when something actually changed.
     */
    private boolean sendScroll(int leftScrollRow, int rightScrollRow) {
        if (leftScrollRow == this.menu.getLeftScrollRow() && rightScrollRow == this.menu.getRightScrollRow()) {
            return false;
        }

        PacketDistributor.sendToServer(new RecoveryScrollPayload(this.menu.containerId, leftScrollRow, rightScrollRow));
        return true;
    }

    /**
     * Converts wheel direction into a clamped row index.
     */
    private int nextScrollRow(int currentScrollRow, int maxScrollRow, double verticalAmount) {
        int direction = verticalAmount > 0.0D ? -1 : 1;
        return Math.max(0, Math.min(maxScrollRow, currentScrollRow + direction));
    }

    /**
     * @return whether a screen coordinate is inside a fixed 4x3 grid
     */
    private boolean insideGrid(double mouseX, double mouseY, int gridX, int gridY) {
        return mouseX >= gridX
                && mouseX < gridX + (LostItemsRecoveryMenu.GRID_COLUMNS * SLOT_SIZE)
                && mouseY >= gridY
                && mouseY < gridY + (LostItemsRecoveryMenu.GRID_ROWS * SLOT_SIZE);
    }

    /**
     * @return screen X coordinate for the left scrollbar, one pixel past the grid
     */
    private int leftScrollBarX() {
        return this.leftPos + LostItemsRecoveryMenu.LEFT_GRID_X + (LostItemsRecoveryMenu.GRID_COLUMNS * SLOT_SIZE);
    }

    /**
     * @return screen X coordinate for the right scrollbar, one pixel past the grid
     */
    private int rightScrollBarX() {
        return this.leftPos + LostItemsRecoveryMenu.RIGHT_GRID_X + (LostItemsRecoveryMenu.GRID_COLUMNS * SLOT_SIZE);
    }

    /**
     * Starts dragging a scrollbar when it is active and under the cursor.
     */
    private boolean tryStartScrollbarDrag(DraggingScrollbar scrollbar, int x, int y, double mouseX, double mouseY) {
        if (!this.canDragScrollbar(scrollbar) || !this.isHoveringScrollbar(x, y, mouseX, mouseY)) {
            return false;
        }

        this.activeScrollbar = scrollbar;
        this.dragScrollbar(scrollbar, mouseY);
        return true;
    }

    /**
     * Calculates and sends the row selected by a scrollbar drag.
     */
    private boolean dragScrollbar(DraggingScrollbar scrollbar, double mouseY) {
        int maxScrollRow = this.maxScrollRow(scrollbar);
        if (maxScrollRow <= 0) {
            return false;
        }

        int scrollBarY = this.topPos + LostItemsRecoveryMenu.GRID_Y;
        int travelHeight = this.scrollbarTravelHeight();
        float relative = ((float) mouseY - scrollBarY - (SCROLLER_HEIGHT / 2.0F)) / travelHeight;
        int targetScroll = Mth.clamp(Math.round(relative * maxScrollRow), 0, maxScrollRow);
        if (scrollbar == DraggingScrollbar.LEFT) {
            return this.sendScroll(targetScroll, this.menu.getRightScrollRow());
        }

        return this.sendScroll(this.menu.getLeftScrollRow(), targetScroll);
    }

    /**
     * @return maximum row for one of the two grids
     */
    private int maxScrollRow(DraggingScrollbar scrollbar) {
        return scrollbar == DraggingScrollbar.LEFT ? this.menu.getMaxLeftScrollRow() : this.menu.getMaxRightScrollRow();
    }

    /**
     * @return whether the scrollbar has enough hidden rows to be draggable
     */
    private boolean canDragScrollbar(DraggingScrollbar scrollbar) {
        return this.maxScrollRow(scrollbar) > 0;
    }

    /**
     * @return vertical range available to the villager-style scrollbar thumb
     */
    private int scrollbarTravelHeight() {
        return (LostItemsRecoveryMenu.GRID_ROWS * SLOT_SIZE) - SCROLLER_HEIGHT;
    }

    /**
     * @return whether the mouse is over the scrollbar track or thumb area
     */
    private boolean isHoveringScrollbar(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x
                && mouseX < x + SCROLLBAR_WIDTH
                && mouseY >= y
                && mouseY < y + (LostItemsRecoveryMenu.GRID_ROWS * SLOT_SIZE);
    }

    /**
     * @return top Y coordinate for the scrollbar thumb at the given row
     */
    private int thumbY(int y, int scrollRow, int maxScrollRow) {
        if (maxScrollRow <= 0) {
            return y;
        }

        return y + Math.round((this.scrollbarTravelHeight() * scrollRow) / (float) maxScrollRow);
    }

    /**
     * Draws centered, no-shadow vanilla-gray UI text.
     */
    private void drawCenteredLabel(GuiGraphics graphics, Component text, int centerX, int y) {
        int textX = centerX - (this.font.width(text.getVisualOrderText()) / 2);
        graphics.drawString(this.font, text, textX, y, 0xFF404040, false);
    }

    /**
     * @return active background texture based on configured fetch features
     */
    private ResourceLocation backgroundTexture() {
        return Better_lost_items.id(LostItemsConfig.fetchLayout().texturePath());
    }

    /**
     * Draws transparent item hints in empty input slots.
     */
    private void drawGhostItems(GuiGraphics graphics) {
        this.drawGhostItem(
                graphics,
                LostItemsConfig.deathLootPaymentStack(),
                LostItemsRecoveryMenu.PAYMENT_SLOT_X,
                LostItemsRecoveryMenu.PAYMENT_SLOT_Y,
                this.menu.getPaymentInputStack().isEmpty()
        );

        if (!LostItemsConfig.isFetchEnabled()) {
            return;
        }

        this.drawGhostItem(
                graphics,
                LostItemsConfig.journeyGhostStack(),
                LostItemsConfig.journeySlotX(),
                LostItemsRecoveryMenu.FETCH_SUPPLY_SLOT_Y,
                this.menu.getJourneyInputStack().isEmpty()
        );

        if (LostItemsConfig.isBurnedFetchEnabled()) {
            this.drawGhostItem(
                    graphics,
                    LostItemsConfig.burnedGhostStack(),
                    LostItemsConfig.burnedSlotX(),
                    LostItemsRecoveryMenu.FETCH_SUPPLY_SLOT_Y,
                    this.menu.getBurnedInputStack().isEmpty()
            );
        }

        if (LostItemsConfig.isVoidFetchEnabled()) {
            this.drawGhostItem(
                    graphics,
                    LostItemsConfig.voidGhostStack(),
                    LostItemsConfig.voidSlotX(),
                    LostItemsRecoveryMenu.FETCH_SUPPLY_SLOT_Y,
                    this.menu.getFallenInputStack().isEmpty()
            );
        }
    }

    /**
     * Draws one translucent ghost item so empty input slots read as placeholders.
     */
    private void drawGhostItem(GuiGraphics graphics, ItemStack stack, int slotX, int slotY, boolean slotIsEmpty) {
        if (!slotIsEmpty || stack.isEmpty() || slotX < 0) {
            return;
        }

        int x = this.leftPos + slotX;
        int y = this.topPos + slotY;
        ItemStack ghostStack = stack.copy();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, GHOST_ITEM_ALPHA);
        graphics.renderFakeItem(ghostStack, x, y);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.renderItemDecorations(this.font, ghostStack, x, y);
        RenderSystem.disableBlend();
    }

    /**
     * Draws complete/incomplete markers above enabled fetch supply slots.
     */
    private void drawFetchStatusBadges(GuiGraphics graphics) {
        if (!LostItemsConfig.isFetchEnabled()) {
            return;
        }

        this.drawFetchStatusBadge(graphics, LostItemsConfig.journeySlotX(), this.hasJourneySupply());

        if (LostItemsConfig.isBurnedFetchEnabled()) {
            this.drawFetchStatusBadge(graphics, LostItemsConfig.burnedSlotX(), this.hasBurnedFetchSupply());
        }

        if (LostItemsConfig.isVoidFetchEnabled()) {
            this.drawFetchStatusBadge(graphics, LostItemsConfig.voidSlotX(), this.hasVoidFetchSupply());
        }
    }

    /**
     * Draws one status badge for a fetch supply slot.
     */
    private void drawFetchStatusBadge(GuiGraphics graphics, int slotX, boolean complete) {
        if (slotX < 0) {
            return;
        }

        ResourceLocation texture = complete ? COMPLETE_TEXTURE : INCOMPLETE_TEXTURE;
        graphics.blit(
                texture,
                this.fetchStatusX(slotX),
                this.fetchStatusY(),
                0.0F,
                0.0F,
                FETCH_STATUS_SIZE,
                FETCH_STATUS_SIZE,
                FETCH_STATUS_SIZE,
                FETCH_STATUS_SIZE
        );
    }

    /**
     * Shows the correct dynamic tooltip when hovering a fetch status badge.
     */
    private void setFetchBadgeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!LostItemsConfig.isFetchEnabled()) {
            return;
        }

        if (this.isHoveringFetchStatus(LostItemsConfig.journeySlotX(), mouseX, mouseY)) {
            graphics.renderTooltip(this.font, this.journeyTooltip(), mouseX, mouseY);
            return;
        }

        if (LostItemsConfig.isBurnedFetchEnabled() && this.isHoveringFetchStatus(LostItemsConfig.burnedSlotX(), mouseX, mouseY)) {
            graphics.renderTooltip(this.font, this.burnedTooltip(), mouseX, mouseY);
            return;
        }

        if (LostItemsConfig.isVoidFetchEnabled() && this.isHoveringFetchStatus(LostItemsConfig.voidSlotX(), mouseX, mouseY)) {
            graphics.renderTooltip(this.font, this.voidTooltip(), mouseX, mouseY);
        }
    }

    /**
     * Explains why the Fetch button is disabled when the supplies look correct.
     */
    private void setFetchButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.fetchButton == null || !this.fetchButton.visible || !this.isHoveringFetchButton(mouseX, mouseY)) {
            return;
        }

        Component tooltip = null;
        if (this.menu.isFetchActive()) {
            tooltip = Component.literal("This trader is already fetching your loot.");
        } else if (!this.hasFetchableLoot()) {
            tooltip = Component.literal("No unloaded, burned, or void-lost loot is waiting to be fetched.");
        } else if (!this.hasJourneySupply()) {
            tooltip = this.journeyTooltip();
        } else if (this.menu.getBurnedCount() > 0 && !this.hasBurnedFetchSupply()) {
            tooltip = this.burnedTooltip();
        } else if (this.menu.getFallenCount() > 0 && !this.hasVoidFetchSupply()) {
            tooltip = this.voidTooltip();
        }

        if (tooltip != null) {
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    /**
     * @return whether there is any hidden or unloaded loot that Fetch can start retrieving.
     */
    private boolean hasFetchableLoot() {
        return this.menu.getTrackedChunkCount() > 0
                || this.menu.getBurnedCount() > 0
                || this.menu.getFallenCount() > 0;
    }

    /**
     * @return tooltip text for the journey supply requirement
     */
    private Component journeyTooltip() {
        int amount = LostItemsConfig.journeySupplyAmount();
        if (LostItemsConfig.useFoodForJourney()) {
            return Component.literal(amount + " non-raw food " + (amount == 1 ? "item is" : "items are") + " needed to find your old loot.");
        }

        return Component.literal(this.requirementText(amount, LostItemsConfig.journeyGhostStack(), "your old loot"));
    }

    /**
     * @return tooltip text for the burned-loot fetch requirement
     */
    private Component burnedTooltip() {
        int amount = LostItemsConfig.burnedFetchAmount();
        return Component.literal(this.requirementText(amount, LostItemsConfig.burnedGhostStack(), "your burned loot"));
    }

    /**
     * @return tooltip text for the void-loot fetch requirement
     */
    private Component voidTooltip() {
        int amount = LostItemsConfig.voidFetchAmount();
        return Component.literal(this.requirementText(amount, LostItemsConfig.voidGhostStack(), "your void loot"));
    }

    /**
     * Builds a grammatically simple requirement sentence for configured items.
     */
    private String requirementText(int amount, ItemStack stack, String targetLoot) {
        return amount + " " + this.itemName(stack, amount) + " " + (amount == 1 ? "is" : "are") + " needed to find " + targetLoot + ".";
    }

    /**
     * @return singular or best-effort plural display name
     */
    private String itemName(ItemStack stack, int amount) {
        String name = stack.getHoverName().getString();
        return amount == 1 ? name : pluralize(name);
    }

    /**
     * Best-effort pluralizer for short tooltips. It is not a full localization system.
     */
    private static String pluralize(String name) {
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        if (lowerName.endsWith("beef") || lowerName.endsWith("fish") || lowerName.endsWith("food")) {
            return name;
        }

        if (lowerName.endsWith("s") || lowerName.endsWith("x") || lowerName.endsWith("ch") || lowerName.endsWith("sh")) {
            return name + "es";
        }

        if (lowerName.endsWith("y") && name.length() > 1) {
            char beforeY = lowerName.charAt(lowerName.length() - 2);
            if ("aeiou".indexOf(beforeY) < 0) {
                return name.substring(0, name.length() - 1) + "ies";
            }
        }

        return name + "s";
    }

    /**
     * @return whether the journey supply slot is complete
     */
    private boolean hasJourneySupply() {
        ItemStack stack = this.menu.getJourneyInputStack();
        return LostItemsConfig.isJourneySupply(stack) && stack.getCount() >= LostItemsConfig.journeySupplyAmount();
    }

    /**
     * @return whether the burned-loot supply slot is complete
     */
    private boolean hasBurnedFetchSupply() {
        ItemStack stack = this.menu.getBurnedInputStack();
        return LostItemsConfig.isBurnedFetchSupply(stack) && stack.getCount() >= LostItemsConfig.burnedFetchAmount();
    }

    /**
     * @return whether the void-loot supply slot is complete
     */
    private boolean hasVoidFetchSupply() {
        ItemStack stack = this.menu.getFallenInputStack();
        return LostItemsConfig.isVoidFetchSupply(stack) && stack.getCount() >= LostItemsConfig.voidFetchAmount();
    }

    /**
     * @return whether the mouse is over a small fetch status badge
     */
    private boolean isHoveringFetchStatus(int slotX, int mouseX, int mouseY) {
        if (slotX < 0) {
            return false;
        }

        int x = this.fetchStatusX(slotX);
        int y = this.fetchStatusY();
        return mouseX >= x - FETCH_STATUS_HOVER_PADDING
                && mouseX < x + FETCH_STATUS_SIZE + FETCH_STATUS_HOVER_PADDING
                && mouseY >= y - FETCH_STATUS_HOVER_PADDING
                && mouseY < y + FETCH_STATUS_SIZE + FETCH_STATUS_HOVER_PADDING;
    }

    /**
     * @return whether the mouse is over the Fetch button bounds.
     */
    private boolean isHoveringFetchButton(int mouseX, int mouseY) {
        int x = this.leftPos + FETCH_BUTTON_X;
        int y = this.topPos + FETCH_BUTTON_Y;
        return mouseX >= x
                && mouseX < x + FETCH_BUTTON_WIDTH
                && mouseY >= y
                && mouseY < y + FETCH_BUTTON_HEIGHT;
    }

    /**
     * @return screen X coordinate for a badge centered above a slot
     */
    private int fetchStatusX(int slotX) {
        return this.leftPos + slotX + ((16 - FETCH_STATUS_SIZE) / 2);
    }

    /**
     * @return screen Y coordinate shared by all fetch badges
     */
    private int fetchStatusY() {
        return this.topPos + LostItemsRecoveryMenu.FETCH_SUPPLY_SLOT_Y - FETCH_STATUS_Y_OFFSET;
    }

    /**
     * Draws a vanilla villager-style scrollbar and requests a matching cursor while hovered.
     */
    private void drawScrollBar(GuiGraphics graphics, int x, int y, int scrollRow, int maxScrollRow, boolean hovered, boolean dragging) {
        ResourceLocation sprite = maxScrollRow > 0 ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        graphics.blitSprite(sprite, x, this.thumbY(y, scrollRow, maxScrollRow), SCROLLBAR_WIDTH, SCROLLER_HEIGHT);
    }

    /**
     * Tracks which grid scrollbar is currently being dragged.
     */
    private enum DraggingScrollbar {
        NONE,
        LEFT,
        RIGHT
    }
}
