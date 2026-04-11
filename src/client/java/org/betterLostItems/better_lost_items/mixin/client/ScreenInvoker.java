package org.betterLostItems.better_lost_items.mixin.client;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for adding widgets from a screen mixin.
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
    /**
     * Calls the protected {@code Screen.addRenderableWidget} method.
     */
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T betterLostItems$invokeAddRenderableWidget(T widget);
}
