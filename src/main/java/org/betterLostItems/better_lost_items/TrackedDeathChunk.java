package org.betterLostItems.better_lost_items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Dimension-aware chunk coordinate for unloaded player-death drops.
 *
 * <p>Fetch journeys use this record to temporarily load chunks that might still
 * contain tagged item entities. The dimension is part of the key because two
 * dimensions can share the same chunk coordinates.</p>
 *
 * @param dimension Minecraft dimension containing the tracked chunk
 * @param chunkX chunk X coordinate
 * @param chunkZ chunk Z coordinate
 */
public record TrackedDeathChunk(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
    public static final Codec<TrackedDeathChunk> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(TrackedDeathChunk::dimension),
            Codec.INT.fieldOf("chunk_x").forGetter(TrackedDeathChunk::chunkX),
            Codec.INT.fieldOf("chunk_z").forGetter(TrackedDeathChunk::chunkZ)
    ).apply(instance, TrackedDeathChunk::new));

    /**
     * Creates a tracked chunk from a vanilla {@link ChunkPos}.
     *
     * @param dimension dimension containing the chunk
     * @param chunkPos vanilla chunk position
     * @return dimension-aware tracked chunk
     */
    public static TrackedDeathChunk of(ResourceKey<Level> dimension, ChunkPos chunkPos) {
        return new TrackedDeathChunk(dimension, chunkPos.x, chunkPos.z);
    }

    /**
     * @return vanilla chunk position for APIs that do not include dimension
     */
    public ChunkPos chunkPos() {
        return new ChunkPos(this.chunkX, this.chunkZ);
    }

    /**
     * @return packed chunk coordinate used by entity-loaded checks
     */
    public long toLong() {
        return ChunkPos.asLong(this.chunkX, this.chunkZ);
    }

    /**
     * @return duplicate record for consistency with other storage entry types
     */
    public TrackedDeathChunk copy() {
        return new TrackedDeathChunk(this.dimension, this.chunkX, this.chunkZ);
    }
}
