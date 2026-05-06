package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.betterLostItems.better_lost_items.Better_lost_items;

/**
 * Client-only NeoForge setup for Better Lost Items.
 */
public final class Better_lost_itemsClient {
    private Better_lost_itemsClient() {
    }

    /**
     * Registers the recovery menu screen and client tick hook.
     */
    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(Better_lost_itemsClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(Better_lost_itemsClient::onClientTick);
        IConfigScreenFactory configScreenFactory = (container, parent) -> new BetterLostItemsConfigScreen(parent);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Better_lost_items.LOST_ITEMS_RECOVERY_MENU.get(), LostItemsRecoveryMenuScreen::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        LostItemsClientState.tick(Minecraft.getInstance());
    }
}
