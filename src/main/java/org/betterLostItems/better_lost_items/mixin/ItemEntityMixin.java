package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.betterLostItems.better_lost_items.Better_lost_items;
import org.betterLostItems.better_lost_items.DeathDropTrackingContext;
import org.betterLostItems.better_lost_items.LostItemsStorage;
import org.betterLostItems.better_lost_items.LostItemsStorageManager;
import org.betterLostItems.better_lost_items.LostItemsDebug;
import org.betterLostItems.better_lost_items.TrackedDeathChunk;
import org.betterLostItems.better_lost_items.TrackedItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

/**
 * Adds ownership and loss handling to item entities.
 *
 * <p>This is the core of the mod's item tracking. Untagged despawns become public market items.
 * Tagged death drops are routed to a player's visible lost loot, hidden burned loot, hidden fallen
 * loot, or pending fetch tracking depending on how the entity disappears.</p>
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements TrackedItemEntity {
    @Unique
    private UUID betterLostItems$ownerId;
    @Unique
    private long betterLostItems$trackedChunkLong = Long.MIN_VALUE;
    @Unique
    private boolean betterLostItems$hasTrackedChunk;

    /**
     * @return player UUID attached to this item entity, or {@code null}
     */
    @Override
    public UUID betterLostItems$getOwnerId() {
        return this.betterLostItems$ownerId;
    }

    /**
     * Stores the death-owner UUID on this item entity.
     */
    @Override
    public void betterLostItems$setOwnerId(UUID ownerId) {
        this.betterLostItems$ownerId = ownerId;
    }

    /**
     * Persists the custom owner UUID with the vanilla item entity save data.
     */
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void betterLostItems$writeTrackedOwner(CompoundTag tag, CallbackInfo ci) {
        if (this.betterLostItems$ownerId != null) {
            tag.putUUID("BetterLostItemsOwner", this.betterLostItems$ownerId);
        }
    }

    /**
     * Restores the custom owner UUID when a chunk is loaded.
     */
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void betterLostItems$readTrackedOwner(CompoundTag tag, CallbackInfo ci) {
        this.betterLostItems$ownerId = tag.hasUUID("BetterLostItemsOwner") ? tag.getUUID("BetterLostItemsOwner") : null;
    }

    /**
     * Captures item stacks at the exact vanilla despawn discard point.
     */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void betterLostItems$storeDespawningStack(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty() || itemEntity.getAge() < 6000) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(serverLevel.getServer());
        if (this.betterLostItems$ownerId == null) {
            Better_lost_items.LOGGER.info("[BLI DEBUG] Despawn storing UNCLAIMED entityId={} age={} stack={}", itemEntity.getId(), itemEntity.getAge(), LostItemsDebug.stack(stack));
            storage.addUnclaimed(stack);
        } else {
            Better_lost_items.LOGGER.info("[BLI DEBUG] Despawn storing PLAYER entityId={} age={} owner={} stack={}", itemEntity.getId(), itemEntity.getAge(), this.betterLostItems$ownerId, LostItemsDebug.stack(stack));
            storage.addPlayerLostItem(this.betterLostItems$ownerId, stack);
            this.betterLostItems$untrackChunkIfEmpty(serverLevel, itemEntity.chunkPosition(), itemEntity.getId());
        }
    }

    /**
     * Captures tagged death loot that falls below the world before normal despawn.
     */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V", shift = At.Shift.AFTER))
    private void betterLostItems$storeBelowWorldDeathLoot(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (!(itemEntity.level() instanceof ServerLevel serverLevel) || this.betterLostItems$ownerId == null) {
            return;
        }

        if (!itemEntity.isRemoved() || itemEntity.getY() >= serverLevel.getMinBuildHeight() - 64.0D) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }

        Better_lost_items.LOGGER.info(
                "[BLI DEBUG] BelowWorld storing FALLEN entityId={} owner={} y={} stack={}",
                itemEntity.getId(),
                this.betterLostItems$ownerId,
                itemEntity.getY(),
                LostItemsDebug.stack(stack)
        );
        LostItemsStorage storage = LostItemsStorageManager.get(serverLevel.getServer());
        storage.addPlayerFallenItem(this.betterLostItems$ownerId, stack);
        this.betterLostItems$untrackChunkIfEmpty(serverLevel, itemEntity.chunkPosition(), itemEntity.getId());
    }

    /**
     * Captures tagged death loot destroyed by environmental damage.
     */
    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V"))
    private void betterLostItems$storeDestroyedDeathLoot(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (this.betterLostItems$ownerId == null || !(itemEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }

        LostItemsStorage storage = LostItemsStorageManager.get(serverLevel.getServer());
        if (this.betterLostItems$isBurnDamage(damageSource)) {
            Better_lost_items.LOGGER.info("[BLI DEBUG] Destroyed storing BURNED entityId={} owner={} source={} stack={}", itemEntity.getId(), this.betterLostItems$ownerId, damageSource.type().msgId(), LostItemsDebug.stack(stack));
            storage.addPlayerBurnedItem(this.betterLostItems$ownerId, stack);
            this.betterLostItems$untrackChunkIfEmpty(serverLevel, itemEntity.chunkPosition(), itemEntity.getId());
        } else if (damageSource.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            Better_lost_items.LOGGER.info("[BLI DEBUG] Destroyed storing FALLEN entityId={} owner={} source={} stack={}", itemEntity.getId(), this.betterLostItems$ownerId, damageSource.type().msgId(), LostItemsDebug.stack(stack));
            storage.addPlayerFallenItem(this.betterLostItems$ownerId, stack);
            this.betterLostItems$untrackChunkIfEmpty(serverLevel, itemEntity.chunkPosition(), itemEntity.getId());
        } else if (damageSource.is(DamageTypes.CACTUS)) {
            Better_lost_items.LOGGER.info("[BLI DEBUG] Destroyed storing LOST entityId={} owner={} source={} stack={}", itemEntity.getId(), this.betterLostItems$ownerId, damageSource.type().msgId(), LostItemsDebug.stack(stack));
            storage.addPlayerLostItem(this.betterLostItems$ownerId, stack);
            this.betterLostItems$untrackChunkIfEmpty(serverLevel, itemEntity.chunkPosition(), itemEntity.getId());
        }
    }

    /**
     * Pushes the owner while a destroyed container item emits its contents.
     */
    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private void betterLostItems$pushDestroyedContainerOwner(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.betterLostItems$ownerId != null) {
            DeathDropTrackingContext.push(this.betterLostItems$ownerId);
        }
    }

    /**
     * Pops the temporary owner after destroyed-container contents have spawned.
     */
    @Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onDestroyed(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void betterLostItems$popDestroyedContainerOwner(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.betterLostItems$ownerId != null) {
            DeathDropTrackingContext.pop();
        }
    }

    /**
     * Prevents owned death drops from merging with unowned or differently owned items.
     */
    @Inject(method = "tryToMerge", at = @At("HEAD"), cancellable = true)
    private void betterLostItems$stopMixedOwnershipMerges(ItemEntity other, CallbackInfo ci) {
        UUID otherOwner = ((TrackedItemEntity) other).betterLostItems$getOwnerId();
        if (!Objects.equals(this.betterLostItems$ownerId, otherOwner)) {
            ci.cancel();
        }
    }

    /**
     * Records the chunk containing this owned item so a future fetch can load it.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void betterLostItems$trackOwnerChunk(CallbackInfo ci) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (this.betterLostItems$ownerId == null || !(itemEntity.level() instanceof ServerLevel serverLevel) || itemEntity.isRemoved()) {
            return;
        }

        ChunkPos currentChunk = itemEntity.chunkPosition();
        long currentChunkLong = currentChunk.toLong();
        if (this.betterLostItems$hasTrackedChunk && this.betterLostItems$trackedChunkLong == currentChunkLong) {
            return;
        }

        if (this.betterLostItems$hasTrackedChunk) {
            this.betterLostItems$untrackChunkIfEmpty(serverLevel, new ChunkPos(this.betterLostItems$trackedChunkLong), itemEntity.getId());
        }

        LostItemsStorageManager.get(serverLevel.getServer()).addTrackedDeathChunk(this.betterLostItems$ownerId, serverLevel.dimension(), currentChunk.x, currentChunk.z);
        this.betterLostItems$trackedChunkLong = currentChunkLong;
        this.betterLostItems$hasTrackedChunk = true;
    }

    /**
     * Removes a chunk marker when no other owned items remain in that chunk.
     */
    @Unique
    private void betterLostItems$untrackChunkIfEmpty(ServerLevel serverLevel, ChunkPos chunkPos, int ignoredEntityId) {
        if (this.betterLostItems$ownerId == null) {
            return;
        }

        AABB bounds = new AABB(
                chunkPos.getMinBlockX(),
                serverLevel.getMinBuildHeight(),
                chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1,
                serverLevel.getMaxBuildHeight(),
                chunkPos.getMaxBlockZ() + 1
        );

        boolean hasOtherOwnedItems = !serverLevel.getEntities(EntityTypeTest.forClass(ItemEntity.class), bounds,
                item -> item.getId() != ignoredEntityId && this.betterLostItems$ownerId.equals(((TrackedItemEntity) item).betterLostItems$getOwnerId())).isEmpty();
        if (!hasOtherOwnedItems) {
            LostItemsStorageManager.get(serverLevel.getServer()).removeTrackedDeathChunk(
                    this.betterLostItems$ownerId,
                    TrackedDeathChunk.of(serverLevel.dimension(), chunkPos)
            );
        }
    }

    /**
     * @return whether a damage source should route death loot into burned storage
     */
    @Unique
    private boolean betterLostItems$isBurnDamage(DamageSource damageSource) {
        return damageSource.is(DamageTypes.IN_FIRE)
                || damageSource.is(DamageTypes.ON_FIRE)
                || damageSource.is(DamageTypes.CAMPFIRE)
                || damageSource.is(DamageTypes.LAVA);
    }
}
