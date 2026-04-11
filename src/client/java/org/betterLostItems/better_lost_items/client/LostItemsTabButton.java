package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Creative-inventory style tab button used on both trader screens.
 *
 * <p>The button renders above the menu texture. Selected tabs sit lower so they visually connect
 * to the open menu, while inactive tabs rise behind it.</p>
 */
public final class LostItemsTabButton extends AbstractWidget {
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int UNSELECTED_Y_OFFSET = 4;

    private static final Identifier LEFT_UNSELECTED = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_2");
    private static final Identifier LEFT_SELECTED = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_2");
    private static final Identifier RIGHT_UNSELECTED = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_unselected_3");
    private static final Identifier RIGHT_SELECTED = Identifier.withDefaultNamespace("container/creative_inventory/tab_top_selected_3");

    private final ItemStack icon;
    private final PressAction onPress;
    private final boolean leftTab;
    private final int selectedY;
    private boolean selected;
    private Component tooltip;

    /**
     * Creates a tab button.
     *
     * @param leftTab chooses which vanilla tab sprite shape to use
     * @param icon item icon rendered on top of the tab
     * @param selected whether this tab starts as the active tab
     */
    public LostItemsTabButton(int x, int y, boolean leftTab, ItemStack icon, Component message, Component tooltip, boolean selected, PressAction onPress) {
        super(x, y, TAB_WIDTH, TAB_HEIGHT, message);
        this.leftTab = leftTab;
        this.icon = icon.copy();
        this.tooltip = tooltip;
        this.selectedY = y;
        this.onPress = onPress;
        this.setSelected(selected);
    }

    /**
     * Updates visual selected state and y-position.
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
        this.setY(selected ? this.selectedY : this.selectedY - UNSELECTED_Y_OFFSET);
    }

    /**
     * Updates the hover tooltip text.
     */
    public void setTooltipMessage(Component tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * Captures mouse position before screen recreation and runs the tab action.
     */
    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean bl) {
        if (!this.active || !this.visible) {
            return;
        }

        LostItemsClientState.capturePendingMouse();
        playButtonClickSound(Minecraft.getInstance().getSoundManager());
        this.onPress.onPress(this);
    }

    /**
     * Draws the tab sprite, icon, disabled overlay, and tooltip.
     */
    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Identifier sprite = this.leftTab
                ? (this.selected ? LEFT_SELECTED : LEFT_UNSELECTED)
                : (this.selected ? RIGHT_SELECTED : RIGHT_UNSELECTED);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height);
        graphics.item(this.icon, this.getX() + 5, this.getY() + 8);

        if (!this.active && !this.selected) {
            graphics.fill(this.getX() + 2, this.getY() + 5, this.getRight() - 2, this.getBottom() - 2, 0x66000000);
        }

        if (this.isHovered()) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
        }
    }

    /**
     * Supplies default button narration for accessibility.
     */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    /**
     * Callback invoked when a tab is pressed.
     */
    @FunctionalInterface
    public interface PressAction {
        void onPress(LostItemsTabButton button);
    }
}
