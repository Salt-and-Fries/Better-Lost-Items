package org.betterLostItems.better_lost_items.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.betterLostItems.better_lost_items.CollectRecoveryItemPayload;
import org.betterLostItems.better_lost_items.OpenTraderMarketPayload;
import org.betterLostItems.better_lost_items.RecoveryScreenPayload;
import org.betterLostItems.better_lost_items.LostItemEntry;
import org.betterLostItems.better_lost_items.PurchaseRecoveryItemsPayload;

import java.util.List;

/**
 * Legacy standalone recovery screen kept for compatibility with older payload flow.
 *
 * <p>The current production UI is {@link LostItemsRecoveryMenuScreen}, which is backed by a real
 * container menu so players can use normal inventory interactions. This screen is intentionally
 * left functional because its packets are still registered and it can be useful while debugging
 * client-only rendering ideas.</p>
 */
public final class LostItemsRecoveryScreen extends Screen {
    private static final int WINDOW_WIDTH = 372;
    private static final int WINDOW_HEIGHT = 214;
    private static final int PANEL_WIDTH = 142;
    private static final int PANEL_HEIGHT = 126;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_COLUMNS = 7;
    private static final int GRID_ROWS = 5;
    private static final int GRID_SLOTS = GRID_COLUMNS * GRID_ROWS;
    private static final int RECOVERY_PRICE = 10;

    private RecoveryScreenPayload state;
    private int leftScrollRow;
    private int rightScrollRow;
    private int originX;
    private int originY;
    private LostItemsTabButton marketTab;
    private LostItemsTabButton recoveryTab;
    private EditBox emeraldInput;
    private Button purchaseButton;

    /**
     * Creates the legacy screen from a full clientbound state payload.
     */
    public LostItemsRecoveryScreen(RecoveryScreenPayload state) {
        super(Component.literal("Lost Item Recovery"));
        this.state = state;
    }

    /**
     * @return trader entity backing this legacy screen
     */
    public int getTraderEntityId() {
        return this.state.traderEntityId();
    }

    /**
     * Applies refreshed state without recreating the screen.
     */
    public void applyState(RecoveryScreenPayload state) {
        this.state = state;
        this.leftScrollRow = clampScroll(this.leftScrollRow, state.lostItems());
        this.rightScrollRow = clampScroll(this.rightScrollRow, state.purchasedItems());
        if (this.marketTab != null) {
            this.marketTab.active = state.marketCount() > 0;
            this.marketTab.setTooltipMessage(Component.literal("Market (" + state.marketCount() + ")"));
        }
        this.updatePurchaseButton();
    }

    /**
     * Creates tabs, input field, and purchase button.
     */
    @Override
    protected void init() {
        this.originX = (this.width - WINDOW_WIDTH) / 2;
        this.originY = (this.height - WINDOW_HEIGHT) / 2;

        this.marketTab = this.addRenderableWidget(new LostItemsTabButton(
                this.originX + 14,
                this.originY - 28,
                true,
                new ItemStack(Items.EMERALD),
                Component.literal("Market"),
                Component.literal("Market (" + this.state.marketCount() + ")"),
                false,
                button -> ClientPlayNetworking.send(new OpenTraderMarketPayload(this.state.traderEntityId()))
        ));
        this.marketTab.active = this.state.marketCount() > 0;

        this.recoveryTab = this.addRenderableWidget(new LostItemsTabButton(
                this.originX + 42,
                this.originY - 28,
                false,
                new ItemStack(Items.CHEST),
                Component.literal("Recovery"),
                Component.literal("Recovery"),
                true,
                button -> {
                }
        ));
        this.recoveryTab.active = false;

        this.emeraldInput = this.addRenderableWidget(new EditBox(this.font, this.originX + 158, this.originY + 96, 56, 20, Component.literal("Emeralds")));
        this.emeraldInput.setValue(Integer.toString(RECOVERY_PRICE));
        this.emeraldInput.setResponder(value -> this.updatePurchaseButton());

        this.purchaseButton = this.addRenderableWidget(Button.builder(Component.literal("Buy All"), button ->
                        ClientPlayNetworking.send(new PurchaseRecoveryItemsPayload(this.state.traderEntityId(), parseEmeraldOffer())))
                .bounds(this.originX + 152, this.originY + 124, 68, 20)
                .build());

        this.updatePurchaseButton();
    }

    /**
     * Draws the full custom legacy screen.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderTransparentBackground(graphics);
        renderWindow(graphics);
        renderGrid(graphics, this.state.lostItems(), leftGridX(), gridY(), this.leftScrollRow, false, mouseX, mouseY);
        renderGrid(graphics, this.state.purchasedItems(), rightGridX(), gridY(), this.rightScrollRow, true, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, delta);
        renderLabels(graphics);
    }

    /**
     * Handles clicking retrieved items in the right grid.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        LostItemEntry clickedPurchasedItem = entryAt(this.state.purchasedItems(), rightGridX(), gridY(), this.rightScrollRow, mouseX, mouseY);
        if (clickedPurchasedItem != null) {
            ClientPlayNetworking.send(new CollectRecoveryItemPayload(this.state.traderEntityId(), clickedPurchasedItem.id()));
            return true;
        }

        return false;
    }

    /**
     * Scrolls whichever legacy grid the cursor is over.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insideGrid(leftGridX(), gridY(), mouseX, mouseY)) {
            this.leftScrollRow = scrollRows(this.leftScrollRow, this.state.lostItems(), verticalAmount);
            return true;
        }

        if (insideGrid(rightGridX(), gridY(), mouseX, mouseY)) {
            this.rightScrollRow = scrollRows(this.rightScrollRow, this.state.purchasedItems(), verticalAmount);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * Draws the old panel-style background and slot grid.
     */
    private void renderWindow(GuiGraphics graphics) {
        graphics.fill(this.originX, this.originY, this.originX + WINDOW_WIDTH, this.originY + WINDOW_HEIGHT, 0xF0202028);
        graphics.renderOutline(this.originX, this.originY, WINDOW_WIDTH, WINDOW_HEIGHT, 0xFF8B8B8B);

        graphics.fill(this.originX + 12, this.originY + 24, this.originX + 12 + PANEL_WIDTH, this.originY + 24 + PANEL_HEIGHT, 0xCC101014);
        graphics.renderOutline(this.originX + 12, this.originY + 24, PANEL_WIDTH, PANEL_HEIGHT, 0xFF5C5C5C);

        graphics.fill(this.originX + WINDOW_WIDTH - 12 - PANEL_WIDTH, this.originY + 24, this.originX + WINDOW_WIDTH - 12, this.originY + 24 + PANEL_HEIGHT, 0xCC101014);
        graphics.renderOutline(this.originX + WINDOW_WIDTH - 12 - PANEL_WIDTH, this.originY + 24, PANEL_WIDTH, PANEL_HEIGHT, 0xFF5C5C5C);

        graphics.fill(this.originX + 154, this.originY + 42, this.originX + 218, this.originY + 152, 0x99202024);
        graphics.renderOutline(this.originX + 154, this.originY + 42, 64, 110, 0xFF6D6D6D);

        drawSlotGrid(graphics, leftGridX(), gridY());
        drawSlotGrid(graphics, rightGridX(), gridY());
    }

    /**
     * Draws labels for the old panel-style screen.
     */
    private void renderLabels(GuiGraphics graphics) {
        graphics.drawString(this.font, this.title, this.originX + 14, this.originY + 10, 0xFFFFFF);
        graphics.drawString(this.font, Component.literal("Lost Items"), this.originX + 18, this.originY + 30, 0xE0E0E0);
        graphics.drawString(this.font, Component.literal(this.state.lostItems().size() + " stacks"), this.originX + 86, this.originY + 30, 0xB0B0B0);
        graphics.drawString(this.font, Component.literal("Purchased"), this.originX + WINDOW_WIDTH - PANEL_WIDTH + 14, this.originY + 30, 0xE0E0E0);
        graphics.drawString(this.font, Component.literal(this.state.purchasedItems().size() + " stacks"), this.originX + WINDOW_WIDTH - PANEL_WIDTH + 84, this.originY + 30, 0xB0B0B0);

        graphics.drawCenteredString(this.font, Component.literal("Recovery"), this.originX + 186, this.originY + 50, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("10 emeralds"), this.originX + 186, this.originY + 66, 0x55FF55);
        graphics.drawCenteredString(this.font, Component.literal("buys every lost item"), this.originX + 186, this.originY + 78, 0xC8C8C8);
        graphics.drawCenteredString(this.font, Component.literal("Type 10 below"), this.originX + 186, this.originY + 92, 0x9E9E9E);
        graphics.drawCenteredString(this.font, Component.literal("Scroll a column"), this.originX + 186, this.originY + 162, 0xB0B0B0);
        graphics.drawCenteredString(this.font, Component.literal("Click items on the right"), this.originX + 186, this.originY + 174, 0xB0B0B0);
    }

    /**
     * Draws one scrollable grid of legacy recovery entries.
     */
    private void renderGrid(GuiGraphics graphics, List<LostItemEntry> entries, int gridX, int gridY, int scrollRow, boolean purchased, int mouseX, int mouseY) {
        int startIndex = scrollRow * GRID_COLUMNS;
        int endIndex = Math.min(entries.size(), startIndex + GRID_SLOTS);
        for (int index = startIndex; index < endIndex; index++) {
            LostItemEntry entry = entries.get(index);
            int localIndex = index - startIndex;
            int x = gridX + (localIndex % GRID_COLUMNS) * SLOT_SIZE + 1;
            int y = gridY + (localIndex / GRID_COLUMNS) * SLOT_SIZE + 1;
            graphics.renderItem(entry.stack(), x, y);
            graphics.renderItemDecorations(this.font, entry.stack(), x, y);

            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                graphics.renderOutline(x - 1, y - 1, 18, 18, purchased ? 0xFF7BC07B : 0xFFBFBF7B);
                graphics.renderTooltip(this.font, entry.stack(), mouseX, mouseY);
            }
        }

        if (entries.isEmpty()) {
            graphics.drawCenteredString(this.font, purchased ? Component.literal("Nothing purchased yet") : Component.literal("No lost items waiting"), gridX + 63, gridY + 38, 0x8F8F8F);
        }
    }

    /**
     * Draws placeholder slot boxes for the legacy screen.
     */
    private void drawSlotGrid(GuiGraphics graphics, int gridX, int gridY) {
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int x = gridX + column * SLOT_SIZE;
                int y = gridY + row * SLOT_SIZE;
                graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x66101010);
                graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0xFF363636);
            }
        }
    }

    /**
     * Finds the entry under the mouse in a legacy grid.
     */
    private LostItemEntry entryAt(List<LostItemEntry> entries, int gridX, int gridY, int scrollRow, double mouseX, double mouseY) {
        if (!insideGrid(gridX, gridY, mouseX, mouseY)) {
            return null;
        }

        int column = (int) ((mouseX - gridX) / SLOT_SIZE);
        int row = (int) ((mouseY - gridY) / SLOT_SIZE);
        int entryIndex = scrollRow * GRID_COLUMNS + row * GRID_COLUMNS + column;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return null;
        }

        return entries.get(entryIndex);
    }

    /**
     * @return whether a coordinate is inside a legacy grid
     */
    private boolean insideGrid(int gridX, int gridY, double mouseX, double mouseY) {
        return mouseX >= gridX && mouseX < gridX + GRID_COLUMNS * SLOT_SIZE
                && mouseY >= gridY && mouseY < gridY + GRID_ROWS * SLOT_SIZE;
    }

    /**
     * Converts mouse wheel movement to a legacy grid row.
     */
    private int scrollRows(int currentRow, List<LostItemEntry> entries, double verticalAmount) {
        int direction = verticalAmount > 0.0D ? -1 : 1;
        return clampScroll(currentRow + direction, entries);
    }

    /**
     * Clamps a legacy grid row to valid bounds.
     */
    private static int clampScroll(int currentRow, List<LostItemEntry> entries) {
        int maxRows = Math.max(0, Mth.ceil(entries.size() / (float) GRID_COLUMNS) - GRID_ROWS);
        return Mth.clamp(currentRow, 0, maxRows);
    }

    /**
     * Enables the legacy purchase button only when the old typed payment is valid.
     */
    private void updatePurchaseButton() {
        if (this.purchaseButton == null) {
            return;
        }

        this.purchaseButton.active = parseEmeraldOffer() == RECOVERY_PRICE
                && !this.state.lostItems().isEmpty()
                && playerEmeraldCount() >= RECOVERY_PRICE;
    }

    /**
     * Parses the old editable emerald field.
     */
    private int parseEmeraldOffer() {
        if (this.emeraldInput == null) {
            return 0;
        }

        try {
            return Integer.parseInt(this.emeraldInput.getValue().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * Counts local-player emeralds for the legacy screen.
     */
    private int playerEmeraldCount() {
        if (Minecraft.getInstance().player == null) {
            return 0;
        }

        int emeralds = 0;
        for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
            if (stack.is(Items.EMERALD)) {
                emeralds += stack.getCount();
            }
        }
        return emeralds;
    }

    /**
     * @return left legacy grid X coordinate
     */
    private int leftGridX() {
        return this.originX + 20;
    }

    /**
     * @return right legacy grid X coordinate
     */
    private int rightGridX() {
        return this.originX + WINDOW_WIDTH - 20 - GRID_COLUMNS * SLOT_SIZE;
    }

    /**
     * @return shared legacy grid Y coordinate
     */
    private int gridY() {
        return this.originY + 50;
    }
}
