package org.betterLostItems.better_lost_items;

import net.minecraft.server.MinecraftServer;

/**
 * Owns the per-server {@link LostItemsStorage} instance.
 *
 * <p>The mod stores data under the active world's save folder, so there must be
 * exactly one storage instance for the currently running server. This wrapper
 * centralizes lazy creation and final saving during shutdown.</p>
 */
public final class LostItemsStorageManager {
    private static LostItemsStorage storage;

    private LostItemsStorageManager() {
    }

    /**
     * Creates fresh storage when a server starts.
     *
     * @param server active dedicated or integrated Minecraft server
     */
    public static synchronized void onServerStarting(MinecraftServer server) {
        storage = new LostItemsStorage(server);
    }

    /**
     * Flushes pending storage changes and drops the server-bound reference.
     *
     * @param server server that is stopping; currently informational only
     */
    public static synchronized void onServerStopping(MinecraftServer server) {
        if (storage != null) {
            storage.saveAll();
            storage = null;
        }
    }

    /**
     * Returns the active storage, creating it if lifecycle ordering asks for it
     * before {@link #onServerStarting(MinecraftServer)} has run.
     *
     * @param server active server used to resolve the save path and registries
     * @return storage bound to the active world save
     */
    public static synchronized LostItemsStorage get(MinecraftServer server) {
        if (storage == null) {
            storage = new LostItemsStorage(server);
        }

        return storage;
    }
}
