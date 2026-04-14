package org.betterLostItems.better_lost_items.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.RecoveryScreenPayload;
import org.betterLostItems.better_lost_items.TraderTabStatePayload;

/**
 * Client-only NeoForge entry point for Better Lost Items.
 */
@Mod(value = Better_lost_items.MOD_ID, dist = Dist.CLIENT)
public class Better_lost_itemsClient {

    /**
     * Registers the recovery menu screen and clientbound packet handlers.
     */
    public Better_lost_itemsClient(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(this::registerMenuScreens);
        modBus.addListener(this::registerClientPayloadHandlers);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (container, modListScreen) -> new BetterLostItemsConfigScreen(modListScreen));
    }

    private void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Better_lost_items.LOST_ITEMS_RECOVERY_MENU.get(), LostItemsRecoveryMenuScreen::new);
    }

    private void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(TraderTabStatePayload.TYPE, Better_lost_itemsClient::handleTraderTabState);
        event.register(RecoveryScreenPayload.TYPE, Better_lost_itemsClient::handleRecoveryScreen);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        LostItemsClientState.tick(Minecraft.getInstance());
    }

    private static void handleTraderTabState(TraderTabStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LostItemsClientState.apply(payload));
    }

    private static void handleRecoveryScreen(RecoveryScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LostItemsClientState.openOrRefreshRecovery(payload));
    }
}
