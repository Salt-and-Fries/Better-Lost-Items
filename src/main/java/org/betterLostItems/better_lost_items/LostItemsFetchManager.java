package org.betterLostItems.better_lost_items;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.betterLostItems.better_lost_items.mixin.MerchantMenuAccessor;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background worker that briefly loads tracked death-drop chunks and collects tagged items.
 *
 * <p>This manager is only responsible for the chunk-scanning part of fetch. The immersive trader
 * departure/return timing lives in {@link LostItemsTraderJourneyManager}. Collected stacks are
 * placed into the player's pending-fetch pool so they do not become available until the trader
 * returns the next morning.</p>
 */
public final class LostItemsFetchManager {
    private static final TicketType FETCH_TICKET_TYPE = TicketType.FORCED;
    private static final int FETCH_TICKET_RADIUS = 1;
    private static final Map<UUID, FetchJob> ACTIVE_JOBS = new ConcurrentHashMap<>();

    private LostItemsFetchManager() {
    }

    /**
     * @return whether a chunk scan is currently running for the player
     */
    public static boolean isFetchActive(UUID playerId) {
        return ACTIVE_JOBS.containsKey(playerId);
    }

    /**
     * Starts scanning all tracked chunks for one player's unloaded death drops.
     *
     * @return {@code true} when a new scan job was queued
     */
    public static boolean startFetch(ServerPlayer player, int traderEntityId) {
        if (!LostItemsConfig.isFetchEnabled()) {
            return false;
        }

        if (ACTIVE_JOBS.containsKey(player.getUUID())) {
            return false;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        ArrayDeque<TrackedDeathChunk> pendingChunks = new ArrayDeque<>(new LinkedHashSet<>(storage.getTrackedDeathChunks(player.getUUID())));
        if (pendingChunks.isEmpty()) {
            return false;
        }

        ACTIVE_JOBS.put(player.getUUID(), new FetchJob(player.getUUID(), traderEntityId, pendingChunks));
        return true;
    }

    /**
     * Advances all active chunk-scan jobs by one server tick.
     */
    public static void tick(MinecraftServer server) {
        if (ACTIVE_JOBS.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, FetchJob>> iterator = ACTIVE_JOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FetchJob> entry = iterator.next();
            if (entry.getValue().tick(server)) {
                iterator.remove();
            }
        }
    }

    /**
     * Clears in-memory jobs on shutdown. Persisted tracked chunks remain in player storage.
     */
    public static void onServerStopping() {
        ACTIVE_JOBS.clear();
    }

    /**
     * Stateful scanner for one player's fetch request.
     *
     * <p>The job processes one chunk at a time: add a forced ticket, wait until entities are
     * loaded, transfer matching item entities into pending storage, then release the ticket.</p>
     */
    private static final class FetchJob {
        private final UUID playerId;
        private final int traderEntityId;
        private final ArrayDeque<TrackedDeathChunk> pendingChunks;
        private TrackedDeathChunk activeChunk;
        private int scannedChunks;
        private int collectedStacks;

        private FetchJob(UUID playerId, int traderEntityId, ArrayDeque<TrackedDeathChunk> pendingChunks) {
            this.playerId = playerId;
            this.traderEntityId = traderEntityId;
            this.pendingChunks = pendingChunks;
        }

        /**
         * @return {@code true} when the job is complete and can be removed
         */
        private boolean tick(MinecraftServer server) {
            ServerPlayer player = server.getPlayerList().getPlayer(this.playerId);
            if (player == null) {
                this.cleanup(server);
                return true;
            }

            if (this.activeChunk == null) {
                if (this.pendingChunks.isEmpty()) {
                    this.finish(player);
                    return true;
                }

                this.beginLoading(server);
                this.refreshOpenView(player);
                return false;
            }

            ServerLevel level = server.getLevel(this.activeChunk.dimension());
            if (level == null) {
                this.dropActiveChunk(server);
                this.refreshOpenView(player);
                return false;
            }

            if (!level.areEntitiesLoaded(this.activeChunk.toLong())) {
                return false;
            }

            this.scanActiveChunk(player, level);
            this.refreshOpenView(player);
            return false;
        }

        /**
         * Adds a chunk ticket for the next tracked chunk.
         */
        private void beginLoading(MinecraftServer server) {
            this.activeChunk = this.pendingChunks.removeFirst();
            ServerLevel level = server.getLevel(this.activeChunk.dimension());
            if (level == null) {
                this.dropActiveChunk(server);
                return;
            }

            ChunkPos chunkPos = this.activeChunk.chunkPos();
            Better_lost_items.LOGGER.info(
                    "[BLI DEBUG] Fetch starting player={} dimension={} chunk={},{} remainingAfterStart={}",
                    this.playerId,
                    this.activeChunk.dimension().location(),
                    chunkPos.x,
                    chunkPos.z,
                    this.pendingChunks.size()
            );
            level.getChunkSource().addRegionTicket(FETCH_TICKET_TYPE, chunkPos, FETCH_TICKET_RADIUS, chunkPos);
        }

        /**
         * Collects tagged item entities from the active loaded chunk.
         */
        private void scanActiveChunk(ServerPlayer player, ServerLevel level) {
            ChunkPos chunkPos = this.activeChunk.chunkPos();
            AABB bounds = new AABB(
                    chunkPos.getMinBlockX(),
                    level.getMinBuildHeight(),
                    chunkPos.getMinBlockZ(),
                    chunkPos.getMaxBlockX() + 1,
                    level.getMaxBuildHeight(),
                    chunkPos.getMaxBlockZ() + 1
            );

            LostItemsStorage storage = LostItemsStorageManager.get(level.getServer());
            int movedStacks = 0;
            for (ItemEntity itemEntity : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), bounds,
                    item -> this.playerId.equals(((TrackedItemEntity) item).betterLostItems$getOwnerId()))) {
                if (itemEntity.getItem().isEmpty()) {
                    continue;
                }

                storage.addPlayerPendingFetchItem(this.playerId, itemEntity.getItem());
                itemEntity.discard();
                movedStacks++;
            }

            // Once scanned, the chunk marker is removed even if it contained no matching items.
            storage.removeTrackedDeathChunk(this.playerId, this.activeChunk);
            level.getChunkSource().removeRegionTicket(FETCH_TICKET_TYPE, chunkPos, FETCH_TICKET_RADIUS, chunkPos);

            this.scannedChunks++;
            this.collectedStacks += movedStacks;

            Better_lost_items.LOGGER.info(
                    "[BLI DEBUG] Fetch scanned player={} dimension={} chunk={},{} collectedStacksInChunk={} totalCollectedStacks={} remainingChunks={}",
                    this.playerId,
                    this.activeChunk.dimension().location(),
                    chunkPos.x,
                    chunkPos.z,
                    movedStacks,
                    this.collectedStacks,
                    this.pendingChunks.size()
            );

            this.activeChunk = null;
        }

        /**
         * Releases and forgets the active chunk when the dimension is unavailable.
         */
        private void dropActiveChunk(MinecraftServer server) {
            if (this.activeChunk == null) {
                return;
            }

            LostItemsStorageManager.get(server).removeTrackedDeathChunk(this.playerId, this.activeChunk);

            ServerLevel level = server.getLevel(this.activeChunk.dimension());
            if (level != null) {
                level.getChunkSource().removeRegionTicket(FETCH_TICKET_TYPE, this.activeChunk.chunkPos(), FETCH_TICKET_RADIUS, this.activeChunk.chunkPos());
            }

            this.activeChunk = null;
        }

        /**
         * Releases any chunk ticket held by an interrupted job.
         */
        private void cleanup(MinecraftServer server) {
            if (this.activeChunk != null) {
                ServerLevel level = server.getLevel(this.activeChunk.dimension());
                if (level != null) {
                    level.getChunkSource().removeRegionTicket(FETCH_TICKET_TYPE, this.activeChunk.chunkPos(), FETCH_TICKET_RADIUS, this.activeChunk.chunkPos());
                }
            }
        }

        /**
         * Final refresh hook after every tracked chunk has been processed.
         */
        private void finish(ServerPlayer player) {
            this.refreshOpenView(player);
        }

        /**
         * Keeps the currently open UI in sync while fetch state changes.
         */
        private void refreshOpenView(ServerPlayer player) {
            if (player.containerMenu instanceof LostItemsRecoveryMenu recoveryMenu
                    && recoveryMenu.getTraderEntityId() == this.traderEntityId) {
                recoveryMenu.refreshFromServer();
                return;
            }

            if (player.containerMenu instanceof MerchantMenu merchantMenu) {
                if (((MerchantMenuAccessor) merchantMenu).betterLostItems$getTrader() instanceof WanderingTrader trader
                        && trader.getId() == this.traderEntityId) {
                    LostItemsTradeController.sendCurrentTabState(player, trader);
                }
            }
        }
    }
}
