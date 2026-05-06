package org.betterLostItems.better_lost_items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main NeoForge entry point for Better Lost Items.
 *
 * <p>This class intentionally stays small: it registers the shared menu type,
 * networking payloads, commands, storage lifecycle hooks, and server tick
 * workers. Most gameplay behavior lives in the controller/manager classes so
 * new features do not have to be squeezed into the mod initializer.</p>
 */
@Mod(Better_lost_items.MOD_ID)
public class Better_lost_items {
    public static final String MOD_ID = "better_lost_items";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(BuiltInRegistries.MENU, MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<LostItemsRecoveryMenu>> LOST_ITEMS_RECOVERY_MENU = MENU_TYPES.register(
            "lost_items_recovery",
            () -> new MenuType<>(LostItemsRecoveryMenu::new, FeatureFlags.VANILLA_SET)
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
     * Registers all common-side hooks. NeoForge calls this once during mod load.
     */
    public Better_lost_items(IEventBus modEventBus, ModContainer modContainer) {
        MENU_TYPES.register(modEventBus);
        modEventBus.addListener(LostItemsNetworking::registerPayloads);
        LostItemsConfig.load();

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            org.betterLostItems.better_lost_items.client.Better_lost_itemsClient.register(modEventBus, modContainer);
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        DeathLootCacheCommand.register(event.getDispatcher());
    }

    private void onServerStarting(ServerStartingEvent event) {
        LostItemsStorageManager.onServerStarting(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LostItemsFetchManager.onServerStopping();
        LostItemsTraderJourneyManager.onServerStopping();
        LostItemsStorageManager.onServerStopping(event.getServer());
    }

    private void onServerTick(ServerTickEvent.Post event) {
        LostItemsFetchManager.tick(event.getServer());
        LostItemsTraderJourneyManager.tick(event.getServer());
    }
}
