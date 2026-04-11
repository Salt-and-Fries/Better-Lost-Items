package org.betterLostItems.better_lost_items;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * World-save backed storage for every lost-item pool used by the mod.
 *
 * <p>There are two broad storage families:</p>
 *
 * <p>Unclaimed items live in one server-wide file and are used by the public wandering-trader
 * market. These entries are merged by stack identity so a pile of identical despawned items does
 * not flood trader offers.</p>
 *
 * <p>Player recovery data lives in one file per player. Those lists intentionally preserve stack
 * boundaries because death loot should show every stack the player lost, including multiple full
 * stacks of the same item.</p>
 */
public final class LostItemsStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final MinecraftServer server;
    private final Path unclaimedItemsFile;
    private final Path playerDirectory;
    private final List<LostItemEntry> unclaimedItems;
    private final Map<UUID, PlayerRecoveryData> loadedPlayerItems = new HashMap<>();

    /**
     * Creates storage rooted in the active world's save directory.
     *
     * @param server active Minecraft server
     */
    public LostItemsStorage(MinecraftServer server) {
        this.server = server;
        Path rootDirectory = server.getWorldPath(LevelResource.ROOT).resolve("better_lost_items");
        this.unclaimedItemsFile = rootDirectory.resolve("unclaimed_items.json");
        this.playerDirectory = rootDirectory.resolve("players");
        this.unclaimedItems = new ArrayList<>(this.loadEntries(this.unclaimedItemsFile));
    }

    /**
     * Saves all currently loaded data to disk.
     */
    public synchronized void saveAll() {
        this.saveEntries(this.unclaimedItemsFile, this.unclaimedItems);
        for (Map.Entry<UUID, PlayerRecoveryData> entry : this.loadedPlayerItems.entrySet()) {
            this.savePlayerEntries(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Adds an unlabeled despawned stack to the public market pool.
     *
     * <p>Unclaimed storage merges compatible stacks immediately so trader selections stay varied.</p>
     *
     * @param stack stack to copy into storage
     */
    public synchronized void addUnclaimed(ItemStack stack) {
        Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addUnclaimed stack={}", LostItemsDebug.stack(stack));
        this.mergeEntry(this.unclaimedItems, stack);
        this.saveEntries(this.unclaimedItemsFile, this.unclaimedItems);
    }

    /**
     * Adds a normal recoverable death-loot stack for a player.
     */
    public synchronized void addPlayerLostItem(UUID playerId, ItemStack stack) {
        Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addPlayerLostItem player={} stack={}", playerId, LostItemsDebug.stack(stack));
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        this.appendEntry(data.lostItems(), stack);
        this.savePlayerEntries(playerId, data);
    }

    /**
     * Adds a death-loot stack destroyed by fire or lava.
     *
     * <p>Burned stacks stay hidden until the player starts a fetch journey with the configured
     * burned-loot supply.</p>
     */
    public synchronized void addPlayerBurnedItem(UUID playerId, ItemStack stack) {
        Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addPlayerBurnedItem player={} stack={}", playerId, LostItemsDebug.stack(stack));
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        this.appendEntry(data.burnedItems(), stack);
        this.savePlayerEntries(playerId, data);
    }

    /**
     * Adds a death-loot stack deleted by the void.
     */
    public synchronized void addPlayerFallenItem(UUID playerId, ItemStack stack) {
        Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addPlayerFallenItem player={} stack={}", playerId, LostItemsDebug.stack(stack));
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        this.appendEntry(data.fallenItems(), stack);
        this.savePlayerEntries(playerId, data);
    }

    /**
     * Adds several stacks to a player's visible lost-loot list.
     *
     * @return number of non-empty stacks copied into storage
     */
    public synchronized int addPlayerLostItems(UUID playerId, List<ItemStack> stacks) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        int addedCount = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addPlayerLostItems player={} stack={}", playerId, LostItemsDebug.stack(stack));
            this.appendEntry(data.lostItems(), stack);
            addedCount++;
        }

        if (addedCount > 0) {
            this.savePlayerEntries(playerId, data);
        }

        return addedCount;
    }

    /**
     * Removes one unclaimed market entry by ID.
     */
    public synchronized boolean removeUnclaimed(UUID entryId) {
        boolean removed = this.unclaimedItems.removeIf(entry -> entry.id().equals(entryId));
        if (removed) {
            this.saveEntries(this.unclaimedItemsFile, this.unclaimedItems);
        }
        return removed;
    }

    /**
     * Removes part or all of an unclaimed market entry after a trade completes.
     */
    public synchronized boolean removeUnclaimedAmount(UUID entryId, int amount) {
        if (amount <= 0) {
            return false;
        }

        for (int index = 0; index < this.unclaimedItems.size(); index++) {
            LostItemEntry entry = this.unclaimedItems.get(index);
            if (!entry.id().equals(entryId)) {
                continue;
            }

            ItemStack remaining = entry.stack().copy();
            remaining.shrink(amount);
            if (remaining.isEmpty()) {
                this.unclaimedItems.remove(index);
            } else {
                this.unclaimedItems.set(index, new LostItemEntry(entry.id(), remaining));
            }

            this.saveEntries(this.unclaimedItemsFile, this.unclaimedItems);
            return true;
        }

        return false;
    }

    /**
     * Removes one visible lost-loot entry from a player's cache.
     */
    public synchronized boolean removePlayerLostItem(UUID playerId, UUID entryId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        boolean removed = data.lostItems().removeIf(entry -> entry.id().equals(entryId));
        if (removed) {
            this.savePlayerEntries(playerId, data);
        }
        return removed;
    }

    /**
     * Removes one retrieved-loot entry after the player takes it from the right grid.
     */
    public synchronized boolean removePlayerPurchasedItem(UUID playerId, UUID entryId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        boolean removed = data.purchasedItems().removeIf(entry -> entry.id().equals(entryId));
        if (removed) {
            this.savePlayerEntries(playerId, data);
        }
        return removed;
    }

    /**
     * Moves all visible lost loot into the retrieved-loot pool after payment.
     */
    public synchronized boolean movePlayerLostItemsToPurchased(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        if (data.lostItems().isEmpty()) {
            Better_lost_items.LOGGER.info("[BLI DEBUG] movePlayerLostItemsToPurchased player={} skipped because lost list is empty", playerId);
            return false;
        }

        Better_lost_items.LOGGER.info("[BLI DEBUG] movePlayerLostItemsToPurchased player={} lostCount={} purchasedCountBefore={}", playerId, data.lostItems().size(), data.purchasedItems().size());
        data.purchasedItems().addAll(this.copyEntries(data.lostItems()));
        data.lostItems().clear();
        this.savePlayerEntries(playerId, data);
        Better_lost_items.LOGGER.info("[BLI DEBUG] movePlayerLostItemsToPurchased player={} purchasedCountAfter={}", playerId, data.purchasedItems().size());
        return true;
    }

    /**
     * Immediately exposes burned loot as visible lost loot.
     *
     * @return number of entries moved
     */
    public synchronized int movePlayerBurnedItemsToLost(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        if (data.burnedItems().isEmpty()) {
            return 0;
        }

        int movedCount = data.burnedItems().size();
        data.lostItems().addAll(this.copyEntries(data.burnedItems()));
        data.burnedItems().clear();
        this.savePlayerEntries(playerId, data);
        return movedCount;
    }

    /**
     * Moves burned loot into the delayed fetch-return bucket.
     */
    public synchronized int movePlayerBurnedItemsToPendingFetch(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        if (data.burnedItems().isEmpty()) {
            return 0;
        }

        int movedCount = data.burnedItems().size();
        data.pendingFetchItems().addAll(this.copyEntries(data.burnedItems()));
        data.burnedItems().clear();
        this.savePlayerEntries(playerId, data);
        return movedCount;
    }

    /**
     * Immediately exposes void/fallen loot as visible lost loot.
     */
    public synchronized int movePlayerFallenItemsToLost(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        if (data.fallenItems().isEmpty()) {
            return 0;
        }

        int movedCount = data.fallenItems().size();
        data.lostItems().addAll(this.copyEntries(data.fallenItems()));
        data.fallenItems().clear();
        this.savePlayerEntries(playerId, data);
        return movedCount;
    }

    /**
     * Moves void/fallen loot into the delayed fetch-return bucket.
     */
    public synchronized int movePlayerFallenItemsToPendingFetch(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        if (data.fallenItems().isEmpty()) {
            return 0;
        }

        int movedCount = data.fallenItems().size();
        data.pendingFetchItems().addAll(this.copyEntries(data.fallenItems()));
        data.fallenItems().clear();
        this.savePlayerEntries(playerId, data);
        return movedCount;
    }

    /**
     * Adds one stack collected from an unloaded tracked chunk during a fetch journey.
     */
    public synchronized void addPlayerPendingFetchItem(UUID playerId, ItemStack stack) {
        Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addPlayerPendingFetchItem player={} stack={}", playerId, LostItemsDebug.stack(stack));
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        this.appendEntry(data.pendingFetchItems(), stack);
        this.savePlayerEntries(playerId, data);
    }

    /**
     * Moves all delayed fetch loot into visible lost loot and clears the scheduled return time.
     */
    public synchronized int movePlayerPendingFetchItemsToLost(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        int movedCount = data.pendingFetchItems().size();
        if (movedCount > 0) {
            data.lostItems().addAll(this.copyEntries(data.pendingFetchItems()));
            data.pendingFetchItems().clear();
        }

        PlayerRecoveryData updated = this.withFetchReturnGameTime(data, -1L);
        this.loadedPlayerItems.put(playerId, updated);
        this.savePlayerEntries(playerId, updated);
        return movedCount;
    }

    /**
     * @return defensive copies of every unclaimed market entry
     */
    public synchronized List<LostItemEntry> getUnclaimedItems() {
        return this.copyEntries(this.unclaimedItems);
    }

    /**
     * @return defensive copies of visible lost loot for one player
     */
    public synchronized List<LostItemEntry> getPlayerLostItems(UUID playerId) {
        return this.copyEntries(this.ensurePlayerEntries(playerId).lostItems());
    }

    /**
     * @return defensive copies of retrieved loot for one player
     */
    public synchronized List<LostItemEntry> getPlayerPurchasedItems(UUID playerId) {
        return this.copyEntries(this.ensurePlayerEntries(playerId).purchasedItems());
    }

    /**
     * @return defensive copies of hidden burned loot for one player
     */
    public synchronized List<LostItemEntry> getPlayerBurnedItems(UUID playerId) {
        return this.copyEntries(this.ensurePlayerEntries(playerId).burnedItems());
    }

    /**
     * @return defensive copies of hidden void/fallen loot for one player
     */
    public synchronized List<LostItemEntry> getPlayerFallenItems(UUID playerId) {
        return this.copyEntries(this.ensurePlayerEntries(playerId).fallenItems());
    }

    /**
     * @return defensive copies of delayed fetch-return loot for one player
     */
    public synchronized List<LostItemEntry> getPlayerPendingFetchItems(UUID playerId) {
        return this.copyEntries(this.ensurePlayerEntries(playerId).pendingFetchItems());
    }

    /**
     * @return scheduled overworld return time for a fetch journey, or {@code -1}
     */
    public synchronized long getFetchReturnGameTime(UUID playerId) {
        return this.ensurePlayerEntries(playerId).fetchReturnGameTime();
    }

    /**
     * Stores when a departing wandering trader should return with fetched loot.
     */
    public synchronized void scheduleFetchReturn(UUID playerId, long returnGameTime) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        PlayerRecoveryData updated = this.withFetchReturnGameTime(data, returnGameTime);
        this.loadedPlayerItems.put(playerId, updated);
        this.savePlayerEntries(playerId, updated);
    }

    /**
     * @return chunks that may still contain unloaded tagged death drops
     */
    public synchronized List<TrackedDeathChunk> getTrackedDeathChunks(UUID playerId) {
        return this.copyTrackedChunks(this.ensurePlayerEntries(playerId).trackedChunks());
    }

    /**
     * Records a chunk containing at least one tagged death-drop item entity.
     *
     * @return {@code true} when the chunk was newly added
     */
    public synchronized boolean addTrackedDeathChunk(UUID playerId, ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        TrackedDeathChunk trackedChunk = new TrackedDeathChunk(dimension, chunkX, chunkZ);
        if (data.trackedChunks().contains(trackedChunk)) {
            return false;
        }

        Better_lost_items.LOGGER.info("[BLI DEBUG] Storage addTrackedDeathChunk player={} dimension={} chunk={},{}", playerId, dimension.identifier(), chunkX, chunkZ);
        data.trackedChunks().add(trackedChunk);
        this.savePlayerEntries(playerId, data);
        return true;
    }

    /**
     * Removes a tracked chunk after it has been scanned or no longer contains owned items.
     */
    public synchronized boolean removeTrackedDeathChunk(UUID playerId, TrackedDeathChunk trackedChunk) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        boolean removed = data.trackedChunks().remove(trackedChunk);
        if (removed) {
            Better_lost_items.LOGGER.info(
                    "[BLI DEBUG] Storage removeTrackedDeathChunk player={} dimension={} chunk={},{}",
                    playerId,
                    trackedChunk.dimension().identifier(),
                    trackedChunk.chunkX(),
                    trackedChunk.chunkZ()
            );
            this.savePlayerEntries(playerId, data);
        }
        return removed;
    }

    /**
     * Returns unclaimed entries in the exact order requested by a trader's locked selection.
     */
    public synchronized List<LostItemEntry> getUnclaimedItems(List<UUID> entryIds) {
        Map<UUID, LostItemEntry> byId = new LinkedHashMap<>();
        for (LostItemEntry entry : this.unclaimedItems) {
            byId.put(entry.id(), entry.copy());
        }

        List<LostItemEntry> orderedEntries = new ArrayList<>();
        for (UUID entryId : entryIds) {
            LostItemEntry entry = byId.get(entryId);
            if (entry != null) {
                orderedEntries.add(entry.copy());
            }
        }
        return orderedEntries;
    }

    /**
     * Finds one unclaimed market entry by ID.
     */
    public synchronized Optional<LostItemEntry> getUnclaimedItem(UUID entryId) {
        return this.findEntry(this.unclaimedItems, entryId);
    }

    /**
     * Finds one visible lost-loot entry by ID.
     */
    public synchronized Optional<LostItemEntry> getPlayerLostItem(UUID playerId, UUID entryId) {
        return this.findEntry(this.ensurePlayerEntries(playerId).lostItems(), entryId);
    }

    /**
     * Finds one retrieved-loot entry by ID.
     */
    public synchronized Optional<LostItemEntry> getPlayerPurchasedItem(UUID playerId, UUID entryId) {
        return this.findEntry(this.ensurePlayerEntries(playerId).purchasedItems(), entryId);
    }

    /**
     * Replaces or removes a retrieved-loot entry after partial pickup.
     */
    public synchronized boolean replacePlayerPurchasedItem(UUID playerId, UUID entryId, ItemStack replacement) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        for (int index = 0; index < data.purchasedItems().size(); index++) {
            LostItemEntry existing = data.purchasedItems().get(index);
            if (!existing.id().equals(entryId)) {
                continue;
            }

            if (replacement.isEmpty()) {
                data.purchasedItems().remove(index);
            } else {
                data.purchasedItems().set(index, new LostItemEntry(existing.id(), replacement.copy()));
            }

            this.savePlayerEntries(playerId, data);
            return true;
        }

        return false;
    }

    /**
     * Clears all cached recovery state for a player.
     *
     * @return number of item entries removed, excluding tracked chunk markers
     */
    public synchronized int clearPlayerRecoveryCache(UUID playerId) {
        PlayerRecoveryData data = this.ensurePlayerEntries(playerId);
        int removedCount = data.lostItems().size()
                + data.purchasedItems().size()
                + data.burnedItems().size()
                + data.fallenItems().size()
                + data.pendingFetchItems().size();
        if (removedCount <= 0 && data.trackedChunks().isEmpty() && data.fetchReturnGameTime() < 0L) {
            return 0;
        }

        data.lostItems().clear();
        data.purchasedItems().clear();
        data.burnedItems().clear();
        data.fallenItems().clear();
        data.pendingFetchItems().clear();
        data.trackedChunks().clear();
        data = this.withFetchReturnGameTime(data, -1L);
        this.loadedPlayerItems.put(playerId, data);
        this.savePlayerEntries(playerId, data);
        return removedCount;
    }

    private PlayerRecoveryData ensurePlayerEntries(UUID playerId) {
        return this.loadedPlayerItems.computeIfAbsent(playerId, id -> this.loadPlayerData(this.playerFile(id)));
    }

    private Path playerFile(UUID playerId) {
        return this.playerDirectory.resolve(playerId + ".json");
    }

    private void savePlayerEntries(UUID playerId, PlayerRecoveryData data) {
        Path file = this.playerFile(playerId);
        if (data.lostItems().isEmpty()
                && data.purchasedItems().isEmpty()
                && data.burnedItems().isEmpty()
                && data.fallenItems().isEmpty()
                && data.pendingFetchItems().isEmpty()
                && data.trackedChunks().isEmpty()) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                Better_lost_items.LOGGER.error("Failed deleting empty lost-items file {}", file, exception);
            }
            return;
        }

        this.savePlayerData(file, data);
    }

    private void saveEntries(Path file, List<LostItemEntry> entries) {
        try {
            Files.createDirectories(file.getParent());
            // Unclaimed items are a public market pool, so they are compacted before writing.
            JsonElement encoded = LostItemEntry.CODEC.listOf()
                    .encodeStart(this.ops(), this.normalizeMergedEntries(entries))
                    .resultOrPartial(message -> Better_lost_items.LOGGER.error("Failed encoding lost-items data for {}: {}", file, message))
                    .orElse(null);
            if (encoded == null) {
                return;
            }

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(encoded, writer);
            }
        } catch (IOException exception) {
            Better_lost_items.LOGGER.error("Failed saving lost-items data to {}", file, exception);
        }
    }

    private void savePlayerData(Path file, PlayerRecoveryData data) {
        try {
            Files.createDirectories(file.getParent());
            // Player lists preserve individual stack boundaries for accurate death recovery.
            PlayerRecoveryData normalized = new PlayerRecoveryData(
                    this.normalizePreservedEntries(data.lostItems()),
                    this.normalizePreservedEntries(data.purchasedItems()),
                    this.normalizePreservedEntries(data.burnedItems()),
                    this.normalizePreservedEntries(data.fallenItems()),
                    this.normalizePreservedEntries(data.pendingFetchItems()),
                    this.normalizeTrackedChunks(data.trackedChunks()),
                    data.fetchReturnGameTime()
            );
            JsonElement encoded = PlayerRecoveryData.CODEC
                    .encodeStart(this.ops(), normalized)
                    .resultOrPartial(message -> Better_lost_items.LOGGER.error("Failed encoding player lost-items data for {}: {}", file, message))
                    .orElse(null);
            if (encoded == null) {
                return;
            }

            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(encoded, writer);
            }
        } catch (IOException exception) {
            Better_lost_items.LOGGER.error("Failed saving player lost-items data to {}", file, exception);
        }
    }

    private List<LostItemEntry> loadEntries(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            Optional<List<LostItemEntry>> decoded = LostItemEntry.CODEC.listOf()
                    .parse(this.ops(), json)
                    .resultOrPartial(message -> Better_lost_items.LOGGER.error("Failed reading lost-items data from {}: {}", file, message));
            return decoded.map(this::normalizeMergedEntries).orElseGet(List::of);
        } catch (IOException exception) {
            Better_lost_items.LOGGER.error("Failed loading lost-items data from {}", file, exception);
            return List.of();
        }
    }

    private PlayerRecoveryData loadPlayerData(Path file) {
        if (!Files.exists(file)) {
            return this.emptyPlayerRecoveryData();
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            Optional<PlayerRecoveryData> decoded = PlayerRecoveryData.CODEC
                    .parse(this.ops(), json)
                    .resultOrPartial(message -> Better_lost_items.LOGGER.error("Failed reading player lost-items data from {}: {}", file, message));
            if (decoded.isPresent()) {
                PlayerRecoveryData data = decoded.get().copy();
                return new PlayerRecoveryData(
                        new ArrayList<>(this.normalizePreservedEntries(data.lostItems())),
                        new ArrayList<>(this.normalizePreservedEntries(data.purchasedItems())),
                        new ArrayList<>(this.normalizePreservedEntries(data.burnedItems())),
                        new ArrayList<>(this.normalizePreservedEntries(data.fallenItems())),
                        new ArrayList<>(this.normalizePreservedEntries(data.pendingFetchItems())),
                        new ArrayList<>(this.normalizeTrackedChunks(data.trackedChunks())),
                        data.fetchReturnGameTime()
                );
            }

            Optional<List<LostItemEntry>> legacy = LostItemEntry.CODEC.listOf()
                    .parse(this.ops(), json)
                    .resultOrPartial(message -> Better_lost_items.LOGGER.error("Failed reading legacy player lost-items data from {}: {}", file, message));
            // Early builds stored player files as a bare lost-item list. Keep loading those worlds.
            return legacy
                    .map(entries -> new PlayerRecoveryData(new ArrayList<>(this.normalizePreservedEntries(entries)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), -1L))
                    .orElseGet(this::emptyPlayerRecoveryData);
        } catch (IOException exception) {
            Better_lost_items.LOGGER.error("Failed loading player lost-items data from {}", file, exception);
            return this.emptyPlayerRecoveryData();
        }
    }

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.create(JsonOps.INSTANCE, this.server.registryAccess());
    }

    private PlayerRecoveryData emptyPlayerRecoveryData() {
        return new PlayerRecoveryData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), -1L);
    }

    private PlayerRecoveryData withFetchReturnGameTime(PlayerRecoveryData data, long fetchReturnGameTime) {
        return new PlayerRecoveryData(
                data.lostItems(),
                data.purchasedItems(),
                data.burnedItems(),
                data.fallenItems(),
                data.pendingFetchItems(),
                data.trackedChunks(),
                fetchReturnGameTime
        );
    }

    private List<LostItemEntry> copyEntries(List<LostItemEntry> entries) {
        return entries.stream().map(LostItemEntry::copy).toList();
    }

    private List<TrackedDeathChunk> copyTrackedChunks(List<TrackedDeathChunk> trackedChunks) {
        return trackedChunks.stream().map(TrackedDeathChunk::copy).toList();
    }

    private Optional<LostItemEntry> findEntry(List<LostItemEntry> entries, UUID entryId) {
        return entries.stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst()
                .map(LostItemEntry::copy);
    }

    private List<LostItemEntry> normalizeMergedEntries(List<LostItemEntry> entries) {
        List<LostItemEntry> normalized = new ArrayList<>();
        for (LostItemEntry entry : entries) {
            if (entry.stack().isEmpty()) {
                continue;
            }

            this.mergeEntry(normalized, entry.id(), entry.stack());
        }
        return this.copyEntries(normalized);
    }

    private List<LostItemEntry> normalizePreservedEntries(List<LostItemEntry> entries) {
        List<LostItemEntry> normalized = new ArrayList<>();
        for (LostItemEntry entry : entries) {
            if (entry.stack().isEmpty()) {
                continue;
            }

            this.appendPreservedEntry(normalized, entry.id(), entry.stack());
        }
        return this.copyEntries(normalized);
    }

    private List<TrackedDeathChunk> normalizeTrackedChunks(List<TrackedDeathChunk> trackedChunks) {
        return new ArrayList<>(new LinkedHashSet<>(this.copyTrackedChunks(trackedChunks)));
    }

    private void appendEntry(List<LostItemEntry> entries, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        this.appendPreservedEntry(entries, UUID.randomUUID(), stack);
    }

    private void appendPreservedEntry(List<LostItemEntry> entries, UUID fallbackId, ItemStack stack) {
        ItemStack remaining = stack.copy();
        boolean usedFallbackId = false;
        while (!remaining.isEmpty()) {
            int maxStackSize = Math.max(1, remaining.getMaxStackSize());
            // Mojang's ItemStack codec rejects counts above vanilla max stack size.
            ItemStack entryStack = remaining.split(Math.min(maxStackSize, remaining.getCount()));
            entries.add(new LostItemEntry(usedFallbackId ? UUID.randomUUID() : fallbackId, entryStack));
            usedFallbackId = true;
        }
    }

    private void mergeEntry(List<LostItemEntry> entries, ItemStack stack) {
        this.mergeEntry(entries, UUID.randomUUID(), stack);
    }

    private void mergeEntry(List<LostItemEntry> entries, UUID fallbackId, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        ItemStack remaining = stack.copy();
        boolean usedFallbackId = false;
        while (!remaining.isEmpty()) {
            int maxStackSize = Math.max(1, remaining.getMaxStackSize());
            ItemStack incoming = remaining.split(Math.min(maxStackSize, remaining.getCount()));
            UUID entryId = usedFallbackId ? UUID.randomUUID() : fallbackId;
            usedFallbackId = true;
            this.mergeSingleStackEntry(entries, entryId, incoming);
        }
    }

    private void mergeSingleStackEntry(List<LostItemEntry> entries, UUID fallbackId, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return;
        }

        int maxStackSize = Math.max(1, incoming.getMaxStackSize());
        if (incoming.isStackable()) {
            for (int index = 0; index < entries.size(); index++) {
                LostItemEntry existing = entries.get(index);
                if (ItemStack.isSameItemSameComponents(existing.stack(), incoming)) {
                    int availableSpace = maxStackSize - existing.stack().getCount();
                    if (availableSpace <= 0) {
                        continue;
                    }

                    ItemStack mergedStack = existing.stack().copy();
                    int movedCount = Math.min(availableSpace, incoming.getCount());
                    mergedStack.grow(movedCount);
                    entries.set(index, new LostItemEntry(existing.id(), mergedStack));
                    incoming.shrink(movedCount);
                    if (incoming.isEmpty()) {
                        return;
                    }
                }
            }
        }

        entries.add(new LostItemEntry(fallbackId, incoming));
    }
}
