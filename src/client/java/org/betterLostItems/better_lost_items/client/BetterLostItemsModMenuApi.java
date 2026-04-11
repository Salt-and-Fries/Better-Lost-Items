package org.betterLostItems.better_lost_items.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Optional Mod Menu integration entry point.
 *
 * <p>This class is loaded only by Mod Menu through the {@code modmenu} entrypoint. Keeping the
 * integration isolated here means Better Lost Items can still run when Mod Menu is not installed.</p>
 */
public final class BetterLostItemsModMenuApi implements ModMenuApi {
    /**
     * Provides the config screen opened by Mod Menu's Configure button.
     *
     * @return factory that creates a fresh config editor screen
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BetterLostItemsConfigScreen::new;
    }
}
