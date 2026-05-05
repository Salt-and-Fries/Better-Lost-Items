package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.RecoveryScreenPayload;
import org.betterLostItems.better_lost_items.TraderTabStatePayload;

/**
 * Client-only NeoForge hooks for Better Lost Items.
 */
public final class Better_lost_itemsClient {
    private Better_lost_itemsClient() {
    }

    /**
     * Registers the config screen hook used by NeoForge's mod list.
     */
    public static void registerModEventListeners(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(Better_lost_itemsClient::registerScreens);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (minecraft, parent) -> new BetterLostItemsConfigScreen(parent));
    }

    /**
     * Handles trader tab state payloads on the client.
     */
    public static void handleTraderTabState(TraderTabStatePayload payload) {
        Minecraft.getInstance().execute(() -> LostItemsClientState.apply(payload));
    }

    /**
     * Handles legacy recovery screen payloads on the client.
     */
    public static void handleRecoveryScreen(RecoveryScreenPayload payload) {
        Minecraft.getInstance().execute(() -> LostItemsClientState.openOrRefreshRecovery(payload));
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Better_lost_items.LOST_ITEMS_RECOVERY_MENU.get(), LostItemsRecoveryMenuScreen::new);
    }

    @EventBusSubscriber(modid = Better_lost_items.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            LostItemsClientState.tick(Minecraft.getInstance());
        }
    }
}
