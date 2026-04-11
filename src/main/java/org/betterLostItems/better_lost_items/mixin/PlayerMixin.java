package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.DeathDropTrackingContext;
import org.betterLostItems.better_lost_items.LostItemsTraderJourneyManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks server-player death and respawn events.
 *
 * <p>The death hooks bracket vanilla {@code dropAllDeathLoot} with
 * {@link DeathDropTrackingContext} so newly created item entities can be tagged with the dying
 * player. The respawn hook schedules a nearby trader check so recovery gameplay is accessible
 * after death.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerMixin {
    @Inject(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void betterLostItems$startTrackingDeathDrops(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        Better_lost_items.LOGGER.info("[BLI DEBUG] Starting death-drop tracking for player {} cause={}", player.getUUID(), damageSource.type().msgId());
        DeathDropTrackingContext.push(player.getUUID());
    }

    @Inject(
            method = "die",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void betterLostItems$stopTrackingDeathDrops(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        Better_lost_items.LOGGER.info("[BLI DEBUG] Stopping death-drop tracking for player {}", player.getUUID());
        DeathDropTrackingContext.pop();
    }

    /**
     * After a real death respawn, make sure a recovery trader exists nearby.
     */
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void betterLostItems$ensureRecoveryTraderAfterRespawn(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        if (!oldPlayer.isAlive()) {
            LostItemsTraderJourneyManager.scheduleRespawnTraderCheck((ServerPlayer) (Object) this);
        }
    }
}
