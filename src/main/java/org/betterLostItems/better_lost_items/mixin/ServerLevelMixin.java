package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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
 * Tags item entities spawned while a player death-drop context is active.
 *
 * <p>This is the broad safety net for drops that bypass {@code LivingEntity}'s direct return value,
 * including contents emitted by destroyed container items.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    /**
     * Applies the current owner marker before the entity enters the world.
     */
    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void betterLostItems$tagTrackedDeathDropOnSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        UUID trackedPlayerId = DeathDropTrackingContext.currentPlayerId();
        if (trackedPlayerId == null || !(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        ((TrackedItemEntity) itemEntity).betterLostItems$setOwnerId(trackedPlayerId);
        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] ServerLevel.addFreshEntity tagged death drop entityId={} owner={} stack={}",
                itemEntity.getId(),
                trackedPlayerId,
                LostItemsDebug.stack(itemEntity.getItem())
        );
    }
}
