package org.betterLostItems.better_lost_items;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Fabric entry point for Better Lost Items.
 *
 * <p>This class intentionally stays small: it registers the shared menu type,
 * networking payloads, commands, storage lifecycle hooks, and server tick
 * workers. Most gameplay behavior lives in the controller/manager classes so
 * new features do not have to be squeezed into the mod initializer.</p>
 */
public class Better_lost_items implements ModInitializer {
    public static final String MOD_ID = "better_lost_items";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MenuType<LostItemsRecoveryMenu> LOST_ITEMS_RECOVERY_MENU = Registry.register(
            BuiltInRegistries.MENU,
            id("lost_items_recovery"),
            new MenuType<>(LostItemsRecoveryMenu::new, FeatureFlags.VANILLA_SET)
    );

    /**
     * Creates a namespaced identifier for this mod.
     *
     * @param path resource path inside the {@code better_lost_items} namespace
     * @return a full Minecraft identifier for assets, menus, and payload IDs
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Registers all common-side hooks. Fabric calls this once during mod load.
     */
    @Override
    public void onInitialize() {
        LostItemsConfig.load();
        LostItemsNetworking.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> DeathLootCacheCommand.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(LostItemsStorageManager::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LostItemsFetchManager.onServerStopping();
            LostItemsTraderJourneyManager.onServerStopping();
            LostItemsStorageManager.onServerStopping(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LostItemsFetchManager.tick(server);
            LostItemsTraderJourneyManager.tick(server);
        });
    }
}
