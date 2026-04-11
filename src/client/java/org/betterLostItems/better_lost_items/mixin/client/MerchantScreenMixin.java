package org.betterLostItems.better_lost_items.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.betterLostItems.better_lost_items.SwitchTraderTabPayload;
import org.betterLostItems.better_lost_items.TraderTabStatePayload;
import org.betterLostItems.better_lost_items.client.LostItemsClientState;
import org.betterLostItems.better_lost_items.client.LostItemsMerchantScreenBridge;
import org.betterLostItems.better_lost_items.client.LostItemsTabButton;
import org.betterLostItems.better_lost_items.mixin.client.AbstractContainerScreenAccessor;
import org.betterLostItems.better_lost_items.mixin.client.ScreenInvoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds Better Lost Items tabs to the vanilla merchant screen.
 *
 * <p>This mixin only runs on the client and uses server-provided
 * {@link TraderTabStatePayload} data to avoid guessing whether a merchant is a custom trader.</p>
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin implements LostItemsMerchantScreenBridge {
    @Shadow
    private int shopItem;

    @Shadow
    private int scrollOff;

    @Unique
    private LostItemsTabButton betterLostItems$marketButton;

    @Unique
    private LostItemsTabButton betterLostItems$recoveryButton;

    /**
     * Creates tab widgets after vanilla initializes the merchant screen.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void betterLostItems$initTabs(CallbackInfo ci) {
        TraderTabStatePayload state = LostItemsClientState.get(this.betterLostItems$getContainerId());
        if (state != null) {
            this.betterLostItems$ensureButtons();
            this.betterLostItems$applyTabState(state);
        }

        LostItemsClientState.restorePendingMouse();
    }

    /**
     * @return container ID used to match tab state packets to this screen
     */
    @Override
    public int betterLostItems$getContainerId() {
        return ((MerchantScreen) (Object) this).getMenu().containerId;
    }

    /**
     * Applies counts and selected/inactive states for the market and recovery tabs.
     */
    @Override
    public void betterLostItems$applyTabState(TraderTabStatePayload state) {
        if (state.containerId() != this.betterLostItems$getContainerId()) {
            return;
        }

        this.betterLostItems$ensureButtons();
        this.shopItem = 0;
        this.scrollOff = 0;
        ((MerchantScreen) (Object) this).getMenu().setSelectionHint(0);

        this.betterLostItems$marketButton.setSelected(true);
        this.betterLostItems$marketButton.setTooltipMessage(Component.literal("Market (" + state.marketCount() + ")"));
        this.betterLostItems$marketButton.active = false;
        this.betterLostItems$marketButton.visible = true;

        this.betterLostItems$recoveryButton.setSelected(false);
        this.betterLostItems$recoveryButton.setTooltipMessage(Component.literal("Recovery (" + state.recoveryCount() + ")"));
        this.betterLostItems$recoveryButton.active = true;
        this.betterLostItems$recoveryButton.visible = true;
    }

    /**
     * Lazily creates the tab buttons so packets arriving after init can still render them.
     */
    @Unique
    private void betterLostItems$ensureButtons() {
        MerchantScreen screen = (MerchantScreen) (Object) this;
        AbstractContainerScreenAccessor containerScreen = (AbstractContainerScreenAccessor) screen;
        int leftPos = containerScreen.betterLostItems$getLeftPos();
        int topPos = containerScreen.betterLostItems$getTopPos();
        if (this.betterLostItems$marketButton == null) {
            this.betterLostItems$marketButton = ((ScreenInvoker) screen).betterLostItems$invokeAddRenderableWidget(new LostItemsTabButton(
                    leftPos + 10,
                    topPos - 28,
                    true,
                    new ItemStack(Items.EMERALD),
                    Component.literal("Market"),
                    Component.literal("Market"),
                    true,
                    button -> {
                    }
            ));
        }

        if (this.betterLostItems$recoveryButton == null) {
            this.betterLostItems$recoveryButton = ((ScreenInvoker) screen).betterLostItems$invokeAddRenderableWidget(new LostItemsTabButton(
                    leftPos + 38,
                    topPos - 28,
                    false,
                    new ItemStack(Items.CHEST),
                    Component.literal("Recovery"),
                    Component.literal("Recovery"),
                    false,
                    button -> ClientPlayNetworking.send(new SwitchTraderTabPayload(screen.getMenu().containerId, true))
            ));
        }
    }
}
