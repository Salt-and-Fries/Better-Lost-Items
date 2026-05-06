package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;
import org.betterLostItems.better_lost_items.TraderTabStatePayload;
import org.betterLostItems.better_lost_items.RecoveryScreenPayload;

/**
 * Small client-side state cache shared between trader screens and networking callbacks.
 *
 * <p>Minecraft recreates screens while opening/switching menus, so this class temporarily stores
 * tab state and mouse coordinates long enough for the new screen to initialize cleanly.</p>
 */
public final class LostItemsClientState {
    private static TraderTabStatePayload currentState;
    private static double pendingMouseX = Double.NaN;
    private static double pendingMouseY = Double.NaN;

    private LostItemsClientState() {
    }

    /**
     * Applies the latest tab counts to the open merchant screen if it matches.
     */
    public static void apply(TraderTabStatePayload state) {
        currentState = state;
        if (Minecraft.getInstance().screen instanceof LostItemsMerchantScreenBridge bridge) {
            bridge.betterLostItems$applyTabState(state);
        }
    }

    public static void handleTraderTabState(TraderTabStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> apply(payload));
    }

    public static void handleRecoveryScreen(RecoveryScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> openOrRefreshRecovery(payload));
    }

    /**
     * @return cached tab state for a container ID, or {@code null}
     */
    public static TraderTabStatePayload get(int containerId) {
        if (currentState != null && currentState.containerId() == containerId) {
            return currentState;
        }
        return null;
    }

    /**
     * Clears stale tab state once the matching screen is no longer open.
     */
    public static void tick(Minecraft client) {
        if (currentState == null) {
            return;
        }

        if (!(client.screen instanceof LostItemsMerchantScreenBridge bridge)) {
            currentState = null;
            return;
        }

        if (bridge.betterLostItems$getContainerId() != currentState.containerId()) {
            currentState = null;
        }
    }

    /**
     * Captures current cursor coordinates before a tab switch recreates the screen.
     */
    public static void capturePendingMouse() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler.isMouseGrabbed()) {
            return;
        }

        pendingMouseX = minecraft.mouseHandler.xpos();
        pendingMouseY = minecraft.mouseHandler.ypos();
    }

    /**
     * Restores captured cursor coordinates after a new screen is initialized.
     */
    public static void restorePendingMouse() {
        if (Double.isNaN(pendingMouseX) || Double.isNaN(pendingMouseY)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), pendingMouseX, pendingMouseY);
        minecraft.mouseHandler.setIgnoreFirstMove();
        pendingMouseX = Double.NaN;
        pendingMouseY = Double.NaN;
    }

    /**
     * Opens or refreshes the legacy recovery screen.
     */
    public static void openOrRefreshRecovery(RecoveryScreenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof LostItemsRecoveryScreen recoveryScreen && recoveryScreen.getTraderEntityId() == payload.traderEntityId()) {
            recoveryScreen.applyState(payload);
            return;
        }

        minecraft.setScreen(new LostItemsRecoveryScreen(payload));
    }
}
