package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import org.betterLostItems.better_lost_items.TrackedItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents mobs from stealing player death drops before the recovery system can track them.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    /**
     * Cancels mob pickup for item entities tagged with a death owner.
     */
    @Inject(method = "pickUpItem", at = @At("HEAD"), cancellable = true)
    private void betterLostItems$preventDeathLootPickup(ItemEntity itemEntity, CallbackInfo ci) {
        if (((TrackedItemEntity) itemEntity).betterLostItems$getOwnerId() != null) {
            ci.cancel();
        }
    }
}
