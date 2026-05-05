package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.betterLostItems.better_lost_items.Better_lost_items;

/**
 * Custom textured 22x22 button used to redeem all lost death loot.
 *
 * <p>The base texture changes with widget state and the confirm icon is drawn over the center.
 * Fallback rectangle rendering keeps development builds usable if the texture files are missing.</p>
 */
public final class LostItemsRedeemButton extends AbstractWidget {
    public static final int WIDTH = 22;
    public static final int HEIGHT = 22;
    private static final int OVERLAY_SIZE = 18;

    private static final ResourceLocation IDLE_TEXTURE = Better_lost_items.id("textures/gui/button.png");
    private static final ResourceLocation DISABLED_TEXTURE = Better_lost_items.id("textures/gui/button_disabled.png");
    private static final ResourceLocation HIGHLIGHTED_TEXTURE = Better_lost_items.id("textures/gui/button_highlighted.png");
    private static final ResourceLocation SELECTED_TEXTURE = Better_lost_items.id("textures/gui/button_selected.png");
    private static final ResourceLocation CONFIRM_TEXTURE = Better_lost_items.id("textures/gui/confirm.png");

    private final PressAction onPress;
    private final Component tooltip;
    private boolean pressed;

    /**
     * Creates the redeem button.
     */
    public LostItemsRedeemButton(int x, int y, Component tooltip, PressAction onPress) {
        super(x, y, WIDTH, HEIGHT, Component.empty());
        this.tooltip = tooltip;
        this.onPress = onPress;
    }

    /**
     * Tracks pressed state for the selected texture.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean clicked = super.mouseClicked(mouseX, mouseY, button);
        if (clicked) {
            this.pressed = true;
        }
        return clicked;
    }

    /**
     * Clears pressed state after mouse release.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Runs the redeem callback when active.
     */
    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!this.active || !this.visible) {
            return;
        }

        this.playDownSound(Minecraft.getInstance().getSoundManager());
        this.onPress.onPress(this);
    }

    /**
     * Draws the custom state texture, confirm overlay, and hover tooltip.
     */
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ResourceLocation texture = this.currentTexture();
        boolean hasCustomTexture = Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
        if (hasCustomTexture) {
            graphics.blit(
                    texture,
                    this.getX(),
                    this.getY(),
                    0.0F,
                    0.0F,
                    this.width,
                    this.height,
                    this.width,
                    this.height
            );
        } else {
            int fillColor = !this.active ? 0xFF5A5A5A : (this.pressed ? 0xFF707070 : (this.isHoveredOrFocused() ? 0xFFA0A0A0 : 0xFF8B8B8B));
            graphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), fillColor);
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF1F1F1F);
        }

        if (Minecraft.getInstance().getResourceManager().getResource(CONFIRM_TEXTURE).isPresent()) {
            graphics.blit(
                    CONFIRM_TEXTURE,
                    this.getX() + ((this.width - OVERLAY_SIZE) / 2),
                    this.getY() + ((this.height - OVERLAY_SIZE) / 2),
                    0.0F,
                    0.0F,
                    OVERLAY_SIZE,
                    OVERLAY_SIZE,
                    OVERLAY_SIZE,
                    OVERLAY_SIZE
            );
        }

        if (this.isHovered()) {
            graphics.renderTooltip(Minecraft.getInstance().font, this.tooltip, mouseX, mouseY);
        }
    }

    /**
     * Chooses the texture matching active, pressed, and hovered state.
     */
    private ResourceLocation currentTexture() {
        if (!this.active) {
            return DISABLED_TEXTURE;
        }

        if (this.pressed) {
            return SELECTED_TEXTURE;
        }

        if (this.isHoveredOrFocused()) {
            return HIGHLIGHTED_TEXTURE;
        }

        return IDLE_TEXTURE;
    }

    /**
     * Supplies default widget narration.
     */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    /**
     * Callback invoked when the button is pressed.
     */
    @FunctionalInterface
    public interface PressAction {
        void onPress(LostItemsRedeemButton button);
    }
}
