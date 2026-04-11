package org.betterLostItems.better_lost_items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * A persisted stack inside one of the lost-item pools.
 *
 * <p>The UUID is deliberately separate from the item stack so market trades and
 * recovery clicks can remove exactly the entry that was offered, even when
 * several entries contain visually identical stacks.</p>
 *
 * @param id stable entry ID used by storage, trade offers, and network payloads
 * @param stack copied Minecraft item stack represented by this entry
 */
public record LostItemEntry(UUID id, ItemStack stack) {
    public static final Codec<LostItemEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(LostItemEntry::id),
            ItemStack.CODEC.fieldOf("item").forGetter(LostItemEntry::stack)
    ).apply(instance, LostItemEntry::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LostItemEntry> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            LostItemEntry::id,
            ItemStack.OPTIONAL_STREAM_CODEC,
            LostItemEntry::stack,
            LostItemEntry::new
    );

    /**
     * Creates a deep copy so callers can safely mutate the returned stack.
     *
     * @return entry with the same ID and a copied {@link ItemStack}
     */
    public LostItemEntry copy() {
        return new LostItemEntry(this.id, this.stack.copy());
    }
}
