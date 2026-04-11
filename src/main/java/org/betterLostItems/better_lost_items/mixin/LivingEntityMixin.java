package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
 * Tags item entities created directly by player death-drop stack conversion.
 *
 * <p>This covers the normal path where vanilla returns an {@link ItemEntity} from
 * {@code createItemStackToDrop}. {@link ServerLevelMixin} covers additional spawn paths that occur
 * later in the same death-drop context.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    /**
     * Applies the current death-owner UUID to the newly returned item entity.
     */
    @Inject(method = "createItemStackToDrop", at = @At("RETURN"))
    private void betterLostItems$tagDeathDrops(ItemStack stack, boolean dropAround, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir) {
        if (!((Object) this instanceof Player player)) {
            return;
        }

        UUID trackedPlayerId = DeathDropTrackingContext.currentPlayerId();
        if (trackedPlayerId == null || !trackedPlayerId.equals(player.getUUID())) {
            if (trackedPlayerId == null) {
                Better_lost_items.LOGGER.info("[BLI DEBUG] createItemStackToDrop had no tracked death owner for player {} stack={}", player.getUUID(), LostItemsDebug.stack(stack));
            }
            return;
        }

        ItemEntity droppedEntity = cir.getReturnValue();
        if (droppedEntity != null) {
            ((TrackedItemEntity) droppedEntity).betterLostItems$setOwnerId(trackedPlayerId);
            Better_lost_items.LOGGER.info("[BLI DEBUG] Tagged death drop entityId={} owner={} stack={}", droppedEntity.getId(), trackedPlayerId, LostItemsDebug.stack(droppedEntity.getItem()));
        } else {
            Better_lost_items.LOGGER.info("[BLI DEBUG] createItemStackToDrop returned null for owner={} stack={}", trackedPlayerId, LostItemsDebug.stack(stack));
        }
    }
}
