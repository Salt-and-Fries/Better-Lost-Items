package org.betterLostItems.better_lost_items.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for container slots needed by merchant payment auto-fill fixes.
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    /**
     * @return live slot list from the target menu
     */
    @Accessor("slots")
    NonNullList<Slot> betterLostItems$getSlots();
}
