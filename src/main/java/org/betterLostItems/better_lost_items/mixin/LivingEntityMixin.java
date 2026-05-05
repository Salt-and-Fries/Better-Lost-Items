package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.DeathDropTrackingContext;
import org.betterLostItems.better_lost_items.LostItemsDebug;
import org.betterLostItems.better_lost_items.TrackedItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Tags item entities created directly by death-drop stack conversion.
 *
 * <p>This covers the normal path where vanilla returns an {@link ItemEntity} from
 * {@code spawnAtLocation}. {@link ServerLevelMixin} covers additional spawn paths that occur later
 * in the same death-drop context.</p>
 */
@Mixin(Entity.class)
public abstract class LivingEntityMixin {
    /**
     * Applies the current death-owner UUID to the newly returned item entity.
     */
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void betterLostItems$tagDeathDrops(ItemStack stack, float yOffset, CallbackInfoReturnable<ItemEntity> cir) {
        UUID trackedPlayerId = DeathDropTrackingContext.currentPlayerId();
        if (trackedPlayerId == null) {
            return;
        }

        ItemEntity droppedEntity = cir.getReturnValue();
        if (droppedEntity != null) {
            ((TrackedItemEntity) droppedEntity).betterLostItems$setOwnerId(trackedPlayerId);
            Better_lost_items.LOGGER.info("[BLI DEBUG] Tagged death drop entityId={} owner={} stack={}", droppedEntity.getId(), trackedPlayerId, LostItemsDebug.stack(droppedEntity.getItem()));
        } else {
            Better_lost_items.LOGGER.info("[BLI DEBUG] spawnAtLocation returned null for owner={} stack={}", trackedPlayerId, LostItemsDebug.stack(stack));
        }
    }
}
