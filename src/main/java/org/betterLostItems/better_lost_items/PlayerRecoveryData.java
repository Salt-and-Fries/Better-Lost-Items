package org.betterLostItems.better_lost_items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * All recoverable state for one player.
 *
 * <p>This record is serialized as a JSON file under
 * {@code world/better_lost_items/players/<uuid>.json}. Lists are kept separate
 * so the UI can distinguish immediately purchasable lost loot, already
 * purchased/retrieved loot, hidden burned/void loot, and fetch-in-progress loot.</p>
 *
 * @param lostItems items visible in the left "Lost Loot" panel
 * @param purchasedItems items visible in the right "Retrieved Loot" panel
 * @param burnedItems death items destroyed by fire/lava and gated behind fetch supplies
 * @param fallenItems death items deleted by the void and gated behind fetch supplies
 * @param pendingFetchItems items already collected by a fetch journey but not returned until morning
 * @param trackedChunks chunks that may contain unloaded death drops
 * @param fetchReturnGameTime overworld time when the trader should return, or {@code -1}
 */
public record PlayerRecoveryData(
        List<LostItemEntry> lostItems,
        List<LostItemEntry> purchasedItems,
        List<LostItemEntry> burnedItems,
        List<LostItemEntry> fallenItems,
        List<LostItemEntry> pendingFetchItems,
        List<TrackedDeathChunk> trackedChunks,
        long fetchReturnGameTime
) {
    public static final Codec<PlayerRecoveryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LostItemEntry.CODEC.listOf().optionalFieldOf("lost_items", List.of()).forGetter(PlayerRecoveryData::lostItems),
            LostItemEntry.CODEC.listOf().optionalFieldOf("purchased_items", List.of()).forGetter(PlayerRecoveryData::purchasedItems),
            LostItemEntry.CODEC.listOf().optionalFieldOf("burned_items", List.of()).forGetter(PlayerRecoveryData::burnedItems),
            LostItemEntry.CODEC.listOf().optionalFieldOf("fallen_items", List.of()).forGetter(PlayerRecoveryData::fallenItems),
            LostItemEntry.CODEC.listOf().optionalFieldOf("pending_fetch_items", List.of()).forGetter(PlayerRecoveryData::pendingFetchItems),
            TrackedDeathChunk.CODEC.listOf().optionalFieldOf("tracked_chunks", List.of()).forGetter(PlayerRecoveryData::trackedChunks),
            Codec.LONG.optionalFieldOf("fetch_return_game_time", -1L).forGetter(PlayerRecoveryData::fetchReturnGameTime)
    ).apply(instance, PlayerRecoveryData::new));

    /**
     * Creates a deep copy of every mutable list and stack.
     *
     * @return isolated copy safe to normalize or save without mutating callers
     */
    public PlayerRecoveryData copy() {
        return new PlayerRecoveryData(
                this.lostItems.stream().map(LostItemEntry::copy).toList(),
                this.purchasedItems.stream().map(LostItemEntry::copy).toList(),
                this.burnedItems.stream().map(LostItemEntry::copy).toList(),
                this.fallenItems.stream().map(LostItemEntry::copy).toList(),
                this.pendingFetchItems.stream().map(LostItemEntry::copy).toList(),
                this.trackedChunks.stream().map(TrackedDeathChunk::copy).toList(),
                this.fetchReturnGameTime
        );
    }
}
