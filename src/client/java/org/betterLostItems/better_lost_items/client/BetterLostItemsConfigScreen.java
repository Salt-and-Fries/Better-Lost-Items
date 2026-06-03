package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.betterLostItems.better_lost_items.LostItemsConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * In-game editor for {@code config/better_lost_items.json}.
 *
 * <p>The screen edits a draft copy first. Pressing Apply or Save validates every field, writes the
 * JSON config, and replaces the live {@link LostItemsConfig} data. Because gameplay reads config
 * through static getters, those changes take effect immediately in the current game session.</p>
 */
public final class BetterLostItemsConfigScreen extends Screen {
    private static final int LIST_WIDTH = 430;
    private static final int LIST_TOP = 42;
    private static final int BOTTOM_BAR_HEIGHT = 54;
    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_HEIGHT = 18;
    private static final int FIELD_WIDTH = 190;
    private static final int FIELD_HEIGHT = 18;
    private static final int LABEL_TO_FIELD_GAP = 154;
    private static final int SCROLL_STEP = 18;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int DISABLED_LABEL_COLOR = 0xFFA0A0A0;
    private static final int SECTION_COLOR = 0xFFFFFFA0;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final int SUCCESS_COLOR = 0xFF55FF55;

    private final Screen parent;
    private final LostItemsConfig.ConfigData draft;
    private final List<Label> labels = new ArrayList<>();
    private final List<WidgetRow> widgetRows = new ArrayList<>();

    private EditBox paymentItemField;
    private EditBox paymentAmountField;
    private EditBox journeyFoodAmountField;
    private EditBox journeyCustomItemField;
    private EditBox journeyCustomPotionField;
    private EditBox journeyCustomAmountField;
    private EditBox burnedItemField;
    private EditBox burnedPotionField;
    private EditBox burnedAmountField;
    private EditBox voidItemField;
    private EditBox voidPotionField;
    private EditBox voidAmountField;

    private Button idleDroppedLootTableButton;
    private Button fetchEnabledButton;
    private Button journeyModeButton;
    private Button burnedEnabledButton;
    private Button voidEnabledButton;
    private Button applyButton;
    private Button saveButton;
    private Button defaultsButton;

    private String statusMessage = "Changes apply immediately after saving.";
    private boolean hasValidationError;
    private boolean draggingScrollbar;
    private int scrollOffset;
    private int contentHeight;
    private int listLeft;

    /**
     * Creates a config editor returning to the supplied parent screen.
     *
     * @param parent previous Mod Menu screen
     */
    public BetterLostItemsConfigScreen(Screen parent) {
        super(Component.literal("Better Lost Items Config"));
        this.parent = parent;
        this.draft = LostItemsConfig.copy();
    }

    /**
     * Builds one long scrollable list of options plus fixed bottom action buttons.
     */
    @Override
    protected void init() {
        this.labels.clear();
        this.widgetRows.clear();
        this.listLeft = (this.width - Math.min(LIST_WIDTH, this.width - 40)) / 2;

        int rowY = 0;
        rowY = this.addSectionLabel("Recovery", rowY);
        this.paymentItemField = this.addTextField(rowY, "Payment item", this.draft.deathLootPaymentItem, () -> true, "Item ID used to buy all lost death loot.");
        rowY += ROW_HEIGHT;
        this.paymentAmountField = this.addTextField(rowY, "Payment amount", Integer.toString(this.draft.deathLootPaymentAmount), () -> true, "How many payment items are needed to move all lost loot into retrieved loot.");
        rowY += ROW_HEIGHT + 8;

        rowY = this.addSectionLabel("Wandering Trader Market", rowY);
        this.idleDroppedLootTableButton = this.addWideButtonRow(rowY, this.idleDroppedLootTableText(), () -> true, "Allows idle despawned items without a player tag to become wandering trader market offers.", button -> {
            this.draft.idleDroppedItemsLootTableEnabled = !this.draft.idleDroppedItemsLootTableEnabled;
            this.updateWidgetStates();
            this.refreshValidation();
        });
        rowY += ROW_HEIGHT + 8;

        rowY = this.addSectionLabel("Fetch Journey", rowY);
        this.fetchEnabledButton = this.addToggle(rowY, "Fetch system", this.draft.fetchEnabled, () -> true, "Allows traders to fetch death drops from unloaded chunks.", button -> {
            this.draft.fetchEnabled = !this.draft.fetchEnabled;
            this.updateWidgetStates();
            this.refreshValidation();
        });
        rowY += ROW_HEIGHT;
        this.journeyModeButton = this.addButtonRow(rowY, "Journey mode", this.journeyModeText(), () -> this.draft.fetchEnabled, "Choose whether the first fetch slot accepts broad food or one configured item.", button -> {
            this.draft.useFoodForJourney = !this.draft.useFoodForJourney;
            this.updateWidgetStates();
            this.refreshValidation();
        });
        rowY += ROW_HEIGHT;
        this.journeyFoodAmountField = this.addTextField(rowY, "Food amount", Integer.toString(this.draft.journeyFoodAmount), () -> this.draft.fetchEnabled && this.draft.useFoodForJourney, "How many non-raw food items are required for a fetch journey.");
        rowY += ROW_HEIGHT;
        this.journeyCustomItemField = this.addTextField(rowY, "Custom item", this.draft.journeyCustomItem, () -> this.draft.fetchEnabled && !this.draft.useFoodForJourney, "Item ID required in the journey slot when custom item mode is selected.");
        rowY += ROW_HEIGHT;
        this.journeyCustomPotionField = this.addTextField(rowY, "Custom potion", this.draft.journeyCustomPotion, () -> this.draft.fetchEnabled && !this.draft.useFoodForJourney, "Potion ID checked only if the custom journey item is a potion.");
        rowY += ROW_HEIGHT;
        this.journeyCustomAmountField = this.addTextField(rowY, "Custom amount", Integer.toString(this.draft.journeyCustomAmount), () -> this.draft.fetchEnabled && !this.draft.useFoodForJourney, "How many custom journey items are required for a fetch journey.");
        rowY += ROW_HEIGHT + 8;

        rowY = this.addSectionLabel("Burned Loot", rowY);
        this.burnedEnabledButton = this.addToggle(rowY, "Retrievable", this.draft.burnedItemsRetrievable, () -> this.draft.fetchEnabled, "Allows fire/lava-destroyed death loot to be recovered with an extra fetch supply.", button -> {
            this.draft.burnedItemsRetrievable = !this.draft.burnedItemsRetrievable;
            this.updateWidgetStates();
            this.refreshValidation();
        });
        rowY += ROW_HEIGHT;
        this.burnedItemField = this.addTextField(rowY, "Fetch item", this.draft.burnedFetchItem, () -> this.draft.fetchEnabled && this.draft.burnedItemsRetrievable, "Item ID required to include burned loot in a fetch journey.");
        rowY += ROW_HEIGHT;
        this.burnedPotionField = this.addTextField(rowY, "Potion type", this.draft.burnedFetchPotion, () -> this.draft.fetchEnabled && this.draft.burnedItemsRetrievable, "Potion ID checked only if the burned fetch item is a potion.");
        rowY += ROW_HEIGHT;
        this.burnedAmountField = this.addTextField(rowY, "Item amount", Integer.toString(this.draft.burnedFetchAmount), () -> this.draft.fetchEnabled && this.draft.burnedItemsRetrievable, "How many burned fetch items are required.");
        rowY += ROW_HEIGHT + 8;

        rowY = this.addSectionLabel("Void Loot", rowY);
        this.voidEnabledButton = this.addToggle(rowY, "Retrievable", this.draft.voidLostItemsRetrievable, () -> this.draft.fetchEnabled, "Allows void-deleted death loot to be recovered with an extra fetch supply.", button -> {
            this.draft.voidLostItemsRetrievable = !this.draft.voidLostItemsRetrievable;
            this.updateWidgetStates();
            this.refreshValidation();
        });
        rowY += ROW_HEIGHT;
        this.voidItemField = this.addTextField(rowY, "Fetch item", this.draft.voidFetchItem, () -> this.draft.fetchEnabled && this.draft.voidLostItemsRetrievable, "Item ID required to include void loot in a fetch journey.");
        rowY += ROW_HEIGHT;
        this.voidPotionField = this.addTextField(rowY, "Potion type", this.draft.voidFetchPotion, () -> this.draft.fetchEnabled && this.draft.voidLostItemsRetrievable, "Potion ID checked only if the void fetch item is a potion.");
        rowY += ROW_HEIGHT;
        this.voidAmountField = this.addTextField(rowY, "Item amount", Integer.toString(this.draft.voidFetchAmount), () -> this.draft.fetchEnabled && this.draft.voidLostItemsRetrievable, "How many void fetch items are required.");
        rowY += ROW_HEIGHT;

        this.contentHeight = rowY;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScrollOffset());
        this.addActionButtons();
        this.updateWidgetStates();
        this.refreshValidation();
    }

    /**
     * Draws a dark, vanilla-options style screen with a scrollable option list.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.extractTransparentBackground(graphics);
        graphics.fill(RenderPipelines.GUI, 0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(RenderPipelines.GUI, 0, 0, this.width, LIST_TOP - 8, 0xA0000000);
        graphics.fill(RenderPipelines.GUI, 0, this.listBottom(), this.width, this.height, 0xC0000000);
        graphics.centeredText(this.font, this.title, this.width / 2, 16, TITLE_COLOR);

        for (Label label : this.labels) {
            int y = this.scrolledY(label.baseY());
            if (!this.isTextRowVisible(y)) {
                continue;
            }

            int color = label.section() ? SECTION_COLOR : (label.active().getAsBoolean() ? LABEL_COLOR : DISABLED_LABEL_COLOR);
            graphics.text(this.font, Component.literal(label.text()), this.listLeft, y, color, true);
        }

        this.drawScrollbar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.drawOptionTooltip(graphics, mouseX, mouseY);
        int statusColor = this.hasValidationError ? ERROR_COLOR : SUCCESS_COLOR;
        graphics.centeredText(this.font, Component.literal(this.statusMessage), this.width / 2, this.height - 46, statusColor);
    }

    /**
     * Scrolls the option list while the cursor is inside the list viewport.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY < LIST_TOP || mouseY > this.listBottom()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int direction = verticalAmount > 0.0D ? -1 : 1;
        return this.scrollBy(direction * SCROLL_STEP);
    }

    /**
     * Starts scrollbar dragging when the scrollbar track is clicked.
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (this.isHoveringScrollbar(event.x(), event.y())) {
            this.draggingScrollbar = true;
            this.dragScrollbarTo(event.y());
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Updates scroll position while the scrollbar thumb is dragged.
     */
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            this.dragScrollbarTo(event.y());
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    /**
     * Stops scrollbar dragging.
     */
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    /**
     * Returns to the Mod Menu screen without applying unsaved edits.
     */
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /**
     * Adds the fixed Apply, Save, and Cancel buttons at the bottom of the screen.
     */
    private void addActionButtons() {
        int buttonY = this.height - 28;
        int center = this.width / 2;
        this.defaultsButton = this.addRenderableWidget(Button.builder(Component.literal("Defaults"), button -> this.resetToDefaults())
                .bounds(center - 206, buttonY, 96, 20)
                .build());
        this.applyButton = this.addRenderableWidget(Button.builder(Component.literal("Apply"), button -> this.applyConfig(false))
                .bounds(center - 102, buttonY, 96, 20)
                .build());
        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.applyConfig(true))
                .bounds(center + 2, buttonY, 96, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(center + 106, buttonY, 96, 20)
                .build());
    }

    /**
     * Adds a non-interactive section label to the scroll list.
     *
     * @return next row Y position after the section label
     */
    private int addSectionLabel(String text, int rowY) {
        this.labels.add(new Label(text, rowY, true, () -> true, ""));
        return rowY + SECTION_HEIGHT;
    }

    /**
     * Adds a labeled text field and validates whenever it changes.
     */
    private EditBox addTextField(int rowY, String label, String value, BooleanSupplier activeSupplier, String tooltip) {
        int fieldX = this.listLeft + LABEL_TO_FIELD_GAP;
        this.labels.add(new Label(label, rowY + 5, false, activeSupplier, tooltip));
        EditBox field = this.addRenderableWidget(new EditBox(this.font, fieldX, this.scrolledY(rowY), FIELD_WIDTH, FIELD_HEIGHT, Component.literal(label)));
        field.setValue(value == null ? "" : value);
        field.setResponder(ignored -> this.refreshValidation());
        this.widgetRows.add(new WidgetRow(field, rowY, activeSupplier));
        return field;
    }

    /**
     * Adds a labeled boolean toggle button.
     */
    private Button addToggle(int rowY, String label, boolean value, BooleanSupplier activeSupplier, String tooltip, Button.OnPress onPress) {
        return this.addButtonRow(rowY, label, this.enabledText(value), activeSupplier, tooltip, onPress);
    }

    /**
     * Adds a labeled button row to the scroll list.
     */
    private Button addButtonRow(int rowY, String label, Component message, BooleanSupplier activeSupplier, String tooltip, Button.OnPress onPress) {
        int buttonX = this.listLeft + LABEL_TO_FIELD_GAP;
        this.labels.add(new Label(label, rowY + 5, false, activeSupplier, tooltip));
        Button button = this.addRenderableWidget(Button.builder(message, onPress)
                .bounds(buttonX, this.scrolledY(rowY), FIELD_WIDTH, FIELD_HEIGHT)
                .build());
        this.widgetRows.add(new WidgetRow(button, rowY, activeSupplier));
        return button;
    }

    /**
     * Adds a full-width button row for long option names that do not fit the split label layout.
     */
    private Button addWideButtonRow(int rowY, Component message, BooleanSupplier activeSupplier, String tooltip, Button.OnPress onPress) {
        int width = Math.min(LIST_WIDTH, this.width - 40);
        this.labels.add(new Label("", rowY + 5, false, activeSupplier, tooltip));
        Button button = this.addRenderableWidget(Button.builder(message, onPress)
                .bounds(this.listLeft, this.scrolledY(rowY), width, FIELD_HEIGHT)
                .build());
        this.widgetRows.add(new WidgetRow(button, rowY, activeSupplier));
        return button;
    }

    /**
     * Applies active/inactive states after toggles or scrolling change.
     */
    private void updateWidgetStates() {
        this.idleDroppedLootTableButton.setMessage(this.idleDroppedLootTableText());
        this.fetchEnabledButton.setMessage(this.enabledText(this.draft.fetchEnabled));
        this.journeyModeButton.setMessage(this.journeyModeText());
        this.burnedEnabledButton.setMessage(this.enabledText(this.draft.burnedItemsRetrievable));
        this.voidEnabledButton.setMessage(this.enabledText(this.draft.voidLostItemsRetrievable));
        this.updateScrolledWidgets();
    }

    /**
     * Repositions scroll-list widgets and hides rows outside the visible viewport.
     */
    private void updateScrolledWidgets() {
        for (WidgetRow row : this.widgetRows) {
            int y = this.scrolledY(row.baseY());
            boolean visible = y + FIELD_HEIGHT > LIST_TOP && y < this.listBottom();
            row.widget().setY(y);
            row.widget().visible = visible;
            row.widget().active = visible && row.active().getAsBoolean();
        }
    }

    /**
     * Restores every draft value and text field to the built-in defaults.
     */
    private void resetToDefaults() {
        LostItemsConfig.ConfigData defaults = new LostItemsConfig.ConfigData();
        this.copyConfigValues(defaults, this.draft);
        this.writeDraftToFields();
        this.updateWidgetStates();
        this.refreshValidation();
        this.statusMessage = "Defaults restored. Press Apply or Save to use them.";
    }

    /**
     * Copies all raw config values from one data object into another.
     */
    private void copyConfigValues(LostItemsConfig.ConfigData source, LostItemsConfig.ConfigData target) {
        target.deathLootPaymentItem = source.deathLootPaymentItem;
        target.deathLootPaymentAmount = source.deathLootPaymentAmount;
        target.idleDroppedItemsLootTableEnabled = source.idleDroppedItemsLootTableEnabled;
        target.fetchEnabled = source.fetchEnabled;
        target.useFoodForJourney = source.useFoodForJourney;
        target.journeyFoodAmount = source.journeyFoodAmount;
        target.journeyCustomItem = source.journeyCustomItem;
        target.journeyCustomPotion = source.journeyCustomPotion;
        target.journeyCustomAmount = source.journeyCustomAmount;
        target.burnedItemsRetrievable = source.burnedItemsRetrievable;
        target.burnedFetchItem = source.burnedFetchItem;
        target.burnedFetchPotion = source.burnedFetchPotion;
        target.burnedFetchAmount = source.burnedFetchAmount;
        target.voidLostItemsRetrievable = source.voidLostItemsRetrievable;
        target.voidFetchItem = source.voidFetchItem;
        target.voidFetchPotion = source.voidFetchPotion;
        target.voidFetchAmount = source.voidFetchAmount;
    }

    /**
     * Writes draft values into the visible text fields after a reset.
     */
    private void writeDraftToFields() {
        this.paymentItemField.setValue(this.draft.deathLootPaymentItem);
        this.paymentAmountField.setValue(Integer.toString(this.draft.deathLootPaymentAmount));
        this.journeyFoodAmountField.setValue(Integer.toString(this.draft.journeyFoodAmount));
        this.journeyCustomItemField.setValue(this.draft.journeyCustomItem);
        this.journeyCustomPotionField.setValue(this.draft.journeyCustomPotion);
        this.journeyCustomAmountField.setValue(Integer.toString(this.draft.journeyCustomAmount));
        this.burnedItemField.setValue(this.draft.burnedFetchItem);
        this.burnedPotionField.setValue(this.draft.burnedFetchPotion);
        this.burnedAmountField.setValue(Integer.toString(this.draft.burnedFetchAmount));
        this.voidItemField.setValue(this.draft.voidFetchItem);
        this.voidPotionField.setValue(this.draft.voidFetchPotion);
        this.voidAmountField.setValue(Integer.toString(this.draft.voidFetchAmount));
    }

    /**
     * Updates Save/Apply availability and shows the first validation error.
     */
    private void refreshValidation() {
        List<String> errors = this.validateFields();
        this.hasValidationError = !errors.isEmpty();
        this.statusMessage = errors.isEmpty() ? "Ready to save. Changes apply immediately." : errors.getFirst();
        if (this.applyButton != null) {
            this.applyButton.active = errors.isEmpty();
        }
        if (this.saveButton != null) {
            this.saveButton.active = errors.isEmpty();
        }
    }

    /**
     * Validates every config field, including currently inactive options.
     */
    private List<String> validateFields() {
        List<String> errors = new ArrayList<>();
        this.requireItem(errors, "Payment item", this.paymentItemField);
        this.requireAmount(errors, "Payment amount", this.paymentAmountField);
        this.requireAmount(errors, "Food amount", this.journeyFoodAmountField);
        this.requireItem(errors, "Journey custom item", this.journeyCustomItemField);
        this.requirePotion(errors, "Journey custom potion", this.journeyCustomPotionField);
        this.requireAmount(errors, "Journey custom amount", this.journeyCustomAmountField);
        this.requireItem(errors, "Burned fetch item", this.burnedItemField);
        this.requirePotion(errors, "Burned potion type", this.burnedPotionField);
        this.requireAmount(errors, "Burned item amount", this.burnedAmountField);
        this.requireItem(errors, "Void fetch item", this.voidItemField);
        this.requirePotion(errors, "Void potion type", this.voidPotionField);
        this.requireAmount(errors, "Void item amount", this.voidAmountField);
        return errors;
    }

    /**
     * Applies the draft config and optionally closes the screen.
     */
    private void applyConfig(boolean closeAfterSave) {
        if (!this.validateFields().isEmpty()) {
            this.refreshValidation();
            return;
        }

        LostItemsConfig.apply(this.readConfigFromFields());
        this.statusMessage = "Saved. New config values are active now.";
        this.hasValidationError = false;
        if (closeAfterSave) {
            this.onClose();
        }
    }

    /**
     * Builds a config object from current widget values.
     */
    private LostItemsConfig.ConfigData readConfigFromFields() {
        LostItemsConfig.ConfigData config = this.draft.copy();
        config.deathLootPaymentItem = this.normalizedText(this.paymentItemField);
        config.deathLootPaymentAmount = this.parseAmount(this.paymentAmountField);
        config.idleDroppedItemsLootTableEnabled = this.draft.idleDroppedItemsLootTableEnabled;
        config.fetchEnabled = this.draft.fetchEnabled;
        config.useFoodForJourney = this.draft.useFoodForJourney;
        config.journeyFoodAmount = this.parseAmount(this.journeyFoodAmountField);
        config.journeyCustomItem = this.normalizedText(this.journeyCustomItemField);
        config.journeyCustomPotion = this.normalizedText(this.journeyCustomPotionField);
        config.journeyCustomAmount = this.parseAmount(this.journeyCustomAmountField);
        config.burnedItemsRetrievable = this.draft.burnedItemsRetrievable;
        config.burnedFetchItem = this.normalizedText(this.burnedItemField);
        config.burnedFetchPotion = this.normalizedText(this.burnedPotionField);
        config.burnedFetchAmount = this.parseAmount(this.burnedAmountField);
        config.voidLostItemsRetrievable = this.draft.voidLostItemsRetrievable;
        config.voidFetchItem = this.normalizedText(this.voidItemField);
        config.voidFetchPotion = this.normalizedText(this.voidPotionField);
        config.voidFetchAmount = this.parseAmount(this.voidAmountField);
        return config;
    }

    /**
     * Adds an error if a field does not contain a registered item ID.
     */
    private void requireItem(List<String> errors, String label, EditBox field) {
        Identifier identifier = Identifier.tryParse(this.normalizedText(field));
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
            errors.add(label + " must be a valid item ID.");
        }
    }

    /**
     * Adds an error if a field does not contain a registered potion ID.
     */
    private void requirePotion(List<String> errors, String label, EditBox field) {
        Identifier identifier = Identifier.tryParse(this.normalizedText(field));
        if (identifier == null || BuiltInRegistries.POTION.get(identifier).isEmpty()) {
            errors.add(label + " must be a valid potion ID.");
        }
    }

    /**
     * Adds an error if a numeric field is outside the supported stack-count range.
     */
    private void requireAmount(List<String> errors, String label, EditBox field) {
        try {
            int amount = Integer.parseInt(this.normalizedText(field));
            if (amount < 1 || amount > 99) {
                errors.add(label + " must be between 1 and 99.");
            }
        } catch (NumberFormatException exception) {
            errors.add(label + " must be a whole number.");
        }
    }

    /**
     * Draws a small vanilla-style scrollbar when the list is taller than the viewport.
     */
    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int maxScroll = this.maxScrollOffset();
        if (maxScroll <= 0) {
            return;
        }

        int trackX = this.scrollbarX();
        int trackY = this.scrollbarY();
        int trackHeight = this.scrollbarHeight();
        int thumbHeight = this.scrollbarThumbHeight();
        int thumbY = this.scrollbarThumbY();
        graphics.fill(RenderPipelines.GUI, trackX, trackY, trackX + 6, trackY + trackHeight, 0x66000000);
        graphics.fill(RenderPipelines.GUI, trackX, thumbY, trackX + 6, thumbY + thumbHeight, 0xFF808080);
        graphics.fill(RenderPipelines.GUI, trackX + 1, thumbY + 1, trackX + 5, thumbY + thumbHeight - 1, 0xFFC0C0C0);
    }

    /**
     * Draws the hovered option's tooltip.
     */
    private void drawOptionTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Label label : this.labels) {
            if (label.tooltip() == null || label.tooltip().isBlank()) {
                continue;
            }

            int y = this.scrolledY(label.baseY() - 5);
            if (mouseX >= this.listLeft
                    && mouseX < this.listLeft + LIST_WIDTH
                    && mouseY >= y
                    && mouseY < y + ROW_HEIGHT
                    && y + ROW_HEIGHT > LIST_TOP
                    && y < this.listBottom()) {
                graphics.setTooltipForNextFrame(this.font, Component.literal(label.tooltip()), mouseX, mouseY);
                return;
            }
        }
    }

    /**
     * Moves the list by a pixel delta.
     */
    private boolean scrollBy(int amount) {
        int previous = this.scrollOffset;
        this.scrollOffset = Mth.clamp(this.scrollOffset + amount, 0, this.maxScrollOffset());
        this.updateScrolledWidgets();
        return previous != this.scrollOffset;
    }

    /**
     * Updates scroll offset from an absolute mouse Y while dragging the scrollbar.
     */
    private void dragScrollbarTo(double mouseY) {
        int maxScroll = this.maxScrollOffset();
        if (maxScroll <= 0) {
            return;
        }

        int travel = this.scrollbarHeight() - this.scrollbarThumbHeight();
        float relative = ((float) mouseY - this.scrollbarY() - (this.scrollbarThumbHeight() / 2.0F)) / Math.max(1, travel);
        this.scrollOffset = Mth.clamp(Math.round(relative * maxScroll), 0, maxScroll);
        this.updateScrolledWidgets();
    }

    /**
     * @return whether the mouse is inside the scrollbar track
     */
    private boolean isHoveringScrollbar(double mouseX, double mouseY) {
        return this.maxScrollOffset() > 0
                && mouseX >= this.scrollbarX()
                && mouseX < this.scrollbarX() + 6
                && mouseY >= this.scrollbarY()
                && mouseY < this.scrollbarY() + this.scrollbarHeight();
    }

    /**
     * @return scrollbar track X coordinate
     */
    private int scrollbarX() {
        return this.listLeft + LIST_WIDTH + 8;
    }

    /**
     * @return scrollbar track Y coordinate
     */
    private int scrollbarY() {
        return LIST_TOP;
    }

    /**
     * @return scrollbar track height
     */
    private int scrollbarHeight() {
        return this.listBottom() - LIST_TOP;
    }

    /**
     * @return scrollbar thumb height based on visible/content ratio
     */
    private int scrollbarThumbHeight() {
        int trackHeight = this.scrollbarHeight();
        return Math.max(24, trackHeight * trackHeight / Math.max(trackHeight, this.contentHeight));
    }

    /**
     * @return scrollbar thumb Y coordinate
     */
    private int scrollbarThumbY() {
        int maxScroll = this.maxScrollOffset();
        if (maxScroll <= 0) {
            return this.scrollbarY();
        }

        int travel = this.scrollbarHeight() - this.scrollbarThumbHeight();
        return this.scrollbarY() + Math.round(travel * (this.scrollOffset / (float) maxScroll));
    }

    /**
     * Parses a valid amount field.
     */
    private int parseAmount(EditBox field) {
        return Integer.parseInt(this.normalizedText(field));
    }

    /**
     * @return trimmed text from an edit box
     */
    private String normalizedText(EditBox field) {
        return field.getValue().trim();
    }

    /**
     * @return screen Y coordinate for a row after applying scroll offset
     */
    private int scrolledY(int baseY) {
        return LIST_TOP + baseY - this.scrollOffset;
    }

    /**
     * @return bottom of the scrollable list viewport
     */
    private int listBottom() {
        return this.height - BOTTOM_BAR_HEIGHT;
    }

    /**
     * @return maximum legal scroll offset for the current screen height
     */
    private int maxScrollOffset() {
        return Math.max(0, this.contentHeight - (this.listBottom() - LIST_TOP));
    }

    /**
     * @return whether a label row should be drawn inside the viewport
     */
    private boolean isTextRowVisible(int y) {
        return y + this.font.lineHeight > LIST_TOP && y < this.listBottom();
    }

    /**
     * @return text for enabled/disabled toggle buttons
     */
    private Component enabledText(boolean enabled) {
        return Component.literal(enabled ? "Enabled" : "Disabled");
    }

    /**
     * @return text for the idle dropped items loot-table toggle button
     */
    private Component idleDroppedLootTableText() {
        return Component.literal("Enable villager idle dropped items loot table: " + (this.draft.idleDroppedItemsLootTableEnabled ? "Enabled" : "Disabled"));
    }

    /**
     * @return text for the journey mode toggle button
     */
    private Component journeyModeText() {
        return Component.literal(this.draft.useFoodForJourney ? "Food" : "Custom Item");
    }

    /**
     * Static text drawn next to a widget in the scrolling list.
     *
     * @param text label text
     * @param baseY unscrolled Y coordinate inside the list
     * @param section whether this row is a section heading
     * @param active whether the label should use active or disabled color
     * @param tooltip hover description for option rows; blank for section headings
     */
    private record Label(String text, int baseY, boolean section, BooleanSupplier active, String tooltip) {
    }

    /**
     * Widget row that can be moved and hidden as the list scrolls.
     *
     * @param widget button or edit box in the scroll list
     * @param baseY unscrolled Y coordinate inside the list
     * @param active whether the widget should be interactive when visible
     */
    private record WidgetRow(AbstractWidget widget, int baseY, BooleanSupplier active) {
    }
}
