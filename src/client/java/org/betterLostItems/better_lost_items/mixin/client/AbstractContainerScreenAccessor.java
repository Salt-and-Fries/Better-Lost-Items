package org.betterLostItems.better_lost_items.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for menu screen origin coordinates used to position custom tabs.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    /**
     * @return left edge of the target screen's background texture
     */
    @Accessor("leftPos")
    int betterLostItems$getLeftPos();

    /**
     * @return top edge of the target screen's background texture
     */
    @Accessor("topPos")
    int betterLostItems$getTopPos();
}
