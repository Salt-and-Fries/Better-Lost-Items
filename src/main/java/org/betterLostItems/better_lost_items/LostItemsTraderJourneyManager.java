package org.betterLostItems.better_lost_items;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the immersive wandering-trader journey wrapped around fetch recovery.
 *
 * <p>This manager has three independent duties:</p>
 *
 * <p>First, after a player respawns, it ensures a wandering trader is nearby if none already is.
 * Second, when the player pays fetch supplies, it makes the current trader walk away and despawn.
 * Third, the next Minecraft morning it spawns a returning trader and exposes the pending fetched
 * loot in the player's lost-loot list.</p>
 */
public final class LostItemsTraderJourneyManager {
    private static final int RESPAWN_TRADER_CHECK_DELAY = 20;
    private static final int RESPAWN_TRADER_RADIUS = 96;
    private static final int RETURN_SPAWN_RADIUS = 48;
    private static final int LLAMA_SPAWN_RADIUS = 4;
    private static final int DEPARTURE_TARGET_DISTANCE = 72;
    private static final double DEPARTURE_DESPAWN_DISTANCE_SQR = 48.0D * 48.0D;
    private static final int DEPARTURE_TIMEOUT_TICKS = 20 * 25;
    private static final long DAY_LENGTH = 24000L;

    private static final Map<UUID, Integer> RESPAWN_TRADER_CHECKS = new ConcurrentHashMap<>();
    private static final Map<Integer, Departure> DEPARTURES = new ConcurrentHashMap<>();

    private LostItemsTraderJourneyManager() {
    }

    /**
     * Queues a delayed nearby-trader check after a player respawns.
     */
    public static void scheduleRespawnTraderCheck(ServerPlayer player) {
        RESPAWN_TRADER_CHECKS.put(player.getUUID(), RESPAWN_TRADER_CHECK_DELAY);
    }

    /**
     * @return whether a player has a chunk scan or delayed trader return in progress
     */
    public static boolean isFetchJourneyActive(MinecraftServer server, UUID playerId) {
        if (!LostItemsConfig.isFetchEnabled()) {
            return false;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(server);
        return LostItemsFetchManager.isFetchActive(playerId)
                || storage.getFetchReturnGameTime(playerId) >= 0L;
    }

    /**
     * @return whether this trader is currently leaving after accepting fetch supplies
     */
    public static boolean isDeparting(WanderingTrader trader) {
        return DEPARTURES.containsKey(trader.getId());
    }

    /**
     * Starts the full fetch journey for one trader/player pair.
     *
     * <p>Hidden burned/fallen pools are moved to pending fetch immediately, but pending items do
     * not become visible until the scheduled morning return. This prevents players from skipping
     * the wait by opening a different wandering trader.</p>
     */
    public static boolean startFetchJourney(ServerPlayer player, int traderEntityId, boolean fetchBurned, boolean fetchFallen) {
        if (!LostItemsConfig.isFetchEnabled()) {
            return false;
        }

        fetchBurned = fetchBurned && LostItemsConfig.isBurnedFetchEnabled();
        fetchFallen = fetchFallen && LostItemsConfig.isVoidFetchEnabled();

        Entity entity = player.level().getEntity(traderEntityId);
        if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()) {
            return false;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(player.level().getServer());
        if (storage.getFetchReturnGameTime(player.getUUID()) >= 0L || LostItemsFetchManager.isFetchActive(player.getUUID())) {
            return false;
        }

        int movedHiddenStacks = 0;
        if (fetchBurned) {
            movedHiddenStacks += storage.movePlayerBurnedItemsToPendingFetch(player.getUUID());
        }
        if (fetchFallen) {
            movedHiddenStacks += storage.movePlayerFallenItemsToPendingFetch(player.getUUID());
        }

        boolean hasTrackedChunks = !storage.getTrackedDeathChunks(player.getUUID()).isEmpty();
        if (movedHiddenStacks <= 0 && !hasTrackedChunks) {
            return false;
        }

        long returnGameTime = nextMorningGameTime(player.level().getServer());
        storage.scheduleFetchReturn(player.getUUID(), returnGameTime);

        boolean startedChunkFetch = hasTrackedChunks && LostItemsFetchManager.startFetch(player, traderEntityId);
        beginDeparture(player, trader);
        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] Fetch journey started player={} trader={} returnGameTime={} hiddenStacks={} chunkFetchStarted={}",
                player.getUUID(),
                trader.getUUID(),
                returnGameTime,
                movedHiddenStacks,
                startedChunkFetch
        );
        return startedChunkFetch || movedHiddenStacks > 0;
    }

    /**
     * Advances respawn checks, departing traders, and scheduled returns by one server tick.
     */
    public static void tick(MinecraftServer server) {
        tickRespawnTraderChecks(server);
        tickDepartures(server);
        tickFetchReturns(server);
    }

    /**
     * Clears transient journey state when the server shuts down.
     */
    public static void onServerStopping() {
        RESPAWN_TRADER_CHECKS.clear();
        DEPARTURES.clear();
    }

    /**
     * Runs delayed checks that create a nearby recovery trader after player respawn.
     */
    private static void tickRespawnTraderChecks(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> iterator = RESPAWN_TRADER_CHECKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks > 0) {
                entry.setValue(remainingTicks);
                continue;
            }

            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                ensureNearbyTraderAfterRespawn(player);
            }
        }
    }

    /**
     * Updates traders that are walking away from players after fetch starts.
     */
    private static void tickDepartures(MinecraftServer server) {
        Iterator<Map.Entry<Integer, Departure>> iterator = DEPARTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Departure> entry = iterator.next();
            Departure departure = entry.getValue().tick();
            ServerLevel level = server.getLevel(departure.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (!(entity instanceof WanderingTrader trader) || !trader.isAlive()) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(departure.playerId());
            if (player != null && player.level().dimension() == departure.dimension()) {
                trader.setWanderTarget(departure.target());
                trader.setHomeTo(departure.target(), 16);
                if (trader.distanceToSqr(player) >= DEPARTURE_DESPAWN_DISTANCE_SQR || departure.ageTicks() >= DEPARTURE_TIMEOUT_TICKS) {
                    trader.discard();
                    iterator.remove();
                }
            } else if (departure.ageTicks() >= DEPARTURE_TIMEOUT_TICKS) {
                trader.discard();
                iterator.remove();
            }

            if (DEPARTURES.containsKey(entry.getKey())) {
                DEPARTURES.put(entry.getKey(), departure);
            }
        }
    }

    /**
     * Spawns returning traders and exposes pending fetch loot once morning arrives.
     */
    private static void tickFetchReturns(MinecraftServer server) {
        LostItemsStorage storage = LostItemsStorageManager.get(server);
        long currentGameTime = server.overworld().getOverworldClockTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            long returnGameTime = storage.getFetchReturnGameTime(playerId);
            if (returnGameTime < 0L || currentGameTime < returnGameTime || LostItemsFetchManager.isFetchActive(playerId)) {
                continue;
            }

            if (storage.getPlayerPendingFetchItems(playerId).isEmpty()) {
                storage.movePlayerPendingFetchItemsToLost(playerId);
                continue;
            }

            WanderingTrader trader = spawnTraderNear(player, RETURN_SPAWN_RADIUS);
            if (trader == null) {
                continue;
            }

            int movedStacks = storage.movePlayerPendingFetchItemsToLost(playerId);
            Better_lost_items.LOGGER.info(
                    "[BLI DEBUG] Fetch journey returned player={} trader={} movedStacks={}",
                    playerId,
                    trader.getUUID(),
                    movedStacks
            );
        }
    }

    /**
     * Ensures the player has a recovery trader close enough to find after death.
     */
    private static void ensureNearbyTraderAfterRespawn(ServerPlayer player) {
        if (hasNearbyTrader(player, RESPAWN_TRADER_RADIUS)) {
            return;
        }

        WanderingTrader trader = spawnTraderNear(player, RETURN_SPAWN_RADIUS);
        if (trader != null) {
            Better_lost_items.LOGGER.info(
                    "[BLI DEBUG] Spawned recovery wandering trader after respawn player={} trader={}",
                    player.getUUID(),
                    trader.getUUID()
            );
        }
    }

    /**
     * @return whether any living wandering trader is already close enough
     */
    private static boolean hasNearbyTrader(ServerPlayer player, int radius) {
        AABB bounds = player.getBoundingBox().inflate(radius);
        return player.level().hasEntities(EntityTypeTest.forClass(WanderingTrader.class), bounds, WanderingTrader::isAlive);
    }

    /**
     * Starts making a trader leave the player before fetched loot can return.
     */
    private static void beginDeparture(ServerPlayer player, WanderingTrader trader) {
        trader.setTradingPlayer(null);
        trader.setDespawnDelay(DEPARTURE_TIMEOUT_TICKS);
        BlockPos target = departureTarget(player, trader);
        trader.setWanderTarget(target);
        trader.setHomeTo(target, 16);
        DEPARTURES.put(trader.getId(), new Departure(player.getUUID(), player.level().dimension(), target, 0));
    }

    /**
     * Chooses a destination roughly away from the player.
     */
    private static BlockPos departureTarget(ServerPlayer player, WanderingTrader trader) {
        Vec3 away = trader.position().subtract(player.position());
        if (away.horizontalDistanceSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 normalized = new Vec3(away.x, 0.0D, away.z).normalize();
        BlockPos roughTarget = BlockPos.containing(trader.getX() + normalized.x * DEPARTURE_TARGET_DISTANCE, trader.getY(), trader.getZ() + normalized.z * DEPARTURE_TARGET_DISTANCE);
        return findSpawnPositionNear(player.level(), roughTarget, 8);
    }

    /**
     * Spawns a wandering trader and two llamas near the player.
     */
    private static WanderingTrader spawnTraderNear(ServerPlayer player, int radius) {
        BlockPos spawnPos = findSpawnPositionNear(player.level(), player.blockPosition(), radius);
        if (spawnPos == null) {
            return null;
        }

        WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(player.level(), spawnPos, EntitySpawnReason.EVENT);
        if (trader == null) {
            return null;
        }

        for (int index = 0; index < 2; index++) {
            spawnTraderLlama(player.level(), trader);
        }

        trader.setDespawnDelay(48000);
        trader.setWanderTarget(player.blockPosition());
        trader.setHomeTo(player.blockPosition(), 16);
        return trader;
    }

    /**
     * Spawns and leashes one trader llama near a trader.
     */
    private static void spawnTraderLlama(ServerLevel level, WanderingTrader trader) {
        BlockPos llamaPos = findSpawnPositionNear(level, trader.blockPosition(), LLAMA_SPAWN_RADIUS);
        if (llamaPos == null) {
            return;
        }

        TraderLlama llama = EntityType.TRADER_LLAMA.spawn(level, llamaPos, EntitySpawnReason.EVENT);
        if (llama != null) {
            llama.setLeashedTo(trader, true);
        }
    }

    /**
     * Finds a valid wandering-trader spawn position near a center point.
     */
    private static BlockPos findSpawnPositionNear(ServerLevel level, BlockPos center, int radius) {
        SpawnPlacementType placementType = SpawnPlacements.getPlacementType(EntityType.WANDERING_TRADER);
        Heightmap.Types heightmapType = SpawnPlacements.getHeightmapType(EntityType.WANDERING_TRADER);
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getX() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            int y = level.getHeight(heightmapType, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (placementType.isSpawnPositionOk(level, pos, EntityType.WANDERING_TRADER) && hasEnoughSpace(level, pos)) {
                return pos;
            }
        }

        ChunkPos chunkPos = new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
        int y = level.getHeight(heightmapType, center.getX(), center.getZ());
        BlockPos fallback = new BlockPos(chunkPos.getMiddleBlockX(), y, chunkPos.getMiddleBlockZ());
        return hasEnoughSpace(level, fallback) ? fallback : center;
    }

    /**
     * Checks that a trader has enough collision-free room at a candidate spawn position.
     */
    private static boolean hasEnoughSpace(ServerLevel level, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos, pos.offset(1, 2, 1))) {
            BlockState blockState = level.getBlockState(blockPos);
            if (!blockState.getCollisionShape(level, blockPos).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return overworld clock time at the start of the next Minecraft day
     */
    private static long nextMorningGameTime(MinecraftServer server) {
        long dayTime = server.overworld().getOverworldClockTime();
        return ((dayTime / DAY_LENGTH) + 1L) * DAY_LENGTH;
    }

    /**
     * Immutable departure state stored between server ticks.
     */
    private record Departure(UUID playerId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos target, int ageTicks) {
        /**
         * @return same departure with age advanced by one tick
         */
        private Departure tick() {
            return new Departure(this.playerId, this.dimension, this.target, this.ageTicks + 1);
        }
    }
}
